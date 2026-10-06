package net.wault.transport.tor

import android.content.Context
import java.io.File
import java.io.FileOutputStream

internal object AndroidTorBinaryLoader {

    fun prepare(context: Context, appRoot: File): TorLayout {
        val torRoot = File(appRoot, "tor").apply { if (!exists()) mkdirs() }
        val dataDir = File(torRoot, "data").apply {
            if (!exists()) mkdirs()
            runCatching {
                val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
                java.nio.file.Files.setPosixFilePermissions(toPath(), perms)
            }
        }

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val torExecutable = File(nativeDir, "libtor.so").takeIf { it.isFile }
            ?: error("Embedded Tor binary missing: ${nativeDir.absolutePath}/libtor.so")
        runCatching { torExecutable.setExecutable(true, false) }

        val obfs4 = File(nativeDir, "liblyrebird.so").takeIf { it.isFile }
        runCatching { obfs4?.setExecutable(true, false) }

        return TorLayout(
            torDir = torRoot,
            executable = torExecutable,
            dataDir = dataDir,
            geoipFile = copyAsset(context, "tor/geoip", File(torRoot, "geoip")),
            geoip6File = copyAsset(context, "tor/geoip6", File(torRoot, "geoip6")),
            obfs4Executable = obfs4
        )
    }

    private fun copyAsset(context: Context, assetPath: String, dest: File): File? = runCatching {
        context.assets.open(assetPath).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        dest
    }.getOrNull()
}
