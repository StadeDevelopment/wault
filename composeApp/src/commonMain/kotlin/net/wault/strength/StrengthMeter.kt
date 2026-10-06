package net.wault.strength

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

enum class StrengthLevel { Critical, Weak, Fair, Strong, Excellent }

data class StrengthReport(
    val entropyBits: Double,
    val level: StrengthLevel,
    val warnings: List<StrengthWarning>
)

enum class StrengthWarning {
    TooShort,
    SingleCharacterClass,
    RepeatedCharacters,
    SequentialCharacters,
    KeyboardPattern,
    CommonPassword,
    ContainsYear
}

object StrengthMeter {

    fun evaluate(password: String): StrengthReport {
        if (password.isEmpty()) {
            return StrengthReport(0.0, StrengthLevel.Critical, listOf(StrengthWarning.TooShort))
        }

        val warnings = ArrayList<StrengthWarning>()
        val lower = password.lowercase()

        if (password.length < 12) warnings.add(StrengthWarning.TooShort)
        if (lower in COMMON_PASSWORDS) warnings.add(StrengthWarning.CommonPassword)
        if (hasRepeat(password)) warnings.add(StrengthWarning.RepeatedCharacters)
        if (hasSequence(lower)) warnings.add(StrengthWarning.SequentialCharacters)
        if (KEYBOARD_ROWS.any { row -> containsRun(lower, row, 4) }) warnings.add(StrengthWarning.KeyboardPattern)
        if (YEAR_PATTERN.containsMatchIn(password)) warnings.add(StrengthWarning.ContainsYear)

        val classes = characterClasses(password)
        if (classes == 1) warnings.add(StrengthWarning.SingleCharacterClass)

        val raw = rawEntropy(password)
        val penalty = warnings.sumOf { penaltyFor(it) }
        val effective = max(0.0, raw - penalty)

        return StrengthReport(effective, levelFor(effective), warnings)
    }

    fun levelFor(entropyBits: Double): StrengthLevel = when {
        entropyBits < 28 -> StrengthLevel.Critical
        entropyBits < 45 -> StrengthLevel.Weak
        entropyBits < 65 -> StrengthLevel.Fair
        entropyBits < 90 -> StrengthLevel.Strong
        else -> StrengthLevel.Excellent
    }

    private fun rawEntropy(password: String): Double {
        val poolSize = poolSize(password)
        if (poolSize <= 1) return 0.0
        val unique = password.toSet().size
        val repetitionFactor = min(1.0, unique.toDouble() / password.length + 0.35)
        return password.length * (ln(poolSize.toDouble()) / ln(2.0)) * repetitionFactor
    }

    private fun poolSize(password: String): Int {
        var size = 0
        if (password.any { it.isLowerCase() }) size += 26
        if (password.any { it.isUpperCase() }) size += 26
        if (password.any { it.isDigit() }) size += 10
        if (password.any { !it.isLetterOrDigit() && !it.isWhitespace() }) size += 32
        if (password.any { it.isWhitespace() }) size += 1
        return size
    }

    private fun characterClasses(password: String): Int {
        var classes = 0
        if (password.any { it.isLowerCase() }) classes++
        if (password.any { it.isUpperCase() }) classes++
        if (password.any { it.isDigit() }) classes++
        if (password.any { !it.isLetterOrDigit() }) classes++
        return classes
    }

    private fun penaltyFor(warning: StrengthWarning): Double = when (warning) {
        StrengthWarning.CommonPassword -> 40.0
        StrengthWarning.KeyboardPattern -> 12.0
        StrengthWarning.SequentialCharacters -> 10.0
        StrengthWarning.RepeatedCharacters -> 8.0
        StrengthWarning.SingleCharacterClass -> 6.0
        StrengthWarning.ContainsYear -> 4.0
        StrengthWarning.TooShort -> 0.0
    }

    private fun hasRepeat(password: String): Boolean {
        var run = 1
        for (i in 1 until password.length) {
            run = if (password[i] == password[i - 1]) run + 1 else 1
            if (run >= 3) return true
        }
        return false
    }

    private fun hasSequence(lower: String): Boolean {
        if (lower.length < 4) return false
        var ascending = 1
        var descending = 1
        for (i in 1 until lower.length) {
            val delta = lower[i].code - lower[i - 1].code
            ascending = if (delta == 1) ascending + 1 else 1
            descending = if (delta == -1) descending + 1 else 1
            if (ascending >= 4 || descending >= 4) return true
        }
        return false
    }

    private fun containsRun(lower: String, row: String, minRun: Int): Boolean {
        if (lower.length < minRun) return false
        for (start in 0..lower.length - minRun) {
            val slice = lower.substring(start, start + minRun)
            if (row.contains(slice) || row.contains(slice.reversed())) return true
        }
        return false
    }

    private val YEAR_PATTERN = Regex("(19|20)\\d{2}")

    private val KEYBOARD_ROWS = listOf(
        "qwertyuiop", "asdfghjkl", "zxcvbnm",
        "qwertzuiop", "azertyuiop", "1234567890"
    )

    private val COMMON_PASSWORDS = setOf(
        "123456", "password", "123456789", "12345678", "12345", "qwerty", "abc123",
        "111111", "123123", "1234567890", "1234567", "qwerty123", "000000", "iloveyou",
        "password1", "admin", "welcome", "monkey", "dragon", "letmein", "football",
        "baseball", "sunshine", "princess", "master", "shadow", "superman", "trustno1",
        "passw0rd", "qwertyuiop", "asdfghjkl", "zxcvbnm", "654321", "666666", "121212",
        "azerty", "qazwsx", "michael", "jordan", "hunter2", "starwars", "whatever"
    )
}
