package net.wault.sync

import net.wault.crypto.CryptoApi
import net.wault.crypto.base64UrlToBytes
import net.wault.crypto.toBase64Url
import net.wault.crypto.zero
import net.wault.device.LocalDevice
import kotlinx.serialization.json.Json

private val PAIRING_INFO = "wault.pairing.v1".encodeToByteArray()
private val DATAKEY_INFO = "wault.pairing.datakey.v1".encodeToByteArray()
private val SAS_INFO = "wault.pairing.sas.v1".encodeToByteArray()
private val ROOT_INFO = "wault.pairing.root.v1".encodeToByteArray()
private const val NONCE_LEN = 12
private const val SESSION_KEY_LEN = 32
private const val PAIRING_NONCE_LEN = 32
private const val SAS_DIGITS = 6

sealed interface PairingResult {
    data class Success(val peer: PairingOffer, val dataKey: ByteArray) : PairingResult
    data object VaultMismatch : PairingResult
    data object BadSignature : PairingResult
    data object Undecryptable : PairingResult
}

class PairingService(private val crypto: CryptoApi) {

    private val json = Json { ignoreUnknownKeys = true }

    fun createOffer(
        vaultId: String,
        device: LocalDevice,
        onionAddress: String?,
        lanHint: String?
    ): PairingOffer = PairingOffer(
        vaultId = vaultId,
        deviceId = device.id,
        label = device.label,
        platform = device.platform,
        signingKey = device.signing.publicKey,
        agreementKey = device.agreement.publicKey,
        onionAddress = onionAddress,
        lanHint = lanHint,
        pairingNonce = crypto.randomBytes(PAIRING_NONCE_LEN)
    )

    fun encodeOffer(offer: PairingOffer): String =
        "wault://pair?d=" + json.encodeToString(PairingOffer.serializer(), offer).encodeToByteArray().toBase64Url()

    fun decodeOffer(scanned: String): PairingOffer? {
        val encoded = scanned.trim().substringAfter("wault://pair?d=", "").takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            json.decodeFromString(PairingOffer.serializer(), encoded.base64UrlToBytes().decodeToString())
        }.getOrNull()
    }

    fun sessionKey(local: LocalDevice, peer: PairingOffer, localNonce: ByteArray): ByteArray {
        val shared = crypto.keyAgreement(local.agreement.privateKey, peer.agreementKey)
        try {
            val salt = orderedConcat(localNonce, peer.pairingNonce)
            return crypto.hkdf(shared, salt, PAIRING_INFO, SESSION_KEY_LEN)
        } finally {
            shared.zero()
        }
    }

    fun shortAuthString(sessionKey: ByteArray, initiator: PairingOffer, responder: PairingOffer): String {
        val transcript = orderedConcat(
            initiator.agreementKey + initiator.signingKey,
            responder.agreementKey + responder.signingKey
        )
        val digest = crypto.hkdf(sessionKey, transcript, SAS_INFO, 8)
        var value = 0L
        for (i in 0 until 4) {
            value = (value shl 8) or (digest[i].toLong() and 0xFF)
        }
        val modulus = 1_000_000L
        return (value % modulus).toString().padStart(SAS_DIGITS, '0')
    }

    fun sealDataKey(sessionKey: ByteArray, dataKey: ByteArray, transcript: ByteArray): ByteArray {
        val wrapKey = crypto.hkdf(sessionKey, transcript, DATAKEY_INFO, SESSION_KEY_LEN)
        try {
            val nonce = crypto.randomBytes(NONCE_LEN)
            return nonce + crypto.aeadSeal(wrapKey, nonce, dataKey, transcript)
        } finally {
            wrapKey.zero()
        }
    }

    fun openDataKey(sessionKey: ByteArray, sealed: ByteArray, transcript: ByteArray): ByteArray? {
        if (sealed.size <= NONCE_LEN) return null
        val wrapKey = crypto.hkdf(sessionKey, transcript, DATAKEY_INFO, SESSION_KEY_LEN)
        try {
            val nonce = sealed.copyOfRange(0, NONCE_LEN)
            return crypto.aeadOpen(wrapKey, nonce, sealed.copyOfRange(NONCE_LEN, sealed.size), transcript)
        } finally {
            wrapKey.zero()
        }
    }

    fun rootKeyFor(sessionKey: ByteArray, transcript: ByteArray): ByteArray =
        crypto.hkdf(sessionKey, transcript, ROOT_INFO, SESSION_KEY_LEN)

    fun transcript(initiator: PairingOffer, responder: PairingOffer): ByteArray =
        orderedConcat(
            initiator.vaultId.encodeToByteArray() + initiator.deviceId.encodeToByteArray() + initiator.pairingNonce,
            responder.vaultId.encodeToByteArray() + responder.deviceId.encodeToByteArray() + responder.pairingNonce
        )

    private fun orderedConcat(a: ByteArray, b: ByteArray): ByteArray =
        if (compareBytes(a, b) <= 0) a + b else b + a

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        val common = minOf(a.size, b.size)
        for (i in 0 until common) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }
}
