package net.wault.ui.i18n

import androidx.compose.ui.unit.LayoutDirection

enum class AppLocale(val code: String, val rightToLeft: Boolean = false) {
    English("en"),
    Turkish("tr")
}

expect fun getSystemLocale(): AppLocale

fun localeToStrings(locale: AppLocale): AppStrings = when (locale) {
    AppLocale.English -> EnglishStrings
    AppLocale.Turkish -> TurkishStrings
}

fun localeToLayoutDirection(locale: AppLocale): LayoutDirection =
    if (locale.rightToLeft) LayoutDirection.Rtl else LayoutDirection.Ltr

fun localeDisplayName(locale: AppLocale): String = when (locale) {
    AppLocale.English -> "English"
    AppLocale.Turkish -> "Türkçe"
}
