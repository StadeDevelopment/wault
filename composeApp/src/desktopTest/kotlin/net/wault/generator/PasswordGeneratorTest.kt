package net.wault.generator

import net.wault.crypto.platformCrypto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordGeneratorTest {

    private val generator = PasswordGenerator(platformCrypto())

    @Test
    fun `the wordlist has no duplicates`() {
        assertEquals(Wordlist.DEFAULT.size, Wordlist.DEFAULT.toSet().size)
    }

    @Test
    fun `the wordlist is a power of two so entropy is exact`() {
        val size = Wordlist.DEFAULT.size
        assertTrue(size > 0 && (size and (size - 1)) == 0, "wordlist size $size is not a power of two")
    }

    @Test
    fun `generated passwords honour the requested length`() {
        repeat(50) {
            val recipe = PasswordRecipe(length = 24)
            assertEquals(24, generator.password(recipe).length)
        }
    }

    @Test
    fun `every enabled class appears`() {
        repeat(100) {
            val password = generator.password(PasswordRecipe(length = 16))
            assertTrue(password.any { it.isLowerCase() }, "no lowercase in $password")
            assertTrue(password.any { it.isUpperCase() }, "no uppercase in $password")
            assertTrue(password.any { it.isDigit() }, "no digit in $password")
            assertTrue(password.any { !it.isLetterOrDigit() }, "no symbol in $password")
        }
    }

    @Test
    fun `disabled classes never appear`() {
        repeat(100) {
            val password = generator.password(
                PasswordRecipe(length = 20, symbols = false, digits = false, minDigits = 0, minSymbols = 0)
            )
            assertTrue(password.all { it.isLetter() }, "unexpected character in $password")
        }
    }

    @Test
    fun `ambiguous characters can be excluded`() {
        repeat(100) {
            val password = generator.password(PasswordRecipe(length = 32, excludeAmbiguous = true))
            assertTrue(password.none { it in "Il1O0o" }, "ambiguous character in $password")
        }
    }

    @Test
    fun `successive passwords differ`() {
        val first = generator.password(PasswordRecipe(length = 32))
        val second = generator.password(PasswordRecipe(length = 32))
        assertTrue(first != second)
    }

    @Test
    fun `passphrases use the requested word count`() {
        val phrase = generator.passphrase(PassphraseRecipe(words = 6, separator = "-"))
        assertEquals(6, phrase.split("-").size)
    }

    @Test
    fun `passphrase entropy matches the wordlist`() {
        val bits = generator.entropyBits(PassphraseRecipe(words = 5))
        assertEquals(40.0, bits, 0.001)
    }

    @Test
    fun `password entropy matches the alphabet`() {
        val recipe = PasswordRecipe(length = 10, uppercase = false, symbols = false, digits = false)
        assertEquals(10 * 4.7004, generator.entropyBits(recipe), 0.01)
    }
}
