package net.wault.security

import net.wault.db.WaultDb

private const val KV_BIOMETRIC_ENABLED = "security.biometricEnabled"
private const val KV_SYNC_ENABLED = "security.syncEnabled"
private const val KV_LOCK_ON_BACKGROUND = "security.lockOnBackground"
private const val KV_KDF_PROFILE = "security.kdfProfile"

class SecretStore(
    private val db: WaultDb,
    private val vault: Vault,
    private val onScreenshotSettingChanged: () -> Unit = {}
) {
    fun isInitialized(): Boolean = vault.isInitialized()

    fun verifyMasterPassword(password: String): Boolean =
        vault.unlock(password) is UnlockOutcome.Success

    fun changeMasterPassword(current: String, new: String): Boolean =
        vault.changeMasterPassword(current, new)

    fun autoLockSeconds(): Int = vault.autoLockSeconds()

    fun setAutoLockSeconds(value: Int) = vault.setAutoLockSeconds(value)

    fun isClipboardClearEnabled(): Boolean = vault.isClipboardClearEnabled()

    fun setClipboardClearEnabled(enabled: Boolean) = vault.setClipboardClearEnabled(enabled)

    fun isScreenshotBlockingEnabled(): Boolean = vault.isScreenshotBlockingEnabled()

    fun setScreenshotBlockingEnabled(enabled: Boolean) {
        vault.setScreenshotBlockingEnabled(enabled)
        onScreenshotSettingChanged()
    }

    fun isBiometricEnabled(): Boolean = flag(KV_BIOMETRIC_ENABLED, default = false)

    fun setBiometricEnabled(enabled: Boolean) = setFlag(KV_BIOMETRIC_ENABLED, enabled)

    fun isSyncEnabled(): Boolean = flag(KV_SYNC_ENABLED, default = true)

    fun setSyncEnabled(enabled: Boolean) = setFlag(KV_SYNC_ENABLED, enabled)

    fun isLockOnBackgroundEnabled(): Boolean = flag(KV_LOCK_ON_BACKGROUND, default = true)

    fun setLockOnBackgroundEnabled(enabled: Boolean) = setFlag(KV_LOCK_ON_BACKGROUND, enabled)

    fun kdfProfile(): String = readKv(KV_KDF_PROFILE)?.decodeToString() ?: "balanced"

    fun setKdfProfile(profile: String) {
        db.waultDbQueries.putKv(KV_KDF_PROFILE, profile.encodeToByteArray())
    }

    fun hasDuressPassword(): Boolean = vault.hasDuressPassword()

    fun setDuressPassword(password: String) = vault.setDuressPassword(password)

    fun clearDuressPassword() = vault.clearDuressPassword()

    fun failedAttempts(): Int = vault.failedAttempts()

    fun lockoutUntilMillis(): Long = vault.lockoutUntilMillis()

    private fun flag(key: String, default: Boolean): Boolean =
        readKv(key)?.decodeToString()?.let { it == "1" } ?: default

    private fun setFlag(key: String, enabled: Boolean) {
        db.waultDbQueries.putKv(key, (if (enabled) "1" else "0").encodeToByteArray())
    }

    private fun readKv(key: String): ByteArray? =
        runCatching { db.waultDbQueries.getKv(key).executeAsOneOrNull() }.getOrNull()
}
