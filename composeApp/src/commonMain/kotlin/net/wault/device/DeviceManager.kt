package net.wault.device

import net.wault.crypto.CryptoApi
import net.wault.crypto.KeyPair
import net.wault.crypto.toHex
import net.wault.db.WaultDb
import net.wault.security.SecretBox
import net.wault.sync.PairedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KV_DEVICE_ID = "device.id"
private const val KV_DEVICE_LABEL = "device.label"
private const val KV_SIGNING_KEYS = "device.signingKeys"
private const val KV_AGREEMENT_KEYS = "device.agreementKeys"

data class LocalDevice(
    val id: String,
    val label: String,
    val platform: String,
    val signing: KeyPair,
    val agreement: KeyPair
)

data class PeerDevice(
    val id: String,
    val label: String,
    val platform: String,
    val signingKey: ByteArray,
    val agreementKey: ByteArray,
    val rootKey: ByteArray,
    val isInitiator: Boolean,
    val onionAddress: String?,
    val lanAddress: String?,
    val keyGeneration: Int
) {
    override fun equals(other: Any?): Boolean = other is PeerDevice && id == other.id
    override fun hashCode(): Int = id.hashCode()
}

class DeviceManager(
    private val db: WaultDb,
    private val crypto: CryptoApi,
    private val secretBox: SecretBox,
    private val platformName: String,
    private val defaultLabel: String,
    private val clock: () -> Long
) {
    private val _paired = MutableStateFlow<List<PairedDevice>>(emptyList())
    val paired: StateFlow<List<PairedDevice>> = _paired.asStateFlow()

    private var cachedLocal: LocalDevice? = null

    fun localDevice(): LocalDevice {
        cachedLocal?.let { return it }

        val existingId = readKv(KV_DEVICE_ID)?.decodeToString()
        val device = if (existingId == null) {
            provisionLocalDevice()
        } else {
            LocalDevice(
                id = existingId,
                label = readKv(KV_DEVICE_LABEL)?.decodeToString() ?: defaultLabel,
                platform = platformName,
                signing = readKeyPair(KV_SIGNING_KEYS) ?: crypto.generateSigningKeyPair(),
                agreement = readKeyPair(KV_AGREEMENT_KEYS) ?: crypto.generateAgreementKeyPair()
            )
        }
        cachedLocal = device
        return device
    }

    fun exportLocalIdentity(): LocalDevice = localDevice()

    fun restoreLocalIdentity(device: LocalDevice) {
        writeKv(KV_DEVICE_ID, device.id.encodeToByteArray())
        writeKv(KV_DEVICE_LABEL, device.label.encodeToByteArray())
        writeKeyPair(KV_SIGNING_KEYS, device.signing)
        writeKeyPair(KV_AGREEMENT_KEYS, device.agreement)
        cachedLocal = device
    }

    fun hasLocalIdentity(): Boolean = readKv(KV_DEVICE_ID) != null

    fun rename(label: String) {
        writeKv(KV_DEVICE_LABEL, label.encodeToByteArray())
        cachedLocal = cachedLocal?.copy(label = label)
    }

    fun load() {
        _paired.value = db.waultDbQueries.selectAllDevices().executeAsList().map { row ->
            PairedDevice(
                id = row.id,
                label = row.label,
                platform = row.platform,
                onionAddress = row.onionAddress,
                pairedAt = row.pairedAt,
                lastSyncedAt = row.lastSyncedAt,
                revoked = row.revoked != 0L,
                keyGeneration = row.keyGeneration.toInt()
            )
        }
    }

    fun remember(
        id: String,
        label: String,
        platform: String,
        signingKey: ByteArray,
        agreementKey: ByteArray,
        rootKey: ByteArray,
        isInitiator: Boolean,
        onionAddress: String?,
        lanAddress: String?,
        keyGeneration: Int
    ) {
        db.waultDbQueries.upsertDevice(
            id,
            label,
            platform,
            signingKey,
            agreementKey,
            secretBox.seal(SecretBox.DEVICE_ROOT, id, rootKey),
            if (isInitiator) 1L else 0L,
            onionAddress,
            lanAddress,
            clock(),
            0L,
            0L,
            keyGeneration.toLong()
        )
        load()
    }

    fun peer(deviceId: String): PeerDevice? {
        val row = db.waultDbQueries.selectDevice(deviceId).executeAsOneOrNull() ?: return null
        if (row.revoked != 0L) return null
        val rootKey = secretBox.open(SecretBox.DEVICE_ROOT, row.id, row.sealedRootKey) ?: return null
        return PeerDevice(
            id = row.id,
            label = row.label,
            platform = row.platform,
            signingKey = row.signingKey,
            agreementKey = row.agreementKey,
            rootKey = rootKey,
            isInitiator = row.isInitiator != 0L,
            onionAddress = row.onionAddress,
            lanAddress = row.lanAddress,
            keyGeneration = row.keyGeneration.toInt()
        )
    }

    fun peers(): List<PeerDevice> =
        db.waultDbQueries.selectDevices().executeAsList().mapNotNull { peer(it.id) }

    fun markKeyGeneration(deviceId: String, generation: Int) {
        db.waultDbQueries.markDeviceKeyGeneration(generation.toLong(), deviceId)
        load()
    }

    fun updateAddresses(deviceId: String, onionAddress: String?, lanAddress: String?) {
        db.waultDbQueries.updateDeviceAddresses(onionAddress, lanAddress, deviceId)
        load()
    }

    fun revoke(deviceId: String) {
        db.waultDbQueries.transaction {
            db.waultDbQueries.revokeDevice(deviceId)
            db.waultDbQueries.deleteRatchetSession(deviceId)
            db.waultDbQueries.upsertSyncCursor(deviceId, 0L, 0L)
        }
        load()
    }

    fun activePeers(): List<PairedDevice> = _paired.value.filterNot { it.revoked }

    private fun provisionLocalDevice(): LocalDevice {
        val device = LocalDevice(
            id = crypto.randomBytes(16).toHex(),
            label = defaultLabel,
            platform = platformName,
            signing = crypto.generateSigningKeyPair(),
            agreement = crypto.generateAgreementKeyPair()
        )
        writeKv(KV_DEVICE_ID, device.id.encodeToByteArray())
        writeKv(KV_DEVICE_LABEL, device.label.encodeToByteArray())
        writeKeyPair(KV_SIGNING_KEYS, device.signing)
        writeKeyPair(KV_AGREEMENT_KEYS, device.agreement)
        return device
    }

    private fun readKv(key: String): ByteArray? =
        db.waultDbQueries.getKv(key).executeAsOneOrNull()

    private fun writeKv(key: String, value: ByteArray) {
        db.waultDbQueries.putKv(key, value)
    }

    private fun readKeyPair(key: String): KeyPair? {
        val sealed = readKv(key) ?: return null
        val plaintext = secretBox.open(SecretBox.DEVICE_KEYS, key, sealed) ?: return null
        if (plaintext.isEmpty()) return null
        val split = plaintext[0].toInt() and 0xFF
        if (plaintext.size < 1 + split) return null
        return KeyPair(
            publicKey = plaintext.copyOfRange(1, 1 + split),
            privateKey = plaintext.copyOfRange(1 + split, plaintext.size)
        )
    }

    private fun writeKeyPair(key: String, keyPair: KeyPair) {
        require(keyPair.publicKey.size < 256) { "public key too large to frame" }
        val payload = byteArrayOf(keyPair.publicKey.size.toByte()) + keyPair.publicKey + keyPair.privateKey
        writeKv(key, secretBox.seal(SecretBox.DEVICE_KEYS, key, payload))
    }
}
