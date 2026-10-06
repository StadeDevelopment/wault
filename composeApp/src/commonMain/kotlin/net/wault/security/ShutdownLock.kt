package net.wault.security

expect fun installShutdownLock(onShutdown: () -> Unit)

expect fun blockScreenshots(enabled: Boolean)

expect fun copyToClipboard(value: String, sensitive: Boolean)

expect fun clearClipboardIfUnchanged(value: String)
