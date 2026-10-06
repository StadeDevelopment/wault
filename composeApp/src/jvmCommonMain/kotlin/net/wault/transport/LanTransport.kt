package net.wault.transport

import net.wault.backgroundFailures
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface

const val LAN_DEFAULT_PORT = 7901
const val LAN_DEFAULT_DISCOVERY_PORT = 7902

private const val DISCOVERY_MAGIC = "WAULT"
private const val DISCOVERY_GROUP = "239.255.77.33"
private const val DISCOVERY_TTL_MS = 60_000L
private const val DISCOVERY_INTERVAL_MS = 15_000L

class LanTransport(
    private val nodeId: String,
    private val tcpPort: Int = LAN_DEFAULT_PORT,
    private val discoveryPort: Int = LAN_DEFAULT_DISCOVERY_PORT
) : BaseTransport(TransportType.LAN, "Local network"), DiscoverableTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    private val selector = SelectorManager(Dispatchers.IO)
    private var server: ServerSocket? = null

    @Volatile
    private var actualPort: Int = tcpPort
    private val discovery = DiscoveryService(nodeId, discoveryPort, tcpPort)
    private val mutex = Mutex()

    override suspend fun start(handler: suspend (Connection) -> Unit) = mutex.withLock {
        if (state.value.running) return@withLock
        val lanIps = allLocalIpv4()
        var lastError: Throwable? = null
        for (candidate in tcpPort..(tcpPort + 9)) {
            try {
                val bound = aSocket(selector).tcp()
                    .bind(hostname = "0.0.0.0", port = candidate) { reuseAddress = true }
                server = bound
                actualPort = candidate
                discovery.announcePort(candidate)
                val message = buildString {
                    if (lanIps.isEmpty()) append("0.0.0.0:$candidate")
                    else append(lanIps.joinToString(", ") { "$it:$candidate" })
                    if (candidate != tcpPort) append(" (port $tcpPort was taken)")
                }
                state.value = TransportInfo(
                    type, "Local network",
                    available = true, running = true,
                    message = message
                )
                scope.launch { runAccept(bound, handler) }
                scope.launch { discovery.start() }
                return@withLock
            } catch (error: Throwable) {
                lastError = error
                continue
            }
        }
        state.value = TransportInfo(
            type, "Local network", available = false, running = false,
            message = "ports $tcpPort..${tcpPort + 9} are all in use — ${lastError?.message ?: ""}"
        )
    }

    override suspend fun stop() = mutex.withLock {
        runCatching { server?.close() }
        server = null
        discovery.stop()
        state.value = state.value.copy(running = false)
    }

    override suspend fun connect(address: String): Connection? {
        val (host, port) = address.removePrefix("lan://").split(":", limit = 2).let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: tcpPort)
        }
        return withTimeoutOrNull(5_000) {
            runCatching {
                val socket = aSocket(selector).tcp().connect(hostname = host, port = port) {
                    noDelay = true
                    keepAlive = true
                }
                EncryptedLinkConnection(TcpConnection(socket, "lan://$host:$port")) as Connection
            }.getOrNull()
        }
    }

    override fun selfAddress(): String? {
        val ip = allLocalIpv4().firstOrNull() ?: return null
        return "lan://$ip:$actualPort"
    }

    override fun selfAddresses(): List<String> =
        allLocalIpv4().map { "lan://$it:$actualPort" }

    override fun discoveredPeers(): List<String> = discovery.snapshot()

    private suspend fun runAccept(server: ServerSocket, handler: suspend (Connection) -> Unit) {
        while (scope.isActive) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            scope.launch {
                handler(EncryptedLinkConnection(TcpConnection(socket, "lan://${socket.remoteAddress}")))
            }
        }
    }

    private fun allLocalIpv4(): List<String> = runCatching {
        val out = mutableListOf<String>()
        for (ni in NetworkInterface.getNetworkInterfaces()) {
            if (!ni.isUp || ni.isLoopback || ni.isVirtual) continue
            for (addr in ni.inetAddresses) {
                val host = addr.hostAddress ?: continue
                if (addr.isLoopbackAddress) continue
                if (host.contains(":")) continue
                if (!addr.isSiteLocalAddress &&
                    !host.startsWith("10.") &&
                    !host.startsWith("172.") &&
                    !host.startsWith("192.168.")
                ) continue
                out += host
            }
        }
        out
    }.getOrDefault(emptyList())
}

internal class TcpConnection : Connection {
    private val socket: Socket
    private val reader: ByteReadChannel
    private val writer: ByteWriteChannel
    private val writeMutex = Mutex()
    override val remoteAddress: String

    constructor(socket: Socket, remoteAddress: String) {
        this.socket = socket
        this.reader = socket.openReadChannel()
        this.writer = socket.openWriteChannel(autoFlush = true)
        this.remoteAddress = remoteAddress
    }

