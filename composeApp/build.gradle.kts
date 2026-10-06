import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties
import java.net.URI
import java.security.MessageDigest

val appVersion = "26.0.0"
val appVersionCode = 2

val appVersionSource = file("src/commonMain/kotlin/net/wault/AppVersion.kt")
val declaredAppVersion = Regex("""APP_VERSION\s*=\s*"([^"]+)"""")
    .find(appVersionSource.readText())?.groupValues?.get(1)
check(declaredAppVersion == appVersion) {
    "APP_VERSION in ${appVersionSource.name} is $declaredAppVersion but the build declares $appVersion. Update both."
}

val macAppVersion = appVersion.split(".").let { parts ->
    val declaredMajor = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val major = if (declaredMajor < 1) declaredMajor + 1 else declaredMajor
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    "$major.$minor.$patch"
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(17)

    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    androidTarget {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions { jvmTarget = "17" }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.network)
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)
            }
        }
        val jvmCommonMain by getting {
            dependencies {
                implementation(libs.bouncycastle)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.zxing.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.biometric)
                implementation(libs.androidx.fragment)
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.sqldelight.android.driver)
                implementation(libs.androidx.camera.core)
                implementation(libs.androidx.camera.camera2)
                implementation(libs.androidx.camera.lifecycle)
                implementation(libs.androidx.camera.view)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.sqldelight.sqlite.driver)
                runtimeOnly(libs.slf4j.nop)
                implementation("net.java.dev.jna:jna:5.14.0")
                implementation("net.java.dev.jna:jna-platform:5.14.0")
            }
            resources.srcDir(layout.buildDirectory.dir("torBinaries"))
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.ext.junit)
            }
        }
    }
}

