package net.wault.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

private const val TAG_BITS = 128
private const val HASH_LEN = 32

class JvmCrypto : CryptoApi {

    private val rng = SecureRandom()

    override fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    override fun generateSigningKeyPair(): KeyPair {
        val priv = Ed25519PrivateKeyParameters(rng)
        return KeyPair(priv.generatePublicKey().encoded, priv.encoded)
    }

    override fun generateAgreementKeyPair(): KeyPair {
        val priv = X25519PrivateKeyParameters(rng)
        return KeyPair(priv.generatePublicKey().encoded, priv.encoded)
    }

    override fun sign(privateKey: ByteArray, data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    override fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean = runCatching {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(data, 0, data.size)
        verifier.verifySignature(signature)
    }.getOrDefault(false)

    override fun keyAgreement(privateKey: ByteArray, peerPublicKey: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(privateKey, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPublicKey, 0), shared, 0)
        return shared
    }

    override fun hash(data: ByteArray): ByteArray {
        val digest = Blake2bDigest(HASH_LEN * 8)
        digest.update(data, 0, data.size)
        val out = ByteArray(HASH_LEN)
        digest.doFinal(out, 0)
        return out
    }

    override fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val out = ByteArray(mac.macSize)
        mac.doFinal(out, 0)
        return out
    }

    override fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(secret, salt, info))
        val out = ByteArray(length)
        generator.generateBytes(out, 0, length)
        return out
    }

    override fun argon2id(password: ByteArray, salt: ByteArray, params: KdfParams, length: Int): ByteArray {
        val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memoryKib)
            .withParallelism(params.parallelism)
            .withSalt(salt)
        val generator = Argon2BytesGenerator()
        generator.init(builder.build())
        val out = ByteArray(length)
        generator.generateBytes(password, out, 0, length)
        return out
    }

    override fun aeadSeal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonce, associatedData))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        var offset = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        offset += cipher.doFinal(out, offset)
        return if (offset == out.size) out else out.copyOf(offset)
    }

    override fun aeadOpen(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        associatedData: ByteArray
    ): ByteArray? = runCatching {
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), TAG_BITS, nonce, associatedData))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        var offset = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        offset += cipher.doFinal(out, offset)
        if (offset == out.size) out else out.copyOf(offset)
    }.getOrNull()

    override fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}

actual fun platformCrypto(): CryptoApi = JvmCrypto()