    constructor(socket: Socket, reader: ByteReadChannel, writer: ByteWriteChannel, remoteAddress: String) {
        this.socket = socket
        this.reader = reader
        this.writer = writer
        this.remoteAddress = remoteAddress
    }

    override suspend fun send(frame: ByteArray): Unit = writeMutex.withLock {
        val length = frame.size
        val header = ByteArray(4)
        header[0] = ((length ushr 24) and 0xff).toByte()
        header[1] = ((length ushr 16) and 0xff).toByte()
        header[2] = ((length ushr 8) and 0xff).toByte()
        header[3] = (length and 0xff).toByte()
        writer.writeFully(header, 0, 4)
        writer.writeFully(frame, 0, frame.size)
        writer.flush()
    }

    override suspend fun receive(): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val header = ByteArray(4)
            reader.readFully(header)
            val length = ((header[0].toInt() and 0xff) shl 24) or
                ((header[1].toInt() and 0xff) shl 16) or
                ((header[2].toInt() and 0xff) shl 8) or
                (header[3].toInt() and 0xff)
            if (length <= 0 || length > MAX_FRAME_BYTES) return@runCatching null
            val payload = ByteArray(length)
            reader.readFully(payload)
            payload
        }.getOrNull()
    }

    override suspend fun close() {
        runCatching { socket.close() }
    }

    private companion object {
        const val MAX_FRAME_BYTES = 8 * 1024 * 1024
    }
}

private class DiscoveryService(
    private val nodeId: String,
    private val port: Int,
    private var tcpPort: Int
) {
    private val peers = mutableMapOf<String, Long>()
    private var rxJob: Job? = null
    private var txJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + backgroundFailures)
    private var receiver: MulticastSocket? = null
    private var sender: DatagramSocket? = null
    private var multicastLock: (() -> Unit)? = null

    fun announcePort(value: Int) {
        tcpPort = value
    }

    fun start() {
        if (rxJob != null) return
        multicastLock = acquireMulticastLock()
        runCatching { openReceiver() }.getOrNull()?.let { sock ->
            receiver = sock
            rxJob = scope.launch { receiveLoop(sock) }
        }
        runCatching { DatagramSocket() }.getOrNull()?.let { sock ->
            sender = sock.also { it.broadcast = true }
            txJob = scope.launch { broadcastLoop(sock) }
        }
    }

    private fun openReceiver(): MulticastSocket {
        val sock = MulticastSocket(null)
        sock.reuseAddress = true
        sock.broadcast = true
        sock.bind(InetSocketAddress(port))
        val group = InetSocketAddress(InetAddress.getByName(DISCOVERY_GROUP), port)
        for (ni in joinableInterfaces()) {
            runCatching { sock.joinGroup(group, ni) }
        }
        return sock
    }

    private fun joinableInterfaces(): List<NetworkInterface> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { ni ->
            runCatching { ni.isUp && ni.supportsMulticast() }.getOrDefault(false)
        }
    }.getOrDefault(emptyList())

    fun stop() {
        rxJob?.cancel(); txJob?.cancel()
        rxJob = null; txJob = null
        runCatching { receiver?.close() }
        runCatching { sender?.close() }
        receiver = null
        sender = null
        runCatching { multicastLock?.invoke() }
        multicastLock = null
    }

    fun snapshot(): List<String> = synchronized(peers) {
        val now = System.currentTimeMillis()
        peers.entries.removeAll { now - it.value > DISCOVERY_TTL_MS }
        peers.keys.toList()
    }

    private suspend fun receiveLoop(sock: DatagramSocket) {
        val buffer = ByteArray(512)
        while (scope.isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            val received = runCatching { sock.receive(packet) }.isSuccess
            if (!received) {
                if (sock.isClosed || !scope.isActive) return
                delay(1_000)
                continue
            }
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val parts = text.split('|')
            if (parts.size < 3 || parts[0] != DISCOVERY_MAGIC) continue
            val peerId = parts[1]
            val peerPort = parts[2].toIntOrNull() ?: continue
            if (peerId == nodeId) continue
            val address = "lan://${packet.address.hostAddress}:$peerPort"
            synchronized(peers) { peers[address] = System.currentTimeMillis() }
        }
    }

    private suspend fun broadcastLoop(sock: DatagramSocket) {
        val group = runCatching { InetAddress.getByName(DISCOVERY_GROUP) }.getOrNull()
        while (scope.isActive) {
            val message = "$DISCOVERY_MAGIC|$nodeId|$tcpPort".toByteArray()
            val targets = broadcastAddresses() + listOfNotNull(group)
            for (addr in targets) {
                runCatching { sock.send(DatagramPacket(message, message.size, addr, port)) }
            }
            delay(DISCOVERY_INTERVAL_MS)
        }
    }

    internal fun broadcastAddresses(): List<InetAddress> {
        val out = mutableListOf<InetAddress>()
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp) continue
                for (ia in ni.interfaceAddresses) {
                    ia.broadcast?.let { out += it }
                }
            }
        }
        return out.distinct()
    }
}
