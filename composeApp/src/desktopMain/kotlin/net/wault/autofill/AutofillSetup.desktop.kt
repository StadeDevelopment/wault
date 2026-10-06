package net.wault.autofill

actual fun isAutofillServiceSupported(): Boolean = false

actual fun isAutofillServiceEnabled(): Boolean = false

actual fun openAutofillServiceSettings() = Unit

actual fun isAccessibilityFillSupported(): Boolean = false

actual fun isAccessibilityFillEnabled(): Boolean = false

actual fun openAccessibilityFillSettings() = Unit

actual fun hasOverlayPermission(): Boolean = false

actual fun openOverlaySettings() = Unit

actual fun mayNeedRestrictedSettingUnlock(): Boolean = false

actual fun openAppInfoSettings() = Unit
