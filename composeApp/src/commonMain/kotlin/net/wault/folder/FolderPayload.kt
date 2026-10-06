package net.wault.folder

private const val MARKER = '\u0000'

object FolderPayload {

    fun encode(name: String, icon: String): String {
        val trimmedName = name.trim()
        val trimmedIcon = icon.trim()
        if (trimmedIcon.isEmpty()) return trimmedName
        return "$MARKER$trimmedIcon$MARKER$trimmedName"
    }

    fun decodeName(raw: String): String {
        if (!raw.startsWith(MARKER)) return raw
        val end = raw.indexOf(MARKER, startIndex = 1)
        if (end < 0) return raw
        return raw.substring(end + 1)
    }

    fun decodeIcon(raw: String): String {
        if (!raw.startsWith(MARKER)) return ""
        val end = raw.indexOf(MARKER, startIndex = 1)
        if (end < 0) return ""
        return raw.substring(1, end)
    }
}
