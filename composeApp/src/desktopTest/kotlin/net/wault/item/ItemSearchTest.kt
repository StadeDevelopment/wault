package net.wault.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemSearchTest {

    private fun login(id: String, title: String, username: String = "", uri: String? = null) = VaultItem(
        id = id,
        content = ItemContent.Login(
            title = title,
            username = username,
            uris = uri?.let { listOf(MatchableUri(it)) } ?: emptyList()
        ),
        createdAt = 0,
        updatedAt = 0,
        revision = 1,
        updatedBy = "device-a"
    )

    private val items = listOf(
        login("1", "GitHub", "eren", "https://github.com"),
        login("2", "GitLab", "eren@stade.dev", "https://gitlab.com"),
        login("3", "Bank", "12345", "https://login.bank.co.uk")
    )

    @Test
    fun `an empty query returns everything`() {
        assertEquals(3, ItemSearch.filter(items, "").size)
    }

    @Test
    fun `an exact title match ranks first`() {
        assertEquals("GitHub", ItemSearch.filter(items, "github").first().title)
    }

    @Test
    fun `a prefix matches multiple items`() {
        assertEquals(2, ItemSearch.filter(items, "git").size)
    }

    @Test
    fun `a username matches`() {
        val hits = ItemSearch.filter(items, "stade.dev")
        assertEquals(1, hits.size)
        assertEquals("GitLab", hits.first().title)
    }

    @Test
    fun `domain matching ignores subdomains`() {
        val hits = ItemSearch.matchingUri(items, "https://github.com/settings/profile")
        assertEquals(1, hits.size)
        assertEquals("GitHub", hits.first().title)
    }

    @Test
    fun `a multipart suffix is handled`() {
        assertEquals("bank.co.uk", ItemSearch.registrableDomain("login.bank.co.uk"))
        assertEquals("github.com", ItemSearch.registrableDomain("api.github.com"))
        assertEquals("stade.dev", ItemSearch.registrableDomain("stade.dev"))
    }

    @Test
    fun `an unrelated site matches nothing`() {
        assertTrue(ItemSearch.matchingUri(items, "https://evil-github.com").isEmpty())
    }

    @Test
    fun `host extraction strips scheme port and path`() {
        assertEquals("github.com", ItemSearch.hostOf("https://github.com:443/a/b?c=d"))
    }
}
