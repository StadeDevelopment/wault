package net.wault.ui

import net.wault.ui.components.GroupPosition
import net.wault.ui.components.SettingsGroupScope
import net.wault.ui.components.groupPositions
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsGroupTest {

    @Test
    fun `a lone row keeps both ends rounded`() {
        assertEquals(listOf(GroupPosition.Single), groupPositions(1))
    }

    @Test
    fun `two rows are a top and a bottom with no middle`() {
        assertEquals(listOf(GroupPosition.Top, GroupPosition.Bottom), groupPositions(2))
    }

    @Test
    fun `everything between the ends is a middle`() {
        assertEquals(
            listOf(
                GroupPosition.Top,
                GroupPosition.Middle,
                GroupPosition.Middle,
                GroupPosition.Bottom
            ),
            groupPositions(4)
        )
    }

    @Test
    fun `an empty group produces nothing`() {
        assertEquals(emptyList(), groupPositions(0))
    }

    @Test
    fun `a conditional row shifts which row is last`() {
        val withoutBiometrics = SettingsGroupScope().apply {
            row { }
            row { }
        }
        val withBiometrics = SettingsGroupScope().apply {
            row { }
            row { }
            row { }
        }

        assertEquals(
            listOf(GroupPosition.Top, GroupPosition.Bottom),
            groupPositions(withoutBiometrics.size)
        )
        assertEquals(
            listOf(GroupPosition.Top, GroupPosition.Middle, GroupPosition.Bottom),
            groupPositions(withBiometrics.size)
        )
    }
}
