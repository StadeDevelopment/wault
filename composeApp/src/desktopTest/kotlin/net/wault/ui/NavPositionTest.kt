package net.wault.ui

import net.wault.ui.components.HomeDestination
import net.wault.ui.components.homeDestinationOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavPositionTest {

    private fun screenFor(destination: HomeDestination): Screen = when (destination) {
        HomeDestination.Vault -> Screen.Vault
        HomeDestination.Authenticator -> Screen.Authenticator
        HomeDestination.Generator -> Screen.Generator
        HomeDestination.Health -> Screen.Health
        HomeDestination.Settings -> Screen.Settings
    }

    @Test
    fun `every home tab has its own slide position`() {
        val positions = homeDestinationOrder.map { navPosition(screenFor(it)) }
        assertEquals(
            positions.size,
            positions.distinct().size,
            "two tabs sharing a position make the slide direction between them arbitrary"
        )
    }

    @Test
    fun `slide positions follow the order of the tab bar`() {
        val positions = homeDestinationOrder.map { navPosition(screenFor(it)) }
        positions.zipWithNext().forEachIndexed { index, (left, right) ->
            assertTrue(
                left < right,
                "${homeDestinationOrder[index]} sits left of ${homeDestinationOrder[index + 1]} " +
                    "in the bar, so it must slide from the left too ($left >= $right)"
            )
        }
    }

    @Test
    fun `moving left through the tabs is never treated as moving forward`() {
        for (from in homeDestinationOrder.indices) {
            for (to in homeDestinationOrder.indices) {
                if (from == to) continue
                val forward = navPosition(screenFor(homeDestinationOrder[to])) >=
                    navPosition(screenFor(homeDestinationOrder[from]))
                assertEquals(
                    to > from,
                    forward,
                    "${homeDestinationOrder[from]} -> ${homeDestinationOrder[to]} slid the wrong way"
                )
            }
        }
    }

    @Test
    fun `home tabs stay ahead of the lock screen and behind detail screens`() {
        val home = homeDestinationOrder.map { navPosition(screenFor(it)) }
        assertTrue(
            home.all { it > navPosition(Screen.Lock) },
            "entering the vault from the lock screen must read as moving forward"
        )
        assertTrue(
            home.all { it < navPosition(Screen.Devices) },
            "opening a detail screen from any tab must read as moving forward"
        )
    }
}