android {
    namespace = "net.wault"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.wault"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProps = Properties().also { props ->
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { props.load(it) }
    }

    signingConfigs {
        create("release") {
            val ksPath = localProps.getProperty("keystore.path")
            if (ksPath != null) {
                storeFile = rootProject.file(ksPath)
                storePassword = localProps.getProperty("keystore.password") ?: ""
                keyAlias = localProps.getProperty("keystore.alias") ?: ""
                keyPassword = localProps.getProperty("keystore.keyPassword") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ksPath = localProps.getProperty("keystore.path")
            signingConfig = if (ksPath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
    sourceSets["main"].jniLibs.srcDir(layout.buildDirectory.dir("torAndroid/jniLibs"))
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("torAndroid/assets"))
}

compose.desktop {
    application {
        mainClass = "net.wault.MainKt"
        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { localProps.load(it) }
        javaHome = localProps.getProperty("java.home") ?: System.getProperty("java.home")
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Rpm, TargetFormat.Exe, TargetFormat.Dmg)
            modules(
                "jdk.unsupported",
                "java.sql",
                "java.naming",
                "java.net.http",
                "java.management",
                "java.security.jgss",
                "jdk.crypto.cryptoki",
                "jdk.security.auth",
                "java.desktop"
            )
            packageName = "Wault"
            packageVersion = appVersion
            windows {
                menuGroup = "Wault"
                upgradeUuid = "7f3c1a92-6d4b-4e85-9c21-0b7a5e8d43f6"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("icons/wault.ico"))
            }
            linux {
                packageName = "wault"
                menuGroup = "Utility"
                appCategory = "Utility"
                debMaintainer = "Stade Development <contact@stade.dev>"
                appRelease = "1"
                shortcut = true
                iconFile.set(project.file("icons/wault.png"))
            }
            macOS {
                bundleID = "net.wault.app"
                packageVersion = macAppVersion
                packageBuildVersion = appVersionCode.toString()
                iconFile.set(project.file("icons/wault.icns"))
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    inputs.dir(layout.projectDirectory.dir("src/androidMain/res/drawable"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("launcherIconDrawables")
}

sqldelight {
    databases {
        create("WaultDb") {
            packageName.set("net.wault.db")
            deriveSchemaFromMigrations.set(false)
        }
    }
}

val torBundleVersion: String = providers.gradleProperty("tor.bundle.version").getOrElse("15.0.23")
val torDistBase = "https://archive.torproject.org/tor-package-archive/torbrowser"

data class TorPlatform(val key: String, val triple: String, val shaProp: String)

val torPlatforms = listOf(
    TorPlatform("windows-x86_64", "windows-x86_64", "tor.sha256.windows.x86_64"),
    TorPlatform("linux-x86_64", "linux-x86_64", "tor.sha256.linux.x86_64"),
    TorPlatform("macos-x86_64", "macos-x86_64", "tor.sha256.macos.x86_64"),
    TorPlatform("macos-aarch64", "macos-aarch64", "tor.sha256.macos.aarch64")
)

val torBinariesRoot = layout.buildDirectory.dir("torBinaries/tor")

val downloadTorBinaries by tasks.registering {
    group = "tor"
    description = "Downloads and extracts the Tor Expert Bundle for each desktop platform."
    val outRoot = torBinariesRoot
    doNotTrackState("Tor binaries are large external downloads managed via version markers.")
    inputs.property("torVersion", torBundleVersion)
    outputs.upToDateWhen {
        val rootDir = outRoot.get().asFile
        torPlatforms.all { plat ->
            rootDir.resolve("${plat.key}/.ok-$torBundleVersion").exists()
        }
    }
    doLast {
        val rootDir = outRoot.get().asFile
        rootDir.mkdirs()
        torPlatforms.forEach { plat ->
            val targetDir = rootDir.resolve(plat.key)
            val marker = targetDir.resolve(".ok-$torBundleVersion")
            if (marker.exists()) return@forEach
            targetDir.deleteRecursively()
            targetDir.mkdirs()
            val fname = "tor-expert-bundle-${plat.triple}-$torBundleVersion.tar.gz"
            val urlStr = "$torDistBase/$torBundleVersion/$fname"
            logger.lifecycle("Downloading $urlStr")
            val tmp = File.createTempFile("tor-bundle-${plat.key}-", ".tar.gz")
            try {
                URI(urlStr).toURL().openStream().use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                val actualSha = MessageDigest.getInstance("SHA-256")
                    .digest(tmp.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                val expected = providers.gradleProperty(plat.shaProp).orNull?.trim()?.takeIf { it.isNotEmpty() }
                if (expected == null) {
                    logger.warn("[tor] ${plat.key} SHA-256 NOT pinned. Actual=$actualSha  -> set ${plat.shaProp} in gradle.properties")
                } else if (!expected.equals(actualSha, ignoreCase = true)) {
                    throw GradleException("Tor binary ${plat.key} hash mismatch. expected=$expected actual=$actualSha")
                }
                copy {
                    from(tarTree(resources.gzip(tmp)))
                    into(targetDir)
                }
                marker.writeText(actualSha)
            } finally {
                tmp.delete()
            }
        }
    }
}

tasks.matching { it.name == "desktopProcessResources" || it.name == "jvmProcessResources" }.configureEach {
    dependsOn(downloadTorBinaries)
}

data class AndroidTorAbi(val bundleTriple: String, val abi: String, val shaProp: String)

val androidTorAbis = listOf(
    AndroidTorAbi("android-aarch64", "arm64-v8a", "tor.sha256.android.aarch64"),
    AndroidTorAbi("android-armv7", "armeabi-v7a", "tor.sha256.android.armv7"),
    AndroidTorAbi("android-x86_64", "x86_64", "tor.sha256.android.x86_64"),
    AndroidTorAbi("android-x86", "x86", "tor.sha256.android.x86")
)

val androidTorRoot = layout.buildDirectory.dir("torAndroid")

val downloadAndroidTorBinaries by tasks.registering {
    group = "tor"
    description = "Downloads Tor Expert Bundle for Android ABIs and stages jniLibs + assets."
    val outRoot = androidTorRoot
    doNotTrackState("Tor binaries are large external downloads managed via version markers.")
    inputs.property("torVersion", torBundleVersion)
    outputs.upToDateWhen {
        val rootDir = outRoot.get().asFile
        val assetsOk = rootDir.resolve("assets/tor/geoip").exists() && rootDir.resolve("assets/tor/geoip6").exists()
        assetsOk && androidTorAbis.all { abi ->
            rootDir.resolve("jniLibs/${abi.abi}/libtor.so").exists() &&
                rootDir.resolve("jniLibs/${abi.abi}/.ok-$torBundleVersion").exists()
        }
    }
    doLast {
        val rootDir = outRoot.get().asFile
        val jniLibsDir = rootDir.resolve("jniLibs")
        val assetsDir = rootDir.resolve("assets/tor")
        jniLibsDir.mkdirs()
        assetsDir.mkdirs()
        androidTorAbis.forEach { abi ->
            val jniDir = jniLibsDir.resolve(abi.abi)
            val marker = jniDir.resolve(".ok-$torBundleVersion")
            if (marker.exists() && jniDir.resolve("libtor.so").exists()) return@forEach
            jniDir.deleteRecursively()
            jniDir.mkdirs()
            val fname = "tor-expert-bundle-${abi.bundleTriple}-$torBundleVersion.tar.gz"
            val urlStr = "$torDistBase/$torBundleVersion/$fname"
            logger.lifecycle("Downloading $urlStr")
            val tmp = File.createTempFile("tor-android-${abi.abi}-", ".tar.gz")
            try {
                URI(urlStr).toURL().openStream().use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                }
                val actualSha = MessageDigest.getInstance("SHA-256")
                    .digest(tmp.readBytes())
                    .joinToString("") { byte -> "%02x".format(byte) }
                val expected = providers.gradleProperty(abi.shaProp).orNull?.trim()?.takeIf { it.isNotEmpty() }
                if (expected == null) {
                    logger.warn("[tor-android] ${abi.abi} SHA-256 NOT pinned. Actual=$actualSha")
                } else if (!expected.equals(actualSha, ignoreCase = true)) {
                    throw GradleException("Android Tor binary ${abi.abi} hash mismatch. expected=$expected actual=$actualSha")
                }
                val extractDir = File.createTempFile("tor-android-${abi.abi}-extract", "").apply {
                    delete(); mkdirs()
                }
                try {
                    copy {
                        from(tarTree(resources.gzip(tmp)))
                        into(extractDir)
                    }
                    val torBin = sequenceOf(
                        extractDir.resolve("tor/tor"),
                        extractDir.resolve("tor/libtor.so"),
                        extractDir.resolve("tor")
                    ).firstOrNull { it.isFile }
                        ?: throw GradleException("tor binary not found in bundle for ${abi.abi}")
                    torBin.copyTo(jniDir.resolve("libtor.so"), overwrite = true)
                    val lyrebirdBin = sequenceOf(
                        extractDir.resolve("tor/pluggable_transports/lyrebird"),
                        extractDir.resolve("pluggable_transports/lyrebird")
                    ).firstOrNull { it.isFile }
                    if (lyrebirdBin != null) {
                        lyrebirdBin.copyTo(jniDir.resolve("liblyrebird.so"), overwrite = true)
                    }
                    listOf("geoip", "geoip6").forEach { gn ->
                        val candidate = sequenceOf(
                            extractDir.resolve("data/$gn"),
                            extractDir.resolve("tor/$gn"),
                            extractDir.resolve(gn)
                        ).firstOrNull { it.isFile }
                        if (candidate != null) candidate.copyTo(assetsDir.resolve(gn), overwrite = true)
                    }
                } finally {
                    extractDir.deleteRecursively()
                }
                marker.writeText(actualSha)
            } finally {
                tmp.delete()
            }
        }
    }
}

tasks.matching {
    val n = it.name
    n.startsWith("merge") && (n.endsWith("JniLibFolders") || n.endsWith("Assets") || n.endsWith("Resources")) ||
        n == "preBuild" || n == "generateDebugAssets" || n == "generateReleaseAssets"
}.configureEach {
    dependsOn(downloadAndroidTorBinaries)
}
