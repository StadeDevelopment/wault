package net.wault.ui.theme

import android.app.UiModeManager
import android.os.Build
import net.wault.requireAppContext
import net.wault.ui.ThemeMode

private var applied: ThemeMode? = null

actual fun applySystemNightMode(mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    if (applied == mode) return

    val manager = runCatching {
        requireAppContext().getSystemService(UiModeManager::class.java)
    }.getOrNull() ?: return

    val target = when (mode) {
        ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
    }

    runCatching { manager.setApplicationNightMode(target) }
        .onSuccess { applied = mode }
}
