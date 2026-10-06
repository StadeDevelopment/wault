package net.wault.totp

import net.wault.crypto.toBase32
import net.wault.item.TotpAlgorithm
import net.wault.item.TotpSecret

data class ImportedCode(
    val secret: TotpSecret,
    val label: String
)

enum class TotpImportFormat(val displayName: String) {
    OtpAuthUri("otpauth"),
    GoogleAuthenticator("Google Authenticator"),
    Aegis("Aegis"),
    TwoFas("2FAS"),
    AndOtp("andOTP")
}

data class TotpImportPreview(
    val format: TotpImportFormat,
    val codes: List<ImportedCode>,
    val skipped: Int
)

object TotpImport {

    fun preview(payload: String): TotpImportPreview? {
        val text = payload.trim()
        if (text.isEmpty()) return null

        migration(text)?.let { return it }
        uris(text)?.let { return it }
        json(text)?.let { return it }
        return null
    }

    private fun uris(text: String): TotpImportPreview? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        if (lines.none { it.startsWith("otpauth://", ignoreCase = true) }) return null

        var skipped = 0
        val codes = lines.mapNotNull { line ->
            if (!line.startsWith("otpauth://", ignoreCase = true)) {
                skipped += 1
                return@mapNotNull null
            }
            val secret = OtpAuthUri.parse(line)
            if (secret == null) {
                skipped += 1
                null
            } else {
                ImportedCode(secret, labelFor(secret))
            }
        }
        if (codes.isEmpty()) return null
        return TotpImportPreview(TotpImportFormat.OtpAuthUri, codes, skipped)
    }

    private fun migration(text: String): TotpImportPreview? {
        if (!text.startsWith("otpauth-migration://", ignoreCase = true)) return null
        val data = queryParam(text, "data") ?: return null
        val bytes = decodeMigrationData(percentDecode(data)) ?: return null
        val parsed = runCatching { GoogleAuthenticatorPayload.parse(bytes) }.getOrNull() ?: return null
        if (parsed.codes.isEmpty()) return null
        return TotpImportPreview(TotpImportFormat.GoogleAuthenticator, parsed.codes, parsed.skipped)
    }

    private fun json(text: String): TotpImportPreview? {
        if (!text.startsWith("{") && !text.startsWith("[")) return null

        val format = when {
            text.contains("\"db\"") && text.contains("\"entries\"") -> TotpImportFormat.Aegis
            text.contains("\"services\"") -> TotpImportFormat.TwoFas
            else -> TotpImportFormat.AndOtp
        }

        var skipped = 0
        val codes = ArrayList<ImportedCode>()

        for (block in objectBlocks(text)) {
            val secretValue = jsonString(block, "secret")
            if (secretValue.isNullOrBlank()) continue

            val type = jsonString(block, "type")?.lowercase()
            if (type != null && type != "totp") {
                skipped += 1
                continue
            }

            val issuer = jsonString(block, "issuer").orEmpty()
            val account = jsonString(block, "name")
                ?: jsonString(block, "account")
                ?: jsonString(block, "label")
                ?: ""
            val digits = jsonInt(block, "digits") ?: 6
            val period = jsonInt(block, "period") ?: 30
            val algorithm = when (jsonString(block, "algorithm")?.uppercase()) {
                "SHA256" -> TotpAlgorithm.Sha256
                "SHA512" -> TotpAlgorithm.Sha512
                else -> TotpAlgorithm.Sha1
            }

            val secret = TotpSecret(
                secret = secretValue.filterNot { it == ' ' || it == '-' }.uppercase(),
                issuer = issuer,
                account = account,
                digits = digits,
                periodSeconds = period,
                algorithm = algorithm
            )
            codes.add(ImportedCode(secret, labelFor(secret)))
        }

        if (codes.isEmpty()) return null
        return TotpImportPreview(format, codes, skipped)
    }

    private fun decodeMigrationData(value: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val cleaned = value.trim()
            .replace(' ', '+')
            .replace('-', '+')
            .replace('_', '/')
            .filterNot { it == '\n' || it == '\r' || it == '\t' }
            .trimEnd('=')
        if (cleaned.isEmpty()) return null

        val out = ArrayList<Byte>(cleaned.length * 3 / 4 + 3)
        var buffer = 0
        var bits = 0
        for (ch in cleaned) {
            val index = alphabet.indexOf(ch)
            if (index < 0) return null
            buffer = (buffer shl 6) or index
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.add(((buffer shr bits) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }

    internal fun labelFor(secret: TotpSecret): String = when {
        secret.issuer.isNotBlank() && secret.account.isNotBlank() ->
            "${secret.issuer} (${secret.account})"
        secret.issuer.isNotBlank() -> secret.issuer
        secret.account.isNotBlank() -> secret.account
        else -> "Authenticator"
    }

    private fun queryParam(uri: String, name: String): String? {
        val query = uri.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val key = pair.substringBefore('=')
            if (key == name) return pair.substringAfter('=', "")
        }
        return null
    }

    private fun percentDecode(value: String): String {
        val builder = StringBuilder()
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            when {
                ch == '%' && index + 2 < value.length -> {
                    val code = value.substring(index + 1, index + 3).toIntOrNull(16)
                    if (code == null) {
                        builder.append(ch)
                        index += 1
                    } else {
                        builder.append(code.toChar())
                        index += 3
                    }
                }
                ch == '+' -> {
                    builder.append(' ')
                    index += 1
                }
                else -> {
                    builder.append(ch)
                    index += 1
                }
            }
        }
        return builder.toString()
    }

    private fun objectBlocks(text: String): List<String> {
        val blocks = ArrayList<String>()
        var depth = 0
        var start = -1
        for (index in text.indices) {
            when (text[index]) {
                '{' -> {
                    if (depth == 0) start = index
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0 && start >= 0) {
                        blocks.add(text.substring(start, index + 1))
                        start = -1
                    }
                }
            }
        }
        return blocks
    }

    private fun jsonString(block: String, key: String): String? {
        val marker = "\"$key\""
        val at = block.indexOf(marker)
        if (at < 0) return null
        var index = at + marker.length
        while (index < block.length && (block[index] == ' ' || block[index] == ':')) index += 1
        if (index >= block.length || block[index] != '"') return null
        index += 1
        val builder = StringBuilder()
        while (index < block.length && block[index] != '"') {
            if (block[index] == '\\' && index + 1 < block.length) index += 1
            builder.append(block[index])
            index += 1
        }
        return builder.toString()
    }

    private fun jsonInt(block: String, key: String): Int? {
        val marker = "\"$key\""
        val at = block.indexOf(marker)
        if (at < 0) return null
        var index = at + marker.length
        while (index < block.length && (block[index] == ' ' || block[index] == ':')) index += 1
        val digits = StringBuilder()
        while (index < block.length && block[index].isDigit()) {
            digits.append(block[index])
            index += 1
        }
        return digits.toString().toIntOrNull()
    }
}

