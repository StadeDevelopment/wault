package net.wault.security

import android.os.Build
import net.wault.crypto.KdfParams
import net.wault.crypto.platformCrypto
import net.wault.requireAppContext
import java.io.File

actual fun createVault(): Vault = FileVault(
    rootDir = File(requireAppContext().filesDir, "vault"),
    crypto = platformCrypto(),
    kdfParams = KdfParams.Interactive
)

actual fun platformName(): String = "android"

actual fun defaultDeviceLabel(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
