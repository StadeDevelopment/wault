package net.wault.crypto

interface CryptoApi {
    fun randomBytes(size: Int): ByteArray
    fun generateSigningKeyPair(): KeyPair
    fun generateAgreementKeyPair(): KeyPair
    fun sign(privateKey: ByteArray, data: ByteArray): ByteArray
    fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
    fun keyAgreement(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray
    fun hash(data: ByteArray): ByteArray
    fun hmac(key: ByteArray, data: ByteArray): ByteArray
    fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
    fun argon2id(password: ByteArray, salt: ByteArray, params: KdfParams, length: Int): ByteArray
    fun aeadSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray
    fun aeadOpen(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, associatedData: ByteArray = ByteArray(0)): ByteArray?
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean
}

data class KdfParams(
    val memoryKib: Int = DEFAULT_MEMORY_KIB,
    val iterations: Int = DEFAULT_ITERATIONS,
    val parallelism: Int = DEFAULT_PARALLELISM
) {
    companion object {
        const val DEFAULT_MEMORY_KIB: Int = 64 * 1024
        const val DEFAULT_ITERATIONS: Int = 3
        const val DEFAULT_PARALLELISM: Int = 4

        val Interactive = KdfParams(memoryKib = 19 * 1024, iterations = 2, parallelism = 1)
        val Balanced = KdfParams()
        val Hardened = KdfParams(memoryKib = 256 * 1024, iterations = 4, parallelism = 4)
    }
}

data class KeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }

    override fun hashCode(): Int = publicKey.contentHashCode() * 31 + privateKey.contentHashCode()
}

fun ByteArray.zero() {
    for (i in indices) this[i] = 0
}

expect fun platformCrypto(): CryptoApi
