package net.wault.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import net.wault.ui.i18n.AppLocale
import net.wault.ui.i18n.getSystemLocale
import net.wault.ui.theme.applySystemNightMode

object UiSettings {

    private val localeState = mutableStateOf(getSystemLocale())
    private val dynamicColorState = mutableStateOf(false)
    private val themeModeState = mutableStateOf(ThemeMode.System)

    @Volatile
    private var store: AppPreferences? = null

    val locale: State<AppLocale> get() = localeState
    val dynamicColor: State<Boolean> get() = dynamicColorState
    val themeMode: State<ThemeMode> get() = themeModeState

    fun bind(preferences: AppPreferences) {
        store = preferences
        localeState.value = preferences.locale() ?: getSystemLocale()
        dynamicColorState.value = preferences.dynamicColor()
        themeModeState.value = preferences.themeMode()
        applySystemNightMode(themeModeState.value)
    }

    fun setLocale(value: AppLocale) {
        localeState.value = value
        store?.setLocale(value)
    }

    fun setDynamicColor(enabled: Boolean) {
        dynamicColorState.value = enabled
        store?.setDynamicColor(enabled)
    }

    fun setThemeMode(value: ThemeMode) {
        themeModeState.value = value
        store?.setThemeMode(value)
        applySystemNightMode(value)
    }
}