internal object GoogleAuthenticatorPayload {

    data class Result(val codes: List<ImportedCode>, val skipped: Int)

    fun parse(bytes: ByteArray): Result {
        val codes = ArrayList<ImportedCode>()
        var skipped = 0
        val reader = ProtoReader(bytes)

        while (reader.hasMore()) {
            val (field, wire) = reader.readTag() ?: break
            if (field == 1 && wire == 2) {
                val chunk = reader.readBytes() ?: break
                val parameters = parseParameters(chunk)
                if (parameters == null) skipped += 1 else codes.add(parameters)
            } else {
                if (!reader.skip(wire)) break
            }
        }
        return Result(codes, skipped)
    }

    private fun parseParameters(bytes: ByteArray): ImportedCode? {
        val reader = ProtoReader(bytes)
        var secretBytes: ByteArray? = null
        var name = ""
        var issuer = ""
        var algorithm = TotpAlgorithm.Sha1
        var digits = 6
        var isTotp = true

        while (reader.hasMore()) {
            val (field, wire) = reader.readTag() ?: break
            when {
                field == 1 && wire == 2 -> secretBytes = reader.readBytes() ?: return null
                field == 2 && wire == 2 -> name = reader.readBytes()?.decodeToString() ?: ""
                field == 3 && wire == 2 -> issuer = reader.readBytes()?.decodeToString() ?: ""
                field == 4 && wire == 0 -> {
                    algorithm = when (reader.readVarint()?.toInt()) {
                        2 -> TotpAlgorithm.Sha256
                        3 -> TotpAlgorithm.Sha512
                        else -> TotpAlgorithm.Sha1
                    }
                }
                field == 5 && wire == 0 -> {
                    digits = if (reader.readVarint()?.toInt() == 2) 8 else 6
                }
                field == 6 && wire == 0 -> {
                    isTotp = reader.readVarint()?.toInt() != 1
                }
                else -> if (!reader.skip(wire)) break
            }
        }

        val raw = secretBytes ?: return null
        if (raw.isEmpty()) return null
        if (!isTotp) return null

        val secret = TotpSecret(
            secret = raw.toBase32(),
            issuer = issuer,
            account = name,
            digits = digits,
            periodSeconds = 30,
            algorithm = algorithm
        )
        return ImportedCode(secret, TotpImport.labelFor(secret))
    }
}

internal class ProtoReader(private val bytes: ByteArray) {
    private var offset = 0

    fun hasMore(): Boolean = offset < bytes.size

    fun readTag(): Pair<Int, Int>? {
        val value = readVarint() ?: return null
        val field = (value ushr 3).toInt()
        val wire = (value and 0x7L).toInt()
        if (field == 0) return null
        return field to wire
    }

    fun readVarint(): Long? {
        var result = 0L
        var shift = 0
        while (offset < bytes.size && shift < 64) {
            val byte = bytes[offset].toInt() and 0xFF
            offset += 1
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        return null
    }

    fun readBytes(): ByteArray? {
        val length = readVarint()?.toInt() ?: return null
        if (length < 0 || offset + length > bytes.size) return null
        val out = bytes.copyOfRange(offset, offset + length)
        offset += length
        return out
    }

    fun skip(wire: Int): Boolean = when (wire) {
        0 -> readVarint() != null
        1 -> { offset += 8; offset <= bytes.size }
        2 -> readBytes() != null
        5 -> { offset += 4; offset <= bytes.size }
        else -> false
    }
}
