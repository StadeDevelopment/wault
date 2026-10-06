package net.wault.security

import net.wault.crypto.KdfParams
import net.wault.crypto.platformCrypto
import java.io.File
import java.net.InetAddress

actual fun createVault(): Vault = FileVault(
    rootDir = vaultDirectory(),
    crypto = platformCrypto(),
    kdfParams = KdfParams.Balanced
)

actual fun platformName(): String {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("win") -> "windows"
        os.contains("mac") -> "macos"
        else -> "linux"
    }
}

actual fun defaultDeviceLabel(): String =
    runCatching { InetAddress.getLocalHost().hostName }.getOrNull()
        ?: System.getProperty("user.name")
        ?: "Desktop"

fun vaultDirectory(): File {
    val home = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        os.contains("win") -> File(System.getenv("APPDATA") ?: home.path, "Wault")
        os.contains("mac") -> File(home, "Library/Application Support/Wault")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "wault")
    }
}
