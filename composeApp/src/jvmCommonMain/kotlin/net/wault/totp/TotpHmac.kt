package net.wault.totp

import net.wault.crypto.CryptoApi
import net.wault.item.TotpAlgorithm
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual fun CryptoApi.hmacFor(algorithm: TotpAlgorithm, key: ByteArray, message: ByteArray): ByteArray {
    val name = when (algorithm) {
        TotpAlgorithm.Sha1 -> "HmacSHA1"
        TotpAlgorithm.Sha256 -> "HmacSHA256"
        TotpAlgorithm.Sha512 -> "HmacSHA512"
    }
    val mac = Mac.getInstance(name)
    mac.init(SecretKeySpec(key, name))
    return mac.doFinal(message)
}
