package net.wault.security

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun installShutdownLock(onShutdown: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { onShutdown() } })
}

actual fun blockScreenshots(enabled: Boolean) = Unit

actual fun copyToClipboard(value: String, sensitive: Boolean) {
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}

actual fun clearClipboardIfUnchanged(value: String) {
    runCatching {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        val current = clipboard.getData(DataFlavor.stringFlavor) as? String
        if (current == value) clipboard.setContents(StringSelection(""), null)
    }
}
