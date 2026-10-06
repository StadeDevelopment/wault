package net.wault.ui

import net.wault.item.ItemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaultNavigatorTest {

    @Test
    fun `back at the root is refused so the system can close the app`() {
        val nav = WaultNavigator(Screen.Vault)

        assertFalse(nav.canGoBack)
        assertFalse(nav.back())
        assertEquals(Screen.Vault, nav.current)
    }

    @Test
    fun `the lock screen never swallows back`() {
        val nav = WaultNavigator(Screen.Lock)

        assertFalse(nav.canGoBack)
        assertFalse(nav.back())
    }

    @Test
    fun `opening an item can be backed out of`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.push(Screen.ItemDetail("abc"))
        assertTrue(nav.canGoBack)

        assertTrue(nav.back())
        assertEquals(Screen.Vault, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `a secondary tab falls back to the vault`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.selectTab(Screen.Settings)
        assertTrue(nav.canGoBack)

        assertTrue(nav.back())
        assertEquals(Screen.Vault, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `switching tabs repeatedly does not pile up history`() {
        val nav = WaultNavigator(Screen.Vault)

        repeat(10) {
            nav.selectTab(Screen.Generator)
            nav.selectTab(Screen.Health)
            nav.selectTab(Screen.Settings)
        }

        assertEquals(1, nav.depth)
        assertTrue(nav.back())
        assertEquals(Screen.Vault, nav.current)
    }

    @Test
    fun `returning to the vault tab clears the back stack`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.selectTab(Screen.Health)
        nav.selectTab(Screen.Vault)

        assertFalse(nav.canGoBack)
    }

    @Test
    fun `a deep stack unwinds one screen at a time`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.selectTab(Screen.Settings)
        nav.push(Screen.Devices)
        nav.push(Screen.PairDevice(null))

        assertTrue(nav.back())
        assertEquals(Screen.Devices, nav.current)
        assertTrue(nav.back())
        assertEquals(Screen.Settings, nav.current)
        assertTrue(nav.back())
        assertEquals(Screen.Vault, nav.current)
        assertFalse(nav.back())
    }

    @Test
    fun `saving a new item leaves the vault directly behind the detail screen`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.push(Screen.ItemEdit(null, ItemType.Login))
        nav.backTo(Screen.ItemDetail("new"))

        assertEquals(Screen.ItemDetail("new"), nav.current)
        assertEquals(1, nav.depth)
        assertTrue(nav.back())
        assertEquals(Screen.Vault, nav.current)
    }

    @Test
    fun `editing an existing item does not stack a second detail screen`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.push(Screen.ItemDetail("abc"))
        nav.push(Screen.ItemEdit("abc", ItemType.Login))
        nav.backTo(Screen.ItemDetail("abc"))

        assertEquals(Screen.ItemDetail("abc"), nav.current)
        assertEquals(1, nav.depth)
        assertTrue(nav.back())
        assertEquals(Screen.Vault, nav.current)
    }

    @Test
    fun `locking wipes the history so back cannot reveal the vault`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.selectTab(Screen.Settings)
        nav.push(Screen.Devices)
        nav.resetTo(Screen.Lock)

        assertEquals(Screen.Lock, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun `pushing the screen already shown is a no-op`() {
        val nav = WaultNavigator(Screen.Vault)

        nav.push(Screen.ItemDetail("abc"))
        nav.push(Screen.ItemDetail("abc"))

        assertEquals(1, nav.depth)
    }
}
