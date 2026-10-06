package net.wault.generator

import net.wault.crypto.CryptoApi
import kotlin.math.ln

private const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
private const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val DIGITS = "0123456789"
private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
private const val AMBIGUOUS = "Il1O0o"

data class PasswordRecipe(
    val length: Int = 20,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
    val minDigits: Int = 1,
    val minSymbols: Int = 1
) {
    fun alphabet(): String {
        val builder = StringBuilder()
        if (lowercase) builder.append(LOWERCASE)
        if (uppercase) builder.append(UPPERCASE)
        if (digits) builder.append(DIGITS)
        if (symbols) builder.append(SYMBOLS)
        val full = builder.toString()
        return if (excludeAmbiguous) full.filterNot { it in AMBIGUOUS } else full
    }
}

data class PassphraseRecipe(
    val words: Int = 5,
    val separator: String = "-",
    val capitalize: Boolean = false,
    val includeNumber: Boolean = false
)

class PasswordGenerator(private val crypto: CryptoApi) {

    fun password(recipe: PasswordRecipe): String {
        val alphabet = recipe.alphabet()
        require(alphabet.isNotEmpty()) { "at least one character class must be enabled" }
        require(recipe.length > 0) { "length" }

        val required = ArrayList<Char>()
        if (recipe.lowercase) required.add(pick(pool(LOWERCASE, recipe)))
        if (recipe.uppercase) required.add(pick(pool(UPPERCASE, recipe)))
        repeat(if (recipe.digits) recipe.minDigits else 0) { required.add(pick(pool(DIGITS, recipe))) }
        repeat(if (recipe.symbols) recipe.minSymbols else 0) { required.add(pick(pool(SYMBOLS, recipe))) }

        if (required.size > recipe.length) {
            return shuffle(CharArray(recipe.length) { alphabet[uniformIndex(alphabet.length)] }.concatToString())
        }

        val rest = CharArray(recipe.length - required.size) { alphabet[uniformIndex(alphabet.length)] }
        return shuffle((required.toCharArray() + rest).concatToString())
    }

    fun passphrase(recipe: PassphraseRecipe, wordlist: List<String> = Wordlist.DEFAULT): String {
        require(recipe.words > 0) { "words" }
        require(wordlist.isNotEmpty()) { "wordlist" }
        val chosen = (0 until recipe.words).map { index ->
            val word = wordlist[uniformIndex(wordlist.size)]
            if (recipe.capitalize) word.replaceFirstChar { it.uppercaseChar() } else word
        }.toMutableList()
        if (recipe.includeNumber) {
            val slot = uniformIndex(chosen.size)
            chosen[slot] = chosen[slot] + uniformIndex(10).toString()
        }
        return chosen.joinToString(recipe.separator)
    }

    fun entropyBits(recipe: PasswordRecipe): Double {
        val alphabetSize = recipe.alphabet().length
        if (alphabetSize <= 1) return 0.0
        return recipe.length * log2(alphabetSize.toDouble())
    }

    fun entropyBits(recipe: PassphraseRecipe, wordlist: List<String> = Wordlist.DEFAULT): Double {
        if (wordlist.size <= 1) return 0.0
        val base = recipe.words * log2(wordlist.size.toDouble())
        return base + if (recipe.includeNumber) log2(10.0) else 0.0
    }

    private fun pool(source: String, recipe: PasswordRecipe): String =
        if (recipe.excludeAmbiguous) source.filterNot { it in AMBIGUOUS } else source

    private fun pick(source: String): Char = source[uniformIndex(source.length)]

    private fun shuffle(input: String): String {
        val chars = input.toCharArray()
        for (i in chars.size - 1 downTo 1) {
            val j = uniformIndex(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return chars.concatToString()
    }

    private fun uniformIndex(bound: Int): Int {
        require(bound > 0) { "bound" }
        if (bound == 1) return 0
        val limit = Int.MAX_VALUE - (Int.MAX_VALUE % bound)
        while (true) {
            val bytes = crypto.randomBytes(4)
            val value = ((bytes[0].toInt() and 0x7F) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
            if (value < limit) return value % bound
        }
    }
}

internal fun log2(value: Double): Double = ln(value) / ln(2.0)
