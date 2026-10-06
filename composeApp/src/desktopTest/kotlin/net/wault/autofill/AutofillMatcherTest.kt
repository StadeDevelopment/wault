package net.wault.autofill

import net.wault.item.ItemContent
import net.wault.item.MatchableUri
import net.wault.item.VaultItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutofillMatcherTest {

    private fun login(id: String, title: String, vararg uris: String) = VaultItem(
        id = id,
        content = ItemContent.Login(
            title = title,
            username = "eren",
            password = "pw",
            uris = uris.map { MatchableUri(it) }
        ),
        createdAt = 0,
        updatedAt = 0,
        revision = 1,
        updatedBy = "device-a"
    )

    private val items = listOf(
        login("1", "GitHub", "https://github.com"),
        login("2", "Bank", "https://login.bank.co.uk"),
        login("3", "Mail", "https://mail.example.org"),
        login("4", "Signal", "androidapp://org.thoughtcrime.securesms")
    )

    @Test
    fun `a web domain matches by registrable domain`() {
        val hits = AutofillMatcher.candidates(items, webDomain = "github.com", packageName = null)
        assertEquals(listOf("GitHub"), hits.map { it.title })
    }

    @Test
    fun `a subdomain still matches the parent entry`() {
        val hits = AutofillMatcher.candidates(items, webDomain = "gist.github.com", packageName = null)
        assertEquals(listOf("GitHub"), hits.map { it.title })
    }

    @Test
    fun `a look-alike domain does not match`() {
        val hits = AutofillMatcher.candidates(items, webDomain = "evil-github.com", packageName = null)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `an app bound by androidapp uri matches its package`() {
        val hits = AutofillMatcher.candidates(
            items,
            webDomain = null,
            packageName = "org.thoughtcrime.securesms"
        )
        assertEquals(listOf("Signal"), hits.map { it.title })
    }

    @Test
    fun `a package falls back to its guessed domain`() {
        val hits = AutofillMatcher.candidates(items, webDomain = null, packageName = "com.github.android")
        assertEquals(listOf("GitHub"), hits.map { it.title })
    }

    @Test
    fun `browsers never match by package name`() {
        val hits = AutofillMatcher.candidates(items, webDomain = null, packageName = "com.android.chrome")
        assertTrue(hits.isEmpty(), "a browser package must not leak unrelated credentials")
    }

    @Test
    fun `the web domain wins over the package when both are present`() {
        val hits = AutofillMatcher.candidates(
            items,
            webDomain = "bank.co.uk",
            packageName = "com.github.android"
        )
        assertEquals(listOf("Bank"), hits.map { it.title })
    }

    @Test
    fun `package to domain handles common tlds`() {
        assertEquals("github.com", AutofillMatcher.domainFromPackage("com.github.android"))
        assertEquals("example.org", AutofillMatcher.domainFromPackage("org.example.app"))
        assertEquals("stade.dev", AutofillMatcher.domainFromPackage("dev.stade"))
    }

    @Test
    fun `package to domain gives up on unusual prefixes`() {
        assertNull(AutofillMatcher.domainFromPackage("androidx.core"))
        assertNull(AutofillMatcher.domainFromPackage("single"))
    }

    @Test
    fun `an unknown site offers nothing`() {
        val hits = AutofillMatcher.candidates(items, webDomain = "unrelated.com", packageName = null)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `notes and cards are never offered for autofill`() {
        val mixed = items + VaultItem(
            id = "5",
            content = ItemContent.Note(title = "github notes", body = "x"),
            createdAt = 0, updatedAt = 0, revision = 1, updatedBy = "d"
        )
        val hits = AutofillMatcher.candidates(mixed, webDomain = "github.com", packageName = null)
        assertEquals(listOf("GitHub"), hits.map { it.title })
    }

    @Test
    fun `the binding uri is lowercased`() {
        assertEquals("androidapp://com.example.app", AutofillMatcher.bindingUriFor("Com.Example.App"))
    }
}
