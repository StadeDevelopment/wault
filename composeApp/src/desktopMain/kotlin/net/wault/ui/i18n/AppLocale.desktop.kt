package net.wault.ui.i18n

import java.util.Locale

actual fun getSystemLocale(): AppLocale =
    AppLocale.entries.firstOrNull { it.code == Locale.getDefault().language } ?: AppLocale.English
