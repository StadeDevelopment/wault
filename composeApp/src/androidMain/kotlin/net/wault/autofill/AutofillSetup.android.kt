package net.wault.autofill

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import net.wault.requireAppContext

actual fun isAutofillServiceSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

actual fun isAutofillServiceEnabled(): Boolean {
    if (!isAutofillServiceSupported()) return false
    val manager = runCatching {
        requireAppContext().getSystemService(AutofillManager::class.java)
    }.getOrNull() ?: return false
    return runCatching { manager.hasEnabledAutofillServices() }.getOrDefault(false)
}

actual fun openAutofillServiceSettings() {
    val context = requireAppContext()
    val direct = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = runCatching { context.startActivity(direct) }.isSuccess
    if (launched) return

    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

actual fun isAccessibilityFillSupported(): Boolean = true

actual fun isAccessibilityFillEnabled(): Boolean {
    val context = requireAppContext()
    val expected = ComponentName(context, WaultAccessibilityService::class.java)
    val enabled = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    }.getOrNull() ?: return false

    return enabled.split(':').any { entry ->
        val parsed = ComponentName.unflattenFromString(entry)
        parsed != null &&
            parsed.packageName == expected.packageName &&
            parsed.className == expected.className
    }
}

actual fun openAccessibilityFillSettings() {
    runCatching {
        requireAppContext().startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

actual fun mayNeedRestrictedSettingUnlock(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    if (isAccessibilityFillEnabled()) return false

    val context = requireAppContext()
    val installer = runCatching {
        context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
    }.getOrNull()

    return installer !in TRUSTED_INSTALLERS
}

actual fun openAppInfoSettings() {
    val context = requireAppContext()
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private val TRUSTED_INSTALLERS = setOf(
    "com.android.vending",
    "com.google.android.feedback"
)

actual fun hasOverlayPermission(): Boolean =
    runCatching { Settings.canDrawOverlays(requireAppContext()) }.getOrDefault(false)

actual fun openOverlaySettings() {
    val context = requireAppContext()
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
