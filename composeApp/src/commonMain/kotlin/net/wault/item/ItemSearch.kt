package net.wault.item

object ItemSearch {

    fun filter(items: List<VaultItem>, query: String): List<VaultItem> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return items
        return items
            .mapNotNull { item -> score(item, needle)?.let { it to item } }
            .sortedWith(compareByDescending<Pair<Int, VaultItem>> { it.first }.thenBy { it.second.title.lowercase() })
            .map { it.second }
    }

    fun matchingUri(items: List<VaultItem>, target: String): List<VaultItem> {
        val targetHost = hostOf(target) ?: return emptyList()
        val targetDomain = registrableDomain(targetHost)
        return items.filter { item ->
            val login = item.content as? ItemContent.Login ?: return@filter false
            login.uris.any { candidate -> matches(candidate, target, targetHost, targetDomain) }
        }
    }

    private fun matches(candidate: MatchableUri, target: String, targetHost: String, targetDomain: String): Boolean {
        val candidateHost = hostOf(candidate.uri)
        return when (candidate.match) {
            UriMatch.Never -> false
            UriMatch.Exact -> candidate.uri.equals(target, ignoreCase = true)
            UriMatch.StartsWith -> target.startsWith(candidate.uri, ignoreCase = true)
            UriMatch.Host -> candidateHost != null && candidateHost.equals(targetHost, ignoreCase = true)
            UriMatch.Domain -> candidateHost != null &&
                registrableDomain(candidateHost).equals(targetDomain, ignoreCase = true)
        }
    }

    private fun score(item: VaultItem, needle: String): Int? {
        val title = item.title.lowercase()
        if (title == needle) return 100
        if (title.startsWith(needle)) return 80
        if (title.contains(needle)) return 60
        when (val content = item.content) {
            is ItemContent.Login -> {
                if (content.username.lowercase().contains(needle)) return 50
                if (content.uris.any { it.uri.lowercase().contains(needle) }) return 40
            }
            is ItemContent.Identity -> {
                if (content.email.lowercase().contains(needle)) return 50
                if ("${content.firstName} ${content.lastName}".lowercase().contains(needle)) return 45
            }
            is ItemContent.Card -> {
                if (content.cardholderName.lowercase().contains(needle)) return 45
                if (content.brand.lowercase().contains(needle)) return 35
            }
            else -> Unit
        }
        if (item.content.notes.lowercase().contains(needle)) return 20
        if (item.content.customFields.any { it.name.lowercase().contains(needle) }) return 15
        return null
    }

    fun hostOf(uri: String): String? {
        val withoutScheme = uri.substringAfter("://", uri)
        val authority = withoutScheme.substringBefore('/').substringBefore('?').substringAfter('@')
        val host = authority.substringBefore(':').trim().lowercase()
        return host.takeIf { it.isNotEmpty() && it.contains('.') || it == "localhost" }
    }

    fun registrableDomain(host: String): String {
        val labels = host.split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in MULTIPART_SUFFIXES && labels.size >= 3) {
            labels.takeLast(3).joinToString(".")
        } else {
            lastTwo
        }
    }

    private val MULTIPART_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk",
        "com.tr", "org.tr", "net.tr", "gov.tr", "edu.tr", "k12.tr",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "com.br", "com.cn", "com.mx", "com.ar", "com.sg", "com.hk",
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
        "co.kr", "or.kr", "ne.kr", "go.kr",
        "co.in", "net.in", "org.in", "gov.in",
        "co.za", "org.za", "co.nz", "net.nz", "org.nz"
    )
}
