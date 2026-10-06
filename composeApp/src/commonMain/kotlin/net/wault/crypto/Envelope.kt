package net.wault.crypto

const val ENVELOPE_VERSION: Byte = 0x02
const val ENVELOPE_NONCE_LEN = 12
const val ENVELOPE_HEADER_LEN = 1 + 4 + ENVELOPE_NONCE_LEN

data class EnvelopeHeader(val generation: Int, val nonce: ByteArray, val ciphertext: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnvelopeHeader) return false
        return generation == other.generation &&
            nonce.contentEquals(other.nonce) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int =
        (generation * 31 + nonce.contentHashCode()) * 31 + ciphertext.contentHashCode()
}

object Envelope {

    fun wrap(generation: Int, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        require(nonce.size == ENVELOPE_NONCE_LEN) { "nonce size" }
        val out = ByteArray(ENVELOPE_HEADER_LEN + ciphertext.size)
        out[0] = ENVELOPE_VERSION
        out[1] = ((generation ushr 24) and 0xff).toByte()
        out[2] = ((generation ushr 16) and 0xff).toByte()
        out[3] = ((generation ushr 8) and 0xff).toByte()
        out[4] = (generation and 0xff).toByte()
        nonce.copyInto(out, 5)
        ciphertext.copyInto(out, ENVELOPE_HEADER_LEN)
        return out
    }

    fun unwrap(bytes: ByteArray): EnvelopeHeader? {
        if (bytes.size <= ENVELOPE_HEADER_LEN) return null
        if (bytes[0] != ENVELOPE_VERSION) return null
        val generation = ((bytes[1].toInt() and 0xff) shl 24) or
            ((bytes[2].toInt() and 0xff) shl 16) or
            ((bytes[3].toInt() and 0xff) shl 8) or
            (bytes[4].toInt() and 0xff)
        return EnvelopeHeader(
            generation = generation,
            nonce = bytes.copyOfRange(5, ENVELOPE_HEADER_LEN),
            ciphertext = bytes.copyOfRange(ENVELOPE_HEADER_LEN, bytes.size)
        )
    }

    fun generationOf(bytes: ByteArray): Int? = unwrap(bytes)?.generation
}
