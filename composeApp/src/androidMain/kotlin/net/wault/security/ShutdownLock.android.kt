package net.wault.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import net.wault.MainActivity
import net.wault.requireAppContext

actual fun installShutdownLock(onShutdown: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { onShutdown() } })
}

actual fun blockScreenshots(enabled: Boolean) {
    val activity = MainActivity.currentActivity ?: return
    activity.runOnUiThread {
        if (enabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

actual fun copyToClipboard(value: String, sensitive: Boolean) {
    val manager = requireAppContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("", value)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    manager.setPrimaryClip(clip)
}

actual fun clearClipboardIfUnchanged(value: String) {
    val manager = requireAppContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val current = manager.primaryClip?.getItemAt(0)?.text?.toString()
    if (current == value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
