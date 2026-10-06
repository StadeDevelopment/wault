package net.wault.qr

data class QrMatrix(val size: Int, val modules: BooleanArray) {
    operator fun get(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0 || x >= size || y >= size) return false
        return modules[y * size + x]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrMatrix) return false
        return size == other.size && modules.contentEquals(other.modules)
    }

    override fun hashCode(): Int = size * 31 + modules.contentHashCode()
}

expect fun encodeQr(text: String): QrMatrix?

expect fun decodeQr(luminances: ByteArray, width: Int, height: Int): String?
