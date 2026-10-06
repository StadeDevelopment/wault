package net.wault.transport

import net.wault.backgroundFailures
import net.wault.transport.tor.EmbeddedTorRuntime
import net.wault.transport.tor.TorStatus
import net.wault.transport.tor.parseBridgeConfig
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress

class TorTransport(
    private val defaultSocksHost: String = "127.0.0.1",
    private val defaultSocksPort: Int = 9050,
    private val configProvider: () -> String = { "" },
    private val embedded: EmbeddedTorRuntime? = null
) : BaseTransport(TransportType.TOR, "Tor") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    private val selector = SelectorManager(Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile private var inboundOnion: String? = null
    @Volatile private var inboundPort: Int = 0
    @Volatile private var onionPublished: Boolean = true
    @Volatile private var inboundServer: ServerSocket? = null
    @Volatile private var socksHost: String = defaultSocksHost
    @Volatile private var socksPort: Int = defaultSocksPort
    @Volatile private var lastRepublishAt: Long = 0L

    override suspend fun start(handler: suspend (Connection) -> Unit) {
        if (embedded != null) {
            startEmbedded(handler)
            return
        }
        mutex.withLock { startExternal(handler) }
    }

    private suspend fun startEmbedded(handler: suspend (Connection) -> Unit) {
        state.value = TransportInfo(type, "Tor", available = false, running = false, message = "starting Tor")
        scope.launch {
            embedded!!.statusFlow.collectLatest { status ->
                when (status) {
                    is TorStatus.Bootstrapping -> {
                        state.value = TransportInfo(
                            type, "Tor",
                            available = false, running = false,
                            message = "Tor boot ${status.percent}% · ${status.summary}",
                            bootstrapPercent = status.percent,
                            bootstrapPhase = status.summary
                        )
                    }

                    is TorStatus.Failed -> {
                        state.value = TransportInfo(
                            type, "Tor", available = false, running = false,
                            message = "failed to start: ${status.reason}", failed = true
                        )
                    }

                    is TorStatus.Ready -> {
                        if (status.published && !onionPublished) {
                            onionPublished = true
                            mutex.withLock {
                                state.value = TransportInfo(
                                    type, "Tor", available = true, running = true,
                                    message = describe()
                                )
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
        scope.launch { runHealthMonitor(handler) }
        bringUpEmbedded(handler)
    }

    private suspend fun bringUpEmbedded(handler: suspend (Connection) -> Unit) {
        var boundPort = 0
        var preBoundServer: ServerSocket? = null
        var bindError: String? = null
        runCatching {
            val bound = aSocket(selector).tcp().bind(hostname = "127.0.0.1", port = 0)
            preBoundServer = bound
            boundPort = (bound.localAddress as io.ktor.network.sockets.InetSocketAddress).port
        }.onFailure { error ->
            bindError = error.message ?: error::class.simpleName
        }

        val bridgeConfig = parseBridgeConfig(configProvider())
        val ready = runCatching { embedded!!.ensureReady(boundPort, bridgeConfig) }.getOrElse { error ->
            runCatching { preBoundServer?.close() }
            state.value = TransportInfo(
                type, "Tor", available = false, running = false,
                message = "failed to start: ${error.message ?: error::class.simpleName}", failed = true
            )
            return
        }
        mutex.withLock {
            socksHost = ready.socksHost
            socksPort = ready.socksPort
            inboundOnion = ready.onionHostname
            inboundPort = ready.onionVirtualPort
            onionPublished = ready.onionPublished ||
                (embedded?.statusFlow?.value as? TorStatus.Ready)?.published == true
            val listenError = bindError
            if (preBoundServer != null && bindError == null) {
                runCatching { inboundServer?.close() }
                inboundServer = preBoundServer
                scope.launch { runAccept(preBoundServer!!, handler) }
            } else {
                inboundOnion = null
                inboundPort = 0
            }
            state.value = TransportInfo(
                type, "Tor", available = true, running = true,
                message = describe(listenError)
            )
        }
    }

    private fun describe(listenError: String? = null): String = buildString {
        append("SOCKS5 ($socksHost:$socksPort)")
        val onion = inboundOnion
        when {
            onion != null && onionPublished -> append(" · onion :$inboundPort published")
            onion != null -> append(" · onion :$inboundPort publishing")
            listenError != null -> append(" · onion not listening ($listenError)")
        }
    }

    private suspend fun runHealthMonitor(handler: suspend (Connection) -> Unit) {
        var lastRestartAt = 0L
        while (scope.isActive) {
            delay(10_000)
            val runtime = embedded ?: return
            if (!state.value.running) continue
            if (runtime.isAlive()) continue
            val now = System.currentTimeMillis()
            if (now - lastRestartAt < 30_000) continue
            lastRestartAt = now
            state.value = state.value.copy(
                running = false, available = false,
                message = "Tor process exited, restarting"
            )
            runCatching { inboundServer?.close() }
            inboundServer = null
            runCatching { runtime.invalidate() }
            runCatching { bringUpEmbedded(handler) }
        }
    }

    private suspend fun startExternal(handler: suspend (Connection) -> Unit) {
        val config = parseConfig(configProvider())
        val configuredHost = config["socksHost"]?.takeIf { it.isNotBlank() } ?: defaultSocksHost
        val configuredPort = config["socksPort"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: defaultSocksPort
        val probeOrder = buildList {
            add(configuredHost to configuredPort)
            if (configuredHost == "127.0.0.1") {
                if (configuredPort != 9050) add("127.0.0.1" to 9050)
                if (configuredPort != 9150) add("127.0.0.1" to 9150)
            }
        }
        var socksOk = false
        for ((host, port) in probeOrder) {
            if (probeSocks(host, port)) {
                socksHost = host
                socksPort = port
                socksOk = true
                break
            }
        }
        if (!socksOk) {
            socksHost = configuredHost
            socksPort = configuredPort
        }
        inboundOnion = config["onion"]?.takeIf { it.isNotBlank() }
        val publicPort = config["port"]?.toIntOrNull() ?: 0
        val listenPort = config["listenPort"]?.toIntOrNull()?.takeIf { it > 0 } ?: publicPort
        inboundPort = publicPort
        val listenHost = config["listenHost"] ?: "127.0.0.1"

        var listening = false
        var listenError: String? = null
        if (listenPort > 0) {
            runCatching {
                val bound = aSocket(selector).tcp().bind(hostname = listenHost, port = listenPort)
                inboundServer = bound
                scope.launch { runAccept(bound, handler) }
                listening = true
            }.onFailure { error ->
                inboundOnion = null
                inboundPort = 0
                listenError = if (error is java.net.BindException) {
                    "$listenHost:$listenPort already in use"
                } else {
                    error.message ?: error::class.simpleName ?: "bind error"
                }
            }
        }

        val anyUp = socksOk || listening
        state.value = TransportInfo(
            type, "Tor",
            available = anyUp,
            running = anyUp,
            message = if (socksOk) describe(listenError) else "SOCKS5 unavailable (probed 9050 and 9150)"
        )
    }

    override suspend fun stop() = mutex.withLock {
        runCatching { inboundServer?.close() }
        inboundServer = null
        state.value = state.value.copy(running = false)
    }

    override suspend fun reload() {
        embedded?.invalidate()
    }

    override suspend fun refreshReachability(): Boolean {
        val runtime = embedded ?: return false
        val now = System.currentTimeMillis()
        if (now - lastRepublishAt < 300_000) return false
        lastRepublishAt = now
        val ok = runCatching { runtime.republishOnion() }.getOrDefault(false)
        (runtime.statusFlow.value as? TorStatus.Ready)?.onion?.let { inboundOnion = it }
        onionPublished = ok
        state.value = state.value.copy(message = describe())
        return ok
    }

    override suspend fun connect(address: String): Connection? {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            try {
                val result = connectOnce(address)
                if (result != null) return result
                lastError = IllegalStateException("Tor connect timed out")
            } catch (error: Throwable) {
                lastError = error
            }
            if (attempt < 2) delay(1_500L * (attempt + 1))
        }
        throw lastError ?: IllegalStateException("Tor connect failed")
    }

    private suspend fun connectOnce(address: String): Connection? {
        val target = address.removePrefix("tor://")
        val parts = target.split(":", limit = 2)
        val host = parts[0]
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 80
        val proxyHost = socksHost
        val proxyPort = socksPort
        if (proxyPort <= 0) throw IllegalStateException("Tor SOCKS not ready (port=$proxyPort)")
        return withTimeoutOrNull(60_000) {
            var socket: io.ktor.network.sockets.Socket? = null
            try {
                socket = try {
                    aSocket(selector).tcp().connect(hostname = proxyHost, port = proxyPort)
                } catch (error: Throwable) {
                    throw IllegalStateException(
                        "Tor SOCKS unreachable at $proxyHost:$proxyPort (${error.message ?: error::class.simpleName})",
                        error
                    )
                }
                val reader = socket.openReadChannel()
                val writer = socket.openWriteChannel(autoFlush = true)

                val greeting = byteArrayOf(0x05, 0x01, 0x00)
                writer.writeFully(greeting, 0, greeting.size)
                val greetReply = ByteArray(2)
                reader.readFully(greetReply)
                if (greetReply[0] != 0x05.toByte() || greetReply[1] != 0x00.toByte()) {
                    throw IllegalStateException("SOCKS5 greeting rejected")
                }

                val hostBytes = host.toByteArray(Charsets.US_ASCII)
                val request = ByteArray(7 + hostBytes.size)
                request[0] = 0x05; request[1] = 0x01; request[2] = 0x00; request[3] = 0x03
                request[4] = hostBytes.size.toByte()
                hostBytes.copyInto(request, 5)
                request[5 + hostBytes.size] = ((port ushr 8) and 0xff).toByte()
                request[6 + hostBytes.size] = (port and 0xff).toByte()
                writer.writeFully(request, 0, request.size)

                val head = ByteArray(4)
                reader.readFully(head)
                val reply = head[1].toInt() and 0xff
                if (reply != 0x00) {
                    throw IllegalStateException("SOCKS5 error: ${socksReplyMessage(reply)}")
                }
                val rest = when (head[3].toInt() and 0xff) {
                    0x01 -> 4 + 2
                    0x03 -> {
                        val lengthByte = ByteArray(1)
                        reader.readFully(lengthByte)
                        (lengthByte[0].toInt() and 0xff) + 2
                    }
                    0x04 -> 16 + 2
                    else -> 0
                }
                if (rest > 0) reader.readFully(ByteArray(rest))
                val connection = TcpConnection(socket, reader, writer, "tor://$host:$port")
                socket = null
                connection
            } finally {
                socket?.let { runCatching { it.close() } }
            }
        }
    }

    private fun socksReplyMessage(code: Int): String = when (code) {
        0x01 -> "general SOCKS server failure"
        0x02 -> "connection not allowed by ruleset"
        0x03 -> "network unreachable"
        0x04 -> "host unreachable (onion descriptor missing or not propagated)"
        0x05 -> "connection refused (the peer is not listening on .onion)"
        0x06 -> "TTL expired"
        0x07 -> "command not supported"
        0x08 -> "address type not supported"
        else -> "code=$code"
    }

    override fun selfAddress(): String? {
        val onion = inboundOnion ?: return null
        if (embedded != null && !onionPublished) return null
        return if (inboundPort > 0) "tor://$onion:$inboundPort" else null
    }

    fun socksProxyAddress(): Pair<String, Int>? {
        val host = socksHost
        val port = socksPort
        return if (port > 0 && state.value.running) host to port else null
    }

    private suspend fun runAccept(server: ServerSocket, handler: suspend (Connection) -> Unit) {
        while (scope.isActive) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch {
                handler(TcpConnection(socket, "tor://inbound"))
            }
        }
    }

    private fun parseConfig(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    }

    private suspend fun probeSocks(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            java.net.Socket().use { it.connect(InetSocketAddress(host, port), 1500) }
            true
        }.getOrDefault(false)
    }
}
