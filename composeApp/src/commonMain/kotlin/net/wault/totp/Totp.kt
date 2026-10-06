package net.wault.totp

import net.wault.crypto.CryptoApi
import net.wault.crypto.base32ToBytes
import net.wault.crypto.zero
import net.wault.item.TotpAlgorithm
import net.wault.item.TotpSecret

data class TotpCode(
    val code: String,
    val secondsRemaining: Int,
    val periodSeconds: Int
)

class Totp(private val crypto: CryptoApi) {

    fun generate(secret: TotpSecret, nowMillis: Long): TotpCode {
        val period = secret.periodSeconds.coerceAtLeast(1)
        val epochSeconds = nowMillis / 1000L
        val counter = epochSeconds / period
        val remaining = (period - (epochSeconds % period)).toInt()
        return TotpCode(
            code = hotp(secret, counter),
            secondsRemaining = remaining,
            periodSeconds = period
        )
    }

    fun hotp(secret: TotpSecret, counter: Long): String {
        val key = secret.secret.base32ToBytes()
        require(key.isNotEmpty()) { "totp secret" }
        try {
            val message = ByteArray(8)
            var value = counter
            for (i in 7 downTo 0) {
                message[i] = (value and 0xFF).toByte()
                value = value ushr 8
            }
            val digest = crypto.hmacFor(secret.algorithm, key, message)
            val offset = (digest[digest.size - 1].toInt() and 0x0F)
            val binary = ((digest[offset].toInt() and 0x7F) shl 24) or
                ((digest[offset + 1].toInt() and 0xFF) shl 16) or
                ((digest[offset + 2].toInt() and 0xFF) shl 8) or
                (digest[offset + 3].toInt() and 0xFF)
            val digits = secret.digits.coerceIn(6, 8)
            val modulus = when (digits) {
                6 -> 1_000_000
                7 -> 10_000_000
                else -> 100_000_000
            }
            return (binary % modulus).toString().padStart(digits, '0')
        } finally {
            key.zero()
        }
    }
}

expect fun CryptoApi.hmacFor(algorithm: TotpAlgorithm, key: ByteArray, message: ByteArray): ByteArray

object OtpAuthUri {

    fun parse(uri: String): TotpSecret? {
        if (!uri.startsWith("otpauth://totp/", ignoreCase = true)) return null
        val withoutScheme = uri.removePrefix("otpauth://").removePrefix("OTPAUTH://")
        val label = withoutScheme.substringAfter('/').substringBefore('?')
        val query = withoutScheme.substringAfter('?', "")
        val params = query.split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=', "")
                val value = pair.substringAfter('=', "")
                if (key.isEmpty()) null else key.lowercase() to percentDecode(value)
            }
            .toMap()

        val secret = params["secret"]?.takeIf { it.isNotBlank() } ?: return null
        val decodedLabel = percentDecode(label)
        val labelIssuer = decodedLabel.substringBefore(':', "").trim()
        val account = decodedLabel.substringAfter(':', decodedLabel).trim()

        return TotpSecret(
            secret = secret.uppercase().filterNot { it == ' ' || it == '-' },
            issuer = params["issuer"]?.takeIf { it.isNotBlank() } ?: labelIssuer,
            account = account,
            digits = params["digits"]?.toIntOrNull()?.coerceIn(6, 8) ?: 6,
            periodSeconds = params["period"]?.toIntOrNull()?.coerceAtLeast(1) ?: 30,
            algorithm = when (params["algorithm"]?.uppercase()) {
                "SHA256" -> TotpAlgorithm.Sha256
                "SHA512" -> TotpAlgorithm.Sha512
                else -> TotpAlgorithm.Sha1
            }
        )
    }

    fun format(secret: TotpSecret): String {
        val label = if (secret.issuer.isNotBlank()) {
            "${percentEncode(secret.issuer)}:${percentEncode(secret.account)}"
        } else {
            percentEncode(secret.account)
        }
        val algorithm = when (secret.algorithm) {
            TotpAlgorithm.Sha1 -> "SHA1"
            TotpAlgorithm.Sha256 -> "SHA256"
            TotpAlgorithm.Sha512 -> "SHA512"
        }
        return "otpauth://totp/$label?secret=${secret.secret}" +
            "&issuer=${percentEncode(secret.issuer)}" +
            "&algorithm=$algorithm&digits=${secret.digits}&period=${secret.periodSeconds}"
    }

    private fun percentDecode(value: String): String {
        if (!value.contains('%') && !value.contains('+')) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            when {
                value[i] == '%' && i + 2 < value.length -> {
                    val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                    if (hex == null) {
                        bytes.add(value[i].code.toByte())
                        i++
                    } else {
                        bytes.add(hex.toByte())
                        i += 3
                    }
                }
                value[i] == '+' -> {
                    bytes.add(' '.code.toByte())
                    i++
                }
                else -> {
                    value[i].toString().encodeToByteArray().forEach { bytes.add(it) }
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun percentEncode(value: String): String {
        val builder = StringBuilder()
        for (byte in value.encodeToByteArray()) {
            val ch = byte.toInt().toChar()
            if (ch.isLetterOrDigit() || ch in "-._~") {
                builder.append(ch)
            } else {
                builder.append('%').append((byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0'))
            }
        }
        return builder.toString()
    }
}
