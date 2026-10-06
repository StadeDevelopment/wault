package net.wault.sync

import net.wault.crypto.CryptoApi
import net.wault.crypto.DoubleRatchet
import net.wault.crypto.KeyPair
import net.wault.crypto.PqCrypto
import net.wault.crypto.RatchetSerializer
import net.wault.crypto.RatchetSnapshot
import net.wault.db.WaultDb
import net.wault.device.DeviceManager
import net.wault.device.LocalDevice
import net.wault.device.PeerDevice
import net.wault.security.SecretBox
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class DeviceSessions(
    private val db: WaultDb,
    private val crypto: CryptoApi,
    pq: PqCrypto,
    private val devices: DeviceManager,
    private val secretBox: SecretBox,
    private val clock: () -> Long
) {
    private val ratchet = DoubleRatchet(crypto, pq)
    private val states = mutableMapOf<String, DoubleRatchet.State>()
    private val locks = mutableMapOf<String, Mutex>()
    private val locksMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun seal(peerDeviceId: String, plaintext: ByteArray): ByteArray? =
        lockFor(peerDeviceId).withLock {
            val peer = devices.peer(peerDeviceId) ?: return@withLock null
            val state = loadOrInit(devices.localDevice(), peer) ?: return@withLock null
            val out = ratchet.encrypt(state, plaintext, associatedData(devices.localDevice(), peer))
            persist(peerDeviceId, state)
            out
        }

    suspend fun open(peerDeviceId: String, frame: ByteArray): ByteArray? =
        lockFor(peerDeviceId).withLock {
            val peer = devices.peer(peerDeviceId) ?: return@withLock null
            val state = loadOrInit(devices.localDevice(), peer) ?: return@withLock null
            val out = ratchet.decrypt(state, frame, associatedData(devices.localDevice(), peer))
            if (out != null) persist(peerDeviceId, state)
            out
        }

    suspend fun forget(peerDeviceId: String) {
        locksMutex.withLock {
            states.remove(peerDeviceId)
            locks.remove(peerDeviceId)
        }
        db.waultDbQueries.deleteRatchetSession(peerDeviceId)
    }

    suspend fun hasSession(peerDeviceId: String): Boolean =
        lockFor(peerDeviceId).withLock {
            states.containsKey(peerDeviceId) ||
                db.waultDbQueries.selectRatchetSession(peerDeviceId).executeAsOneOrNull() != null
        }

    private suspend fun lockFor(deviceId: String): Mutex =
        locksMutex.withLock { locks.getOrPut(deviceId) { Mutex() } }

    private fun loadOrInit(local: LocalDevice, peer: PeerDevice): DoubleRatchet.State? {
        states[peer.id]?.let { return it }

        val saved = db.waultDbQueries.selectRatchetSession(peer.id).executeAsOneOrNull()
        val restored = saved?.state
            ?.let { secretBox.open(SecretBox.RATCHET_STATE, peer.id, it) }
            ?.let { bytes ->
                runCatching {
                    RatchetSerializer.fromSnapshot(
                        json.decodeFromString(RatchetSnapshot.serializer(), bytes.decodeToString())
                    )
                }.getOrNull()
            }

        val state = if (restored != null && restored.sendChainKey != null && restored.recvChainKey != null) {
            restored
        } else {
            ratchet.initSymmetric(
                rootSeed = peer.rootKey,
                ownDh = local.agreement,
                peerDhPub = peer.agreementKey,
                isAlice = peer.isInitiator
            )
        }

        states[peer.id] = state
        return state
    }

    private fun persist(deviceId: String, state: DoubleRatchet.State) {
        val snapshot = RatchetSerializer.toSnapshot(state)
        val bytes = json.encodeToString(RatchetSnapshot.serializer(), snapshot).encodeToByteArray()
        db.waultDbQueries.upsertRatchetSession(
            deviceId,
            secretBox.seal(SecretBox.RATCHET_STATE, deviceId, bytes),
            clock()
        )
    }

    private fun associatedData(local: LocalDevice, peer: PeerDevice): ByteArray {
        val a = local.signing.publicKey
        val b = peer.signingKey
        val concat = if (compareLex(a, b) <= 0) a + b else b + a
        return crypto.hash(concat)
    }

    private fun compareLex(a: ByteArray, b: ByteArray): Int {
        val n = minOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a[i].toInt() and 0xff
            val y = b[i].toInt() and 0xff
            if (x != y) return x - y
        }
        return a.size - b.size
    }
}
