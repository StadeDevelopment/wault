package net.wault.ui.screens

import kotlin.test.Test
import kotlin.test.assertTrue

class QrFitTest {

    private fun side(width: Float, height: Float): Float = minOf(width * 0.85f, height * 0.6f)

    @Test
    fun `the code never exceeds the height of a wide window`() {
        val w = 1920f
        val h = 1030f
        val s = side(w, h)
        assertTrue(s <= h, "a maximized window must not push the code off screen (got $s for height $h)")
        assertTrue(s <= w, "the code must also fit the width")
    }

    @Test
    fun `the code still fills a tall phone screen sensibly`() {
        val w = 1080f
        val h = 2400f
        val s = side(w, h)
        assertTrue(s <= w * 0.85f + 0.1f)
        assertTrue(s > w * 0.5f, "on a phone the code should still be large and scannable (got $s)")
    }

    @Test
    fun `a short wide window shrinks the code rather than clipping it`() {
        val s = side(1920f, 400f)
        assertTrue(s <= 400f * 0.6f + 0.1f)
    }
}
