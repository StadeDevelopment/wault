package net.wault.strength

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StrengthMeterTest {

    @Test
    fun `an empty password is critical`() {
        val report = StrengthMeter.evaluate("")
        assertEquals(StrengthLevel.Critical, report.level)
        assertEquals(0.0, report.entropyBits)
    }

    @Test
    fun `a known common password is flagged and scored down`() {
        val report = StrengthMeter.evaluate("password")
        assertTrue(StrengthWarning.CommonPassword in report.warnings)
        assertEquals(StrengthLevel.Critical, report.level)
    }

    @Test
    fun `a long random password scores well`() {
        val report = StrengthMeter.evaluate("7Kq#tZr9!mVx2LbW4nQe")
        assertTrue(report.level >= StrengthLevel.Strong, "got ${report.level} at ${report.entropyBits} bits")
    }

    @Test
    fun `sequences are detected`() {
        assertTrue(StrengthWarning.SequentialCharacters in StrengthMeter.evaluate("abcdefgh").warnings)
    }

    @Test
    fun `keyboard runs are detected`() {
        assertTrue(StrengthWarning.KeyboardPattern in StrengthMeter.evaluate("qwertyabc").warnings)
    }

    @Test
    fun `repeats are detected`() {
        assertTrue(StrengthWarning.RepeatedCharacters in StrengthMeter.evaluate("aaabbbccc").warnings)
    }

    @Test
    fun `a year is detected`() {
        assertTrue(StrengthWarning.ContainsYear in StrengthMeter.evaluate("Istanbul1998!").warnings)
    }

    @Test
    fun `a single character class is flagged`() {
        assertTrue(StrengthWarning.SingleCharacterClass in StrengthMeter.evaluate("kjhgfdsapoiuy").warnings)
    }

    @Test
    fun `longer passwords score at least as high`() {
        val short = StrengthMeter.evaluate("7Kq#tZr9").entropyBits
        val long = StrengthMeter.evaluate("7Kq#tZr9!mVx2LbW").entropyBits
        assertTrue(long > short)
    }
}
