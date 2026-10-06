package net.wault.item

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UriMatchTest {

    private fun login(vararg uris: MatchableUri): List<VaultItem> = listOf(
        VaultItem(
            id = "1",
            content = ItemContent.Login(title = "Site", uris = uris.toList()),
            createdAt = 0,
            updatedAt = 0,
            revision = 1,
            updatedBy = "d"
        )
    )

    private fun hits(uri: MatchableUri, target: String): Boolean =
        ItemSearch.matchingUri(login(uri), target).isNotEmpty()

    @Test
    fun `a regex matches what it describes`() {
        val rule = MatchableUri("""^https://[a-z0-9-]+\.example\.com/login""", UriMatch.Regex)
        assertTrue(hits(rule, "https://eu-west.example.com/login?next=1"))
        assertTrue(hits(rule, "https://app.example.com/login"))
        assertFalse(hits(rule, "https://example.com/login"))
        assertFalse(hits(rule, "https://app.example.com/signup"))
    }

    @Test
    fun `a regex ignores case`() {
        val rule = MatchableUri("github\\.com", UriMatch.Regex)
        assertTrue(hits(rule, "https://GitHub.COM/settings"))
    }

    @Test
    fun `an invalid regex matches nothing instead of crashing`() {
        val broken = MatchableUri("([unclosed", UriMatch.Regex)
        assertFalse(hits(broken, "https://anything.com"))
        assertFalse(ItemSearch.isValidRegex("([unclosed"))
        assertTrue(ItemSearch.isValidRegex("^https://example\\.com"))
    }

    @Test
    fun `an absurdly long pattern is refused`() {
        val huge = MatchableUri("a".repeat(5000), UriMatch.Regex)
        assertFalse(hits(huge, "aaaa"))
        assertFalse(ItemSearch.isValidRegex("a".repeat(5000)))
    }

    @Test
    fun `an empty uri never matches`() {
        assertFalse(hits(MatchableUri("", UriMatch.Domain), "https://example.com"))
        assertFalse(hits(MatchableUri("", UriMatch.Regex), "https://example.com"))
    }

    @Test
    fun `never means never even when the uri is identical`() {
        assertFalse(hits(MatchableUri("https://example.com", UriMatch.Never), "https://example.com"))
    }

    @Test
    fun `exact matching needs the whole url`() {
        val rule = MatchableUri("https://example.com/login", UriMatch.Exact)
        assertTrue(hits(rule, "https://example.com/login"))
        assertFalse(hits(rule, "https://example.com/login?next=1"))
    }

    @Test
    fun `starts with matches a prefix`() {
        val rule = MatchableUri("https://example.com/app", UriMatch.StartsWith)
        assertTrue(hits(rule, "https://example.com/app/settings"))
        assertFalse(hits(rule, "https://example.com/other"))
    }

    @Test
    fun `host matching does not leak across subdomains`() {
        val rule = MatchableUri("https://mail.example.com", UriMatch.Host)
        assertTrue(hits(rule, "https://mail.example.com/inbox"))
        assertFalse(hits(rule, "https://other.example.com/inbox"))
    }

    @Test
    fun `domain matching spans subdomains but not lookalikes`() {
        val rule = MatchableUri("https://example.com", UriMatch.Domain)
        assertTrue(hits(rule, "https://mail.example.com"))
        assertFalse(hits(rule, "https://example.com.evil.net"))
        assertFalse(hits(rule, "https://notexample.com"))
    }

    @Test
    fun `a target with no host can still match by exact, prefix or regex`() {
        assertTrue(hits(MatchableUri("androidapp://com.bank.app", UriMatch.Exact), "androidapp://com.bank.app"))
        assertTrue(hits(MatchableUri("androidapp://com.bank", UriMatch.StartsWith), "androidapp://com.bank.app"))
        assertTrue(hits(MatchableUri("com\\.bank", UriMatch.Regex), "androidapp://com.bank.app"))
    }

    @Test
    fun `a blank target matches nothing`() {
        assertFalse(hits(MatchableUri("https://example.com", UriMatch.Domain), ""))
        assertFalse(hits(MatchableUri(".*", UriMatch.Regex), ""))
    }

    @Test
    fun `one item with several rules matches if any rule matches`() {
        val items = login(
            MatchableUri("https://old.example.com", UriMatch.Host),
            MatchableUri("https://new.example.org", UriMatch.Host)
        )
        assertTrue(ItemSearch.matchingUri(items, "https://new.example.org/login").isNotEmpty())
    }
}
