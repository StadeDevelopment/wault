package net.wault.autofill

import net.wault.item.ItemContent
import net.wault.item.ItemSearch
import net.wault.item.VaultItem

const val ANDROID_APP_SCHEME = "androidapp://"

object AutofillMatcher {

    fun candidates(
        items: List<VaultItem>,
        webDomain: String?,
        packageName: String?
    ): List<VaultItem> {
        val logins = items.filter { it.content is ItemContent.Login }
        if (logins.isEmpty()) return emptyList()

        val domain = webDomain?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (domain != null) {
            return ItemSearch.matchingUri(logins, "https://$domain")
        }

        val pkg = packageName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return emptyList()
        if (pkg in BROWSERS) return emptyList()

        val bound = logins.filter { item ->
            val login = item.content as ItemContent.Login
            login.uris.any { it.uri.equals(ANDROID_APP_SCHEME + pkg, ignoreCase = true) }
        }
        if (bound.isNotEmpty()) return bound

        val guessed = domainFromPackage(pkg) ?: return emptyList()
        return ItemSearch.matchingUri(logins, "https://$guessed")
    }

    fun domainFromPackage(packageName: String): String? {
        val parts = packageName.split('.').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val tld = parts[0].lowercase()
        if (tld !in KNOWN_TLDS) return null
        val name = parts[1].lowercase()
        if (name.isEmpty()) return null
        return "$name.$tld"
    }

    fun bindingUriFor(packageName: String): String = ANDROID_APP_SCHEME + packageName.lowercase()

    private val KNOWN_TLDS = setOf("com", "org", "net", "io", "dev", "co", "me", "app")

    private val BROWSERS = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.browser",
        "com.android.browser"
    )
}
