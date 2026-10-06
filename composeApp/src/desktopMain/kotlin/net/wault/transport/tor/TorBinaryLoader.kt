package net.wault.transport.tor

import java.io.File
import java.util.Locale

internal object TorBinaryLoader {

    fun prepare(appRoot: File): TorLayout {
        val platform = detectPlatformKey()
        val resourcePath = "/tor/$platform"
        val targetRoot = File(appRoot, "tor/$platform")
        if (!targetRoot.exists()) targetRoot.mkdirs()
        val torRunDir = File(targetRoot, "bin")
        if (!torRunDir.exists()) torRunDir.mkdirs()
        val markerFile = File(targetRoot, ".extracted")
        val expectedMarker = bundleVersionMarker()
        val needsExtract = !markerFile.exists() || markerFile.readText().trim() != expectedMarker

        val entries = listResourceEntries(resourcePath)
        if (entries.isEmpty()) {
            error("Embedded Tor binaries missing for platform '$platform' (expected resources at $resourcePath). Did the Gradle task downloadTorBinaries run?")
        }
        if (needsExtract) {
            torRunDir.listFiles()?.forEach { it.deleteRecursively() }
            entries.forEach { entry ->
                val out = File(torRunDir, entry.relativePath)
                out.parentFile?.mkdirs()
                javaClass.getResourceAsStream("$resourcePath/${entry.relativePath}")
                    ?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    ?: error("Resource missing: $resourcePath/${entry.relativePath}")
                if (entry.executable) {
                    runCatching { out.setExecutable(true, false) }
                }
            }
            adhocSignMacBinaries(torRunDir)
            markerFile.writeText(expectedMarker)
        }

        val torExecutable = locateTorExecutable(torRunDir) ?: error("Tor executable not found under $torRunDir")
        runCatching { torExecutable.setExecutable(true, false) }

        val dataDir = File(targetRoot, "data")
        if (!dataDir.exists()) dataDir.mkdirs()
        runCatching {
            val perms = java.nio.file.attribute.PosixFilePermissions.fromString("rwx------")
            java.nio.file.Files.setPosixFilePermissions(dataDir.toPath(), perms)
        }
        val geoip = File(torRunDir, "data/geoip").takeIf { it.exists() }
            ?: File(torRunDir, "geoip").takeIf { it.exists() }
        val geoip6 = File(torRunDir, "data/geoip6").takeIf { it.exists() }
            ?: File(torRunDir, "geoip6").takeIf { it.exists() }
        val obfs4 = locateObfs4Executable(torRunDir)
        runCatching { obfs4?.setExecutable(true, false) }
        return TorLayout(torRunDir, torExecutable, dataDir, geoip, geoip6, obfs4)
    }

    private fun adhocSignMacBinaries(dir: File) {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        if (!os.contains("mac") && !os.contains("darwin")) return
        dir.walkTopDown()
            .filter { it.isFile && (it.name == "tor" || it.name == "lyrebird" || it.extension == "dylib") }
            .forEach { file ->
                runCatching {
                    ProcessBuilder("codesign", "-s", "-", "-f", file.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor()
                }
            }
    }

    private fun locateObfs4Executable(dir: File): File? = listOf(
        File(dir, "tor/pluggable_transports/lyrebird.exe"),
        File(dir, "tor/pluggable_transports/lyrebird"),
        File(dir, "pluggable_transports/lyrebird.exe"),
        File(dir, "pluggable_transports/lyrebird")
    ).firstOrNull { it.isFile }

    fun detectPlatformKey(): String {
        val osRaw = System.getProperty("os.name").lowercase(Locale.ROOT)
        val archRaw = System.getProperty("os.arch").lowercase(Locale.ROOT)
        val os = when {
            osRaw.contains("win") -> "windows"
            osRaw.contains("mac") || osRaw.contains("darwin") -> "macos"
            osRaw.contains("nux") || osRaw.contains("nix") -> "linux"
            else -> error("Unsupported OS for embedded Tor: $osRaw")
        }
        val arch = when {
            archRaw.contains("aarch64") || archRaw.contains("arm64") -> "aarch64"
            archRaw.contains("64") -> "x86_64"
            else -> error("Unsupported arch for embedded Tor: $archRaw")
        }
        if (os == "windows" && arch != "x86_64") error("Embedded Tor only ships windows-x86_64")
        if (os == "linux" && arch != "x86_64") error("Embedded Tor only ships linux-x86_64")
        return "$os-$arch"
    }

    private fun bundleVersionMarker(): String = "v1-${detectPlatformKey()}"

    private fun locateTorExecutable(dir: File): File? = listOf(
        File(dir, "tor/tor.exe"),
        File(dir, "tor/tor"),
        File(dir, "tor.exe"),
        File(dir, "tor")
    ).firstOrNull { it.isFile }

    private data class EntryRef(val relativePath: String, val executable: Boolean)

    private fun listResourceEntries(resourcePath: String): List<EntryRef> {
        val url = javaClass.getResource(resourcePath) ?: return emptyList()
        val collected = mutableListOf<EntryRef>()
        when (url.protocol) {
            "file" -> {
                val rootFile = java.nio.file.Paths.get(url.toURI()).toFile()
                rootFile.walkTopDown().filter { it.isFile }.forEach { file ->
                    val relative = file.relativeTo(rootFile).path.replace(File.separatorChar, '/')
                    collected += EntryRef(relative, isExecutableName(relative))
                }
            }

            "jar" -> {
                val spec = url.toString()
                val bang = spec.indexOf("!/")
                val jarPath = spec.substring("jar:file:".length, bang)
                val inside = spec.substring(bang + 2).trimEnd('/')
                java.util.jar.JarFile(File(java.net.URLDecoder.decode(jarPath, Charsets.UTF_8))).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.isDirectory) continue
                        if (!entry.name.startsWith("$inside/")) continue
                        val relative = entry.name.substring(inside.length + 1)
                        collected += EntryRef(relative, isExecutableName(relative))
                    }
                }
            }
        }
        return collected
    }

    private fun isExecutableName(relative: String): Boolean {
        val name = relative.substringAfterLast('/')
        if (name.equals("tor", ignoreCase = true)) return true
        if (name.equals("tor.exe", ignoreCase = true)) return true
        if (name.equals("lyrebird", ignoreCase = true)) return true
        if (name.equals("lyrebird.exe", ignoreCase = true)) return true
        if (name.endsWith(".so") || name.contains(".so.") || name.endsWith(".dylib")) return true
        return false
    }
}
