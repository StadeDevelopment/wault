package net.wault.sync

import net.wault.crypto.zero
import net.wault.device.DeviceManager
import net.wault.security.Vault
import net.wault.security.VaultRotation
import net.wault.transport.Connection
import net.wault.transport.ConnectionRegistry
import net.wault.transport.DiscoverableTransport
import net.wault.transport.TransportSettings
import net.wault.transport.TransportType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import net.wault.currentTimeMillis

private const val HELLO_PREFIX = "WAULT/1 "
private const val HELLO_SEPARATOR = "|"
private const val HANDSHAKE_TIMEOUT_MS = 20_000L
private const val EXCHANGE_TIMEOUT_MS = 60_000L
private const val SYNC_INTERVAL_MS = 120_000L

class SyncService(
    private val transports: ConnectionRegistry,
    private val settings: TransportSettings,
    private val devices: DeviceManager,
    private val sessions: DeviceSessions,
    private val engine: SyncEngine,
    private val vault: Vault,
    private val rotation: VaultRotation,
    private val scope: CoroutineScope,
    private val localDeviceId: () -> String,
    private val isEnabled: () -> Boolean,
    private val clock: () -> Long = { currentTimeMillis() }
) {
    private val statuses = MutableStateFlow<Map<String, PeerSyncStatus>>(emptyMap())
    val peerStatus: StateFlow<Map<String, PeerSyncStatus>> = statuses.asStateFlow()

    private fun record(peerId: String, outcome: SyncOutcome, detail: String = "") {
        statuses.value = statuses.value.toMutableMap().apply {
            val previous = this[peerId] ?: PeerSyncStatus()
            val now = clock()
            this[peerId] = previous.copy(
                outcome = outcome,
                lastAttemptMillis = if (outcome == SyncOutcome.Syncing) now else previous.lastAttemptMillis,
                lastSuccessMillis = if (outcome == SyncOutcome.Succeeded) now else previous.lastSuccessMillis,
                detail = detail
            )
        }
    }

    internal fun statusOf(peerId: String): PeerSyncStatus =
        statuses.value[peerId] ?: PeerSyncStatus()

    private val exchangeLocks = mutableMapOf<String, Mutex>()
    private val locksMutex = Mutex()
    private var running = false

    @Volatile
    var pairingAcceptor: (suspend (Connection) -> Unit)? = null

    suspend fun start() {
        if (running) return
        running = true
        for (plugin in transports.all()) {
            if (!settings.get(plugin.type).enabled) continue
            scope.launch {
                runCatching { plugin.start { connection -> serve(connection) } }
            }
        }
        scope.launch { periodicSync() }
    }

    suspend fun stop() {
        running = false
        transports.all().forEach { runCatching { it.stop() } }
    }

    suspend fun syncNow() {
        if (!isEnabled()) return
        for (peer in devices.peers()) {
            scope.launch { runCatching { dial(peer.id) } }
        }
    }

    private suspend fun periodicSync() {
        while (scope.isActive && running) {
            delay(SYNC_INTERVAL_MS)
            if (!isEnabled()) continue
            runCatching { syncNow() }
        }
    }

    private suspend fun dial(peerDeviceId: String) {
        val peer = devices.peer(peerDeviceId) ?: return
        val addresses = candidateAddresses(peer.onionAddress, peer.lanAddress)
        if (addresses.isEmpty()) {
            record(peerDeviceId, SyncOutcome.NoKnownAddress)
            return
        }

        record(peerDeviceId, SyncOutcome.Syncing)
        var reached = false
        var lastDetail = ""

        for (address in addresses) {
            val plugin = transports.all().firstOrNull { address.startsWith(it.type.scheme()) } ?: continue
            if (!settings.get(plugin.type).enabled) continue

            val attempt = runCatching { plugin.connect(address) }
            val connection = attempt.getOrNull()
            if (connection == null) {
                lastDetail = attempt.exceptionOrNull()?.describe() ?: address
                continue
            }
            reached = true

            val result = runCatching { exchange(connection, expectedPeerId = peerDeviceId, initiator = true) }
            runCatching { connection.close() }

            if (result.getOrDefault(false)) {
                record(peerDeviceId, SyncOutcome.Succeeded)
                return
            }
            lastDetail = result.exceptionOrNull()?.describe() ?: lastDetail
        }

        if (reached) {
            record(peerDeviceId, SyncOutcome.Rejected, lastDetail)
        } else {
            record(peerDeviceId, SyncOutcome.Unreachable, lastDetail)
        }
    }

    private suspend fun serve(connection: Connection) {
        val first = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { connection.receive() }
        if (first == null) {
            runCatching { connection.close() }
            return
        }
        val text = first.decodeToString()

        if (text.startsWith(PAIRING_HELLO)) {
            val acceptor = pairingAcceptor
            if (acceptor == null) {
                runCatching { connection.close() }
                return
            }
            runCatching { acceptor(connection) }
            runCatching { connection.close() }
            return
        }

        runCatching { respond(connection, text) }
        runCatching { connection.close() }
    }

    suspend fun dialForPairing(address: String): Connection? {
        val plugin = transports.all().firstOrNull { address.startsWith(it.type.scheme()) } ?: return null
        if (!settings.get(plugin.type).enabled) return null
        val connection = runCatching { plugin.connect(address) }.getOrNull() ?: return null
        val sent = runCatching { connection.send(PAIRING_HELLO.encodeToByteArray()) }.isSuccess
        if (!sent) {
            runCatching { connection.close() }
            return null
        }
        return connection
    }

    private fun helloPayload(): ByteArray {
        val advertised = transports.all().flatMap { plugin ->
            runCatching { plugin.selfAddresses() }.getOrDefault(emptyList())
        }.distinct()
        val suffix = if (advertised.isEmpty()) "" else HELLO_SEPARATOR + advertised.joinToString(",")
        return (HELLO_PREFIX + localDeviceId() + suffix).encodeToByteArray()
    }

    internal fun advertisedAddresses(helloBody: String): List<String> =
        helloBody.substringAfter(HELLO_SEPARATOR, "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun rememberAddresses(peerId: String, advertised: List<String>) {
        if (advertised.isEmpty()) return

        val onion = advertised.firstOrNull { it.startsWith(TransportType.TOR.scheme()) }
        val lan = advertised.firstOrNull { it.startsWith(TransportType.LAN.scheme()) }
        if (onion == null && lan == null) return

        val existing = devices.peer(peerId) ?: return
        if (onion == existing.onionAddress && lan == existing.lanAddress) return

        runCatching {
            devices.updateAddresses(
                deviceId = peerId,
                onionAddress = onion ?: existing.onionAddress,
                lanAddress = lan ?: existing.lanAddress
            )
        }
    }

    private suspend fun respond(connection: Connection, helloText: String): Boolean {
        if (!isEnabled()) return false
        if (!helloText.startsWith(HELLO_PREFIX)) return false
        val body = helloText.removePrefix(HELLO_PREFIX).trim()
        val claimedId = body.substringBefore(HELLO_SEPARATOR).trim()
        if (claimedId.isEmpty() || devices.peer(claimedId) == null) return false
        val advertised = advertisedAddresses(body)

        runCatching { connection.send(helloPayload()) }.getOrElse { return false }

        val lock = lockFor(claimedId)
        if (!lock.tryLock()) return false
        val completed = try {
            withTimeoutOrNull(EXCHANGE_TIMEOUT_MS) { runResponder(connection, claimedId) } ?: false
        } finally {
            lock.unlock()
        }
        if (completed) {
            rememberAddresses(claimedId, advertised)
            record(claimedId, SyncOutcome.Succeeded)
        }
        return completed
    }

    internal suspend fun exchange(
        connection: Connection,
        expectedPeerId: String?,
        initiator: Boolean
    ): Boolean {
        if (!isEnabled()) return false

        val greeting = handshake(connection, expectedPeerId) ?: return false
        val peerId = greeting.peerId
        val lock = lockFor(peerId)
        if (!lock.tryLock()) return false
        val completed = try {
            withTimeoutOrNull(EXCHANGE_TIMEOUT_MS) {
                if (initiator) runInitiator(connection, peerId) else runResponder(connection, peerId)
            } ?: false
        } finally {
            lock.unlock()
        }
        if (completed) {
            rememberAddresses(peerId, greeting.addresses)
            record(peerId, SyncOutcome.Succeeded)
        }
        return completed
    }

    private data class Greeting(val peerId: String, val addresses: List<String>)

    private suspend fun handshake(connection: Connection, expectedPeerId: String?): Greeting? {
        runCatching { connection.send(helloPayload()) }.getOrElse { return null }

        val reply = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { connection.receive() } ?: return null
        val text = reply.decodeToString()
        if (!text.startsWith(HELLO_PREFIX)) return null
        val body = text.removePrefix(HELLO_PREFIX).trim()
        val claimedId = body.substringBefore(HELLO_SEPARATOR).trim()
        if (claimedId.isEmpty()) return null
        if (expectedPeerId != null && claimedId != expectedPeerId) return null
        if (devices.peer(claimedId) == null) return null
        return Greeting(claimedId, advertisedAddresses(body))
    }

    private suspend fun runInitiator(connection: Connection, peerId: String): Boolean {
        val myGeneration = vault.keyGeneration()
        val peerGeneration = devices.peer(peerId)?.keyGeneration ?: 0

        if (peerGeneration < myGeneration) {
            if (!sendRekey(connection, peerId, myGeneration)) return false
            val ack = receiveSealed(connection, peerId)
            if (ack !is SyncMessage.RekeyAck || ack.generation != myGeneration) return false
            devices.markKeyGeneration(peerId, myGeneration)
        }

        if (!sendSealed(connection, peerId, engine.buildPush(peerId, myGeneration))) return false

        when (val response = receiveSealed(connection, peerId)) {
            is SyncMessage.Push -> {
                if (response.generation != myGeneration) {
                    devices.markKeyGeneration(peerId, response.generation)
                    return false
                }
                absorb(response, peerId)
                sendSealed(connection, peerId, SyncMessage.Ack(response.envelopeId, response.highWaterMark))
                engine.recordAck(peerId, engine.localHighWaterMark())
                return true
            }

            is SyncMessage.RekeyRequest -> {
                devices.markKeyGeneration(peerId, response.currentGeneration)
                return false
            }

            else -> return false
        }
    }

    private suspend fun runResponder(connection: Connection, peerId: String): Boolean {
        var myGeneration = vault.keyGeneration()

        var incoming = receiveSealed(connection, peerId) ?: return false

        if (incoming is SyncMessage.Rekey) {
            if (incoming.generation <= myGeneration) return false
            val adopted = rotation.adopt(incoming.dataKey.copyOf(), incoming.generation)
            incoming.dataKey.zero()
            if (adopted == null || !adopted.succeeded) return false
            myGeneration = vault.keyGeneration()
            if (!sendSealed(connection, peerId, SyncMessage.RekeyAck(myGeneration))) return false
            incoming = receiveSealed(connection, peerId) ?: return false
        }

        val theirPush = incoming as? SyncMessage.Push ?: return false
        if (theirPush.generation != myGeneration) {
            sendSealed(connection, peerId, SyncMessage.RekeyRequest(myGeneration))
            return false
        }

        absorb(theirPush, peerId)

        if (!sendSealed(connection, peerId, engine.buildPush(peerId, myGeneration))) return false

        val ack = receiveSealed(connection, peerId) as? SyncMessage.Ack ?: return false
        engine.recordAck(peerId, ack.acknowledgedUpTo)
        devices.markKeyGeneration(peerId, myGeneration)
        return true
    }

    private fun absorb(push: SyncMessage.Push, peerId: String) {
        if (!engine.isDuplicate(push.envelopeId)) {
            engine.applyIncoming(push.deltas)
            engine.markProcessed(push.envelopeId, peerId)
        }
        engine.recordPulled(peerId, push.highWaterMark)
    }

    private suspend fun sendRekey(connection: Connection, peerId: String, generation: Int): Boolean {
        val message = vault.withDataKey { dataKey ->
            SyncMessage.Rekey(
                envelopeId = engine.newEnvelopeId(),
                generation = generation,
                dataKey = dataKey.copyOf()
            )
        }
        val sent = sendSealed(connection, peerId, message)
        message.dataKey.zero()
        return sent
    }

    private suspend fun sendSealed(connection: Connection, peerId: String, message: SyncMessage): Boolean {
        val sealed = sessions.seal(peerId, engine.encode(message)) ?: return false
        return runCatching { connection.send(sealed) }.isSuccess
    }

    private suspend fun receiveSealed(connection: Connection, peerId: String): SyncMessage? {
        val frame = connection.receive() ?: return null
        val plaintext = sessions.open(peerId, frame) ?: return null
        return runCatching { engine.decode(plaintext) }.getOrNull()
    }

    private suspend fun lockFor(peerId: String): Mutex =
        locksMutex.withLock { exchangeLocks.getOrPut(peerId) { Mutex() } }

    private fun candidateAddresses(onion: String?, lan: String?): List<String> {
        val discovered = transports.all()
            .filterIsInstance<DiscoverableTransport>()
            .flatMap { it.discoveredPeers() }
        return (listOfNotNull(lan) + discovered + listOfNotNull(onion)).distinct()
    }
}

private fun TransportType.scheme(): String = when (this) {
    TransportType.TOR -> "tor://"
    TransportType.LAN -> "lan://"
}

private fun Throwable.describe(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.simpleName.orEmpty()
