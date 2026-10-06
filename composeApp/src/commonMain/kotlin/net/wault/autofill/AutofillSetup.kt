package net.wault.autofill

expect fun isAutofillServiceSupported(): Boolean

expect fun isAutofillServiceEnabled(): Boolean

expect fun openAutofillServiceSettings()

expect fun isAccessibilityFillSupported(): Boolean

expect fun isAccessibilityFillEnabled(): Boolean

expect fun openAccessibilityFillSettings()

expect fun hasOverlayPermission(): Boolean

expect fun openOverlaySettings()

expect fun mayNeedRestrictedSettingUnlock(): Boolean

expect fun openAppInfoSettings()
