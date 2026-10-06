package net.wault.crypto

private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
private const val HEX_ALPHABET = "0123456789abcdef"

fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        out.append(HEX_ALPHABET[v ushr 4])
        out.append(HEX_ALPHABET[v and 0x0F])
    }
    return out.toString()
}

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex length" }
    val out = ByteArray(length / 2)
    for (i in out.indices) {
        val hi = HEX_ALPHABET.indexOf(this[i * 2].lowercaseChar())
        val lo = HEX_ALPHABET.indexOf(this[i * 2 + 1].lowercaseChar())
        require(hi >= 0 && lo >= 0) { "hex char" }
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

fun ByteArray.toBase32(): String {
    if (isEmpty()) return ""
    val out = StringBuilder()
    var buffer = 0
    var bits = 0
    for (b in this) {
        buffer = (buffer shl 8) or (b.toInt() and 0xFF)
        bits += 8
        while (bits >= 5) {
            out.append(BASE32_ALPHABET[(buffer ushr (bits - 5)) and 0x1F])
            bits -= 5
        }
    }
    if (bits > 0) out.append(BASE32_ALPHABET[(buffer shl (5 - bits)) and 0x1F])
    return out.toString()
}

fun String.base32ToBytes(): ByteArray {
    val cleaned = uppercase().filter { it != '=' && !it.isWhitespace() && it != '-' }
    val out = ArrayList<Byte>(cleaned.length * 5 / 8)
    var buffer = 0
    var bits = 0
    for (c in cleaned) {
        val idx = BASE32_ALPHABET.indexOf(c)
        require(idx >= 0) { "base32 char" }
        buffer = (buffer shl 5) or idx
        bits += 5
        if (bits >= 8) {
            out.add(((buffer ushr (bits - 8)) and 0xFF).toByte())
            bits -= 8
        }
    }
    return out.toByteArray()
}
