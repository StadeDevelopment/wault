package net.wault.security

import net.wault.crypto.CryptoApi
import net.wault.crypto.ENVELOPE_NONCE_LEN
import net.wault.crypto.Envelope
import net.wault.crypto.zero

private const val WRAP_KEY_LEN = 32

class SecretBox(
    private val crypto: CryptoApi,
    private val vault: Vault
) {
    fun seal(domain: ByteArray, label: String, plaintext: ByteArray): ByteArray {
        val generation = vault.keyGeneration()
        return vault.withDataKey { dataKey ->
            sealWith(dataKey, generation, domain, label, plaintext)
        }
    }

    fun sealWith(
        dataKey: ByteArray,
        generation: Int,
        domain: ByteArray,
        label: String,
        plaintext: ByteArray
    ): ByteArray {
        val wrapKey = crypto.hkdf(dataKey, label.encodeToByteArray(), domain, WRAP_KEY_LEN)
        try {
            val nonce = crypto.randomBytes(ENVELOPE_NONCE_LEN)
            val ciphertext = crypto.aeadSeal(wrapKey, nonce, plaintext, associatedData(label, generation))
            return Envelope.wrap(generation, nonce, ciphertext)
        } finally {
            wrapKey.zero()
        }
    }

    fun open(domain: ByteArray, label: String, sealed: ByteArray): ByteArray? {
        val envelope = Envelope.unwrap(sealed) ?: return null
        return vault.withDataKeyFor(envelope.generation) { dataKey ->
            openWith(dataKey, domain, label, sealed)
        }
    }

    fun openWith(dataKey: ByteArray, domain: ByteArray, label: String, sealed: ByteArray): ByteArray? {
        val envelope = Envelope.unwrap(sealed) ?: return null
        val wrapKey = crypto.hkdf(dataKey, label.encodeToByteArray(), domain, WRAP_KEY_LEN)
        try {
            return crypto.aeadOpen(
                wrapKey,
                envelope.nonce,
                envelope.ciphertext,
                associatedData(label, envelope.generation)
            )
        } finally {
            wrapKey.zero()
        }
    }

    fun generationOf(sealed: ByteArray): Int? = Envelope.generationOf(sealed)

    private fun associatedData(label: String, generation: Int): ByteArray =
        "$label|$generation".encodeToByteArray()

    companion object {
        val DEVICE_KEYS = "wault.device.keys.v1".encodeToByteArray()
        val DEVICE_ROOT = "wault.device.root.v1".encodeToByteArray()
        val RATCHET_STATE = "wault.ratchet.state.v1".encodeToByteArray()
    }
}
