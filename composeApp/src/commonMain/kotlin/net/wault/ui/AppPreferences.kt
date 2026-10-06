package net.wault.ui

import net.wault.db.WaultDb
import net.wault.ui.i18n.AppLocale

private const val KV_LOCALE = "ui.locale"
private const val KV_DYNAMIC_COLOR = "ui.dynamicColor"
private const val KV_THEME_MODE = "ui.themeMode"

enum class ThemeMode(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark")
}

class AppPreferences(private val db: WaultDb) {

    fun locale(): AppLocale? =
        read(KV_LOCALE)?.let { stored -> AppLocale.entries.firstOrNull { it.code == stored } }

    fun setLocale(locale: AppLocale) = write(KV_LOCALE, locale.code)

    fun dynamicColor(): Boolean = read(KV_DYNAMIC_COLOR) == "1"

    fun setDynamicColor(enabled: Boolean) = write(KV_DYNAMIC_COLOR, if (enabled) "1" else "0")

    fun themeMode(): ThemeMode =
        read(KV_THEME_MODE)?.let { stored -> ThemeMode.entries.firstOrNull { it.key == stored } }
            ?: ThemeMode.System

    fun setThemeMode(mode: ThemeMode) = write(KV_THEME_MODE, mode.key)

    private fun read(key: String): String? = runCatching {
        db.waultDbQueries.getKv(key).executeAsOneOrNull()?.decodeToString()
    }.getOrNull()

    private fun write(key: String, value: String) {
        runCatching { db.waultDbQueries.putKv(key, value.encodeToByteArray()) }
    }
}
