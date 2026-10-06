package net.wault.security

interface Vault {
    fun isInitialized(): Boolean
    fun isUnlocked(): Boolean
    fun setup(masterPassword: String): SetupOutcome
    fun unlock(masterPassword: String): UnlockOutcome
    fun unlockWithRecoveryKey(recoveryKey: String): UnlockOutcome
    fun lock()
    fun changeMasterPassword(current: String, new: String): Boolean
    fun <T> withDataKey(block: (ByteArray) -> T): T
    fun <T> withDataKeyFor(generation: Int, block: (ByteArray) -> T): T?
    fun adoptDataKey(dataKey: ByteArray, masterPassword: String, generation: Int = 0)
    fun keyGeneration(): Int
    fun beginRotation(): RotationHandle?
    fun adoptRotatedKey(newDataKey: ByteArray, generation: Int): RotationHandle?
    fun completeRotation()
    fun isRotationPending(): Boolean
    fun vaultId(): String?
    fun hasDuressPassword(): Boolean
    fun setDuressPassword(password: String)
    fun isDuressPassword(candidate: String): Boolean
    fun clearDuressPassword()
    fun wipe()
    fun metaPath(): String
    fun databasePath(): String
    fun autoLockSeconds(): Int
    fun setAutoLockSeconds(value: Int)
    fun isClipboardClearEnabled(): Boolean
    fun setClipboardClearEnabled(enabled: Boolean)
    fun isScreenshotBlockingEnabled(): Boolean
    fun setScreenshotBlockingEnabled(enabled: Boolean)
    fun failedAttempts(): Int
    fun lockoutUntilMillis(): Long
    fun nowMillis(): Long
}

sealed interface UnlockOutcome {
    data object Success : UnlockOutcome
    data object NotInitialized : UnlockOutcome
    data class Wrong(val remainingBeforeLockout: Int) : UnlockOutcome
    data class LockedOut(val untilMillis: Long) : UnlockOutcome
    data object Duress : UnlockOutcome
    data class Error(val message: String) : UnlockOutcome
}

data class SetupOutcome(val vaultId: String, val recoveryKey: String)

data class RotationHandle(
    val previousGeneration: Int,
    val newGeneration: Int
)

class VaultLockedException : IllegalStateException("vault is locked")

object AutoLock {
    const val IMMEDIATE: Int = 0
    const val NEVER: Int = -1
    val OPTIONS: List<Int> = listOf(IMMEDIATE, 30, 60, 5 * 60, 15 * 60, 60 * 60, NEVER)
}

object ClipboardClear {
    const val SECONDS: Int = 30
}
