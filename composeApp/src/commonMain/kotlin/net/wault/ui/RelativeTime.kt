package net.wault.ui

import net.wault.ui.i18n.AppStrings

object RelativeTime {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    fun format(strings: AppStrings, timestampMillis: Long, nowMillis: Long): String {
        if (timestampMillis <= 0L) return strings.neverSynced
        val elapsed = nowMillis - timestampMillis
        return when {
            elapsed < MINUTE -> strings.justNow
            elapsed < HOUR -> strings.minutesAgo((elapsed / MINUTE).toInt())
            elapsed < DAY -> strings.hoursAgo((elapsed / HOUR).toInt())
            else -> strings.daysAgo((elapsed / DAY).toInt())
        }
    }
}
