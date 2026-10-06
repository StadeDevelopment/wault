package net.wault.security

import net.wault.crypto.CryptoApi
import net.wault.crypto.KdfParams
import net.wault.crypto.base32ToBytes
import net.wault.crypto.hexToBytes
import net.wault.crypto.toBase32
import net.wault.crypto.toHex
import net.wault.crypto.zero
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile

private const val META_VERSION = 1
private const val SALT_LEN = 16
private const val NONCE_LEN = 12
private const val DEK_LEN = 32
private const val KEK_LEN = 32
private const val RECOVERY_LEN = 20
private const val LOCKOUT_THRESHOLD = 5
private val VERIFIER_PLAINTEXT = "WAULT-VERIFY-01".encodeToByteArray()
private val DURESS_PLAINTEXT = "WAULT-DURESS-01".encodeToByteArray()
private val RECOVERY_INFO = "wault.recovery.v1".encodeToByteArray()

@Serializable
private data class SealedBlob(val nonce: String, val ciphertext: String)

@Serializable
private data class KdfConfig(
    val algorithm: String = "argon2id",
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: String
) {
    fun params(): KdfParams = KdfParams(memoryKib, iterations, parallelism)
}

@Serializable
private data class Policy(
    var autoLockSeconds: Int = AutoLock.IMMEDIATE,
    var clipboardClear: Boolean = true,
    var screenshotBlocking: Boolean = true
)

@Serializable
private data class LockState(
    var failedAttempts: Int = 0,
    var lockoutUntilMillis: Long = 0L
)

@Serializable
private data class VaultMeta(
    val version: Int = META_VERSION,
    val vaultId: String,
    val kdf: KdfConfig,
    val verifier: SealedBlob,
    var wrappedDek: SealedBlob,
    var generation: Int = 0,
    var previousWrappedDek: SealedBlob? = null,
    var previousGeneration: Int? = null,
    var recovery: SealedBlob? = null,
    val recoverySalt: String? = null,
    var recoveryKekWrapped: SealedBlob? = null,
    var duressKdf: KdfConfig? = null,
    var duressVerifier: SealedBlob? = null,
    val policy: Policy = Policy(),
    val state: LockState = LockState()
)

class FileVault(
    private val rootDir: File,
    private val crypto: CryptoApi,
    private val kdfParams: KdfParams = KdfParams.Balanced
) : Vault {

    private val metaFile = File(rootDir, "wault.vault")
    private val dbFile = File(rootDir, "wault.db")

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    @Volatile private var dek: ByteArray? = null
    @Volatile private var previousDek: ByteArray? = null
    @Volatile private var kek: ByteArray? = null
    @Volatile private var cached: VaultMeta? = null

    init {
        if (!rootDir.exists()) rootDir.mkdirs()
    }

    override fun isInitialized(): Boolean = metaFile.exists() && metaFile.length() > 0

    override fun isUnlocked(): Boolean = dek != null

    override fun metaPath(): String = metaFile.absolutePath

    override fun databasePath(): String = dbFile.absolutePath

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun vaultId(): String? = readMeta()?.vaultId

    override fun keyGeneration(): Int = (cached ?: readMeta())?.generation ?: 0

    override fun isRotationPending(): Boolean = (cached ?: readMeta())?.previousWrappedDek != null

    override fun setup(masterPassword: String): SetupOutcome = synchronized(lock) {
        require(masterPassword.isNotEmpty()) { "master password" }
        val vaultId = crypto.randomBytes(16).toHex()
        val newDek = crypto.randomBytes(DEK_LEN)
        val recoveryBytes = crypto.randomBytes(RECOVERY_LEN)
        val recoveryKey = formatRecoveryKey(recoveryBytes)

        val salt = crypto.randomBytes(SALT_LEN)
        val derivedKek = crypto.argon2id(masterPassword.encodeToByteArray(), salt, kdfParams, KEK_LEN)
        val recoverySalt = crypto.randomBytes(SALT_LEN)
        val recoveryKek = crypto.hkdf(recoveryBytes, recoverySalt, RECOVERY_INFO, KEK_LEN)

        try {
            val meta = VaultMeta(
                vaultId = vaultId,
                kdf = KdfConfig(
                    memoryKib = kdfParams.memoryKib,
                    iterations = kdfParams.iterations,
                    parallelism = kdfParams.parallelism,
                    salt = salt.toHex()
                ),
                verifier = seal(derivedKek, VERIFIER_PLAINTEXT),
                wrappedDek = seal(derivedKek, newDek),
                generation = 0,
                recovery = seal(recoveryKek, newDek),
                recoverySalt = recoverySalt.toHex(),
                recoveryKekWrapped = seal(derivedKek, recoveryKek)
            )
            writeMeta(meta)
            cached = meta
            dek = newDek.copyOf()
            kek = derivedKek.copyOf()
            return SetupOutcome(vaultId, recoveryKey)
        } finally {
            derivedKek.zero()
            recoveryKek.zero()
            newDek.zero()
            recoveryBytes.zero()
        }
    }

    override fun adoptDataKey(dataKey: ByteArray, masterPassword: String, generation: Int): Unit = synchronized(lock) {
        require(dataKey.size == DEK_LEN) { "data key size" }
        require(masterPassword.isNotEmpty()) { "master password" }
        val existing = readMeta()
        val vaultId = existing?.vaultId ?: crypto.randomBytes(16).toHex()
        val salt = crypto.randomBytes(SALT_LEN)
        val derivedKek = crypto.argon2id(masterPassword.encodeToByteArray(), salt, kdfParams, KEK_LEN)
        try {
            val meta = VaultMeta(
                vaultId = vaultId,
                kdf = KdfConfig(
                    memoryKib = kdfParams.memoryKib,
                    iterations = kdfParams.iterations,
                    parallelism = kdfParams.parallelism,
                    salt = salt.toHex()
                ),
                verifier = seal(derivedKek, VERIFIER_PLAINTEXT),
                wrappedDek = seal(derivedKek, dataKey),
                generation = generation
            )
            writeMeta(meta)
            cached = meta
            previousDek?.zero()
            previousDek = null
            dek?.zero()
            dek = dataKey.copyOf()
            kek?.zero()
            kek = derivedKek.copyOf()
        } finally {
            derivedKek.zero()
        }
    }

    override fun unlock(masterPassword: String): UnlockOutcome = synchronized(lock) {
        val meta = readMeta() ?: return UnlockOutcome.NotInitialized
        val now = nowMillis()
        if (meta.state.lockoutUntilMillis > now) return UnlockOutcome.LockedOut(meta.state.lockoutUntilMillis)

        if (isDuressPassword(masterPassword)) return UnlockOutcome.Duress

        val derivedKek = runCatching {
            crypto.argon2id(
                masterPassword.encodeToByteArray(),
                meta.kdf.salt.hexToBytes(),
                meta.kdf.params(),
                KEK_LEN
            )
        }.getOrElse { return UnlockOutcome.Error(it.message ?: "key derivation failed") }

        var keepKek = false
        try {
            val verified = open(derivedKek, meta.verifier)
                ?.let { crypto.constantTimeEquals(it, VERIFIER_PLAINTEXT) } ?: false
            if (!verified) {
                meta.state.failedAttempts += 1
                meta.state.lockoutUntilMillis = lockoutFor(meta.state.failedAttempts)
                writeMeta(meta)
                cached = meta
                return if (meta.state.lockoutUntilMillis > nowMillis()) {
                    UnlockOutcome.LockedOut(meta.state.lockoutUntilMillis)
                } else {
                    UnlockOutcome.Wrong((LOCKOUT_THRESHOLD - meta.state.failedAttempts).coerceAtLeast(0))
                }
            }

            val unwrapped = open(derivedKek, meta.wrappedDek)
                ?: return UnlockOutcome.Error("data key could not be unwrapped")

            meta.state.failedAttempts = 0
            meta.state.lockoutUntilMillis = 0L
            writeMeta(meta)
            cached = meta
            dek = unwrapped
            previousDek = meta.previousWrappedDek?.let { open(derivedKek, it) }
            kek = derivedKek.copyOf()
            keepKek = true
            return UnlockOutcome.Success
        } finally {
            if (!keepKek) derivedKek.zero()
        }
    }

    override fun unlockWithRecoveryKey(recoveryKey: String): UnlockOutcome = synchronized(lock) {
        val meta = readMeta() ?: return UnlockOutcome.NotInitialized
        val blob = meta.recovery ?: return UnlockOutcome.Error("no recovery key on this vault")
        val saltHex = meta.recoverySalt ?: return UnlockOutcome.Error("no recovery key on this vault")
        val normalized = recoveryKey.uppercase().filter { it.isLetterOrDigit() }
        val bytes = runCatching { normalized.base32ToBytes() }
            .getOrElse { return UnlockOutcome.Error("recovery key is malformed") }

        val recoveryKek = crypto.hkdf(bytes, saltHex.hexToBytes(), RECOVERY_INFO, KEK_LEN)
        try {
            val unwrapped = open(recoveryKek, blob) ?: return UnlockOutcome.Wrong(0)
            dek = unwrapped
            meta.state.failedAttempts = 0
            meta.state.lockoutUntilMillis = 0L
            writeMeta(meta)
            cached = meta
            return UnlockOutcome.Success
        } finally {
            recoveryKek.zero()
            bytes.zero()
        }
    }

    override fun lock() = synchronized(lock) {
        dek?.zero()
        previousDek?.zero()
        kek?.zero()
        dek = null
        previousDek = null
        kek = null
    }

    override fun changeMasterPassword(current: String, new: String): Boolean = synchronized(lock) {
        require(new.isNotEmpty()) { "new master password" }
        val meta = readMeta() ?: return false
        val currentKek = crypto.argon2id(
            current.encodeToByteArray(),
            meta.kdf.salt.hexToBytes(),
            meta.kdf.params(),
            KEK_LEN
        )
        val currentDek: ByteArray
        val carriedPrevious: ByteArray?
        val carriedRecoveryKek: ByteArray?
        try {
            val verified = open(currentKek, meta.verifier)
                ?.let { crypto.constantTimeEquals(it, VERIFIER_PLAINTEXT) } ?: false
            if (!verified) return false
            currentDek = open(currentKek, meta.wrappedDek) ?: return false
            carriedPrevious = meta.previousWrappedDek?.let { open(currentKek, it) }
            carriedRecoveryKek = meta.recoveryKekWrapped?.let { open(currentKek, it) }
        } finally {
            currentKek.zero()
        }

        val newSalt = crypto.randomBytes(SALT_LEN)
        val newKek = crypto.argon2id(new.encodeToByteArray(), newSalt, kdfParams, KEK_LEN)
        try {
            val updated = meta.copy(
                kdf = KdfConfig(
                    memoryKib = kdfParams.memoryKib,
                    iterations = kdfParams.iterations,
                    parallelism = kdfParams.parallelism,
                    salt = newSalt.toHex()
                ),
                verifier = seal(newKek, VERIFIER_PLAINTEXT),
                wrappedDek = seal(newKek, currentDek),
                previousWrappedDek = carriedPrevious?.let { seal(newKek, it) },
                recoveryKekWrapped = carriedRecoveryKek?.let { seal(newKek, it) },
                state = LockState()
            )
            writeMeta(updated)
            cached = updated
            dek?.zero()
            dek = currentDek.copyOf()
            previousDek?.zero()
            previousDek = carriedPrevious?.copyOf()
            kek?.zero()
            kek = newKek.copyOf()
            return true
        } finally {
            newKek.zero()
            currentDek.zero()
            carriedPrevious?.zero()
            carriedRecoveryKek?.zero()
        }
    }

    override fun <T> withDataKey(block: (ByteArray) -> T): T {
        val key = dek ?: throw VaultLockedException()
        return block(key)
    }

    override fun <T> withDataKeyFor(generation: Int, block: (ByteArray) -> T): T? {
        val meta = cached ?: readMeta() ?: return null
        val current = dek ?: throw VaultLockedException()
        if (generation == meta.generation) return block(current)
        if (generation == meta.previousGeneration) {
            val older = previousDek ?: return null
            return block(older)
        }
        return null
    }

    override fun beginRotation(): RotationHandle? = synchronized(lock) {
        val meta = readMeta() ?: return null
        val currentDek = dek ?: throw VaultLockedException()
        val activeKek = kek ?: return null
        if (meta.previousWrappedDek != null) {
            return RotationHandle(meta.previousGeneration ?: (meta.generation - 1), meta.generation)
        }

        val newDek = crypto.randomBytes(DEK_LEN)
        try {
            val updated = meta.copy(
                wrappedDek = seal(activeKek, newDek),
                generation = meta.generation + 1,
                previousWrappedDek = seal(activeKek, currentDek),
                previousGeneration = meta.generation,
                recovery = rewrapRecovery(meta, activeKek, newDek)
            )
            writeMeta(updated)
            cached = updated
            previousDek?.zero()
            previousDek = currentDek.copyOf()
            dek = newDek.copyOf()
            return RotationHandle(meta.generation, updated.generation)
        } finally {
            newDek.zero()
        }
    }

    private fun rewrapRecovery(meta: VaultMeta, activeKek: ByteArray, newDek: ByteArray): SealedBlob? {
        if (meta.recovery == null) return null
        val wrapped = meta.recoveryKekWrapped ?: return null
        val recoveryKek = open(activeKek, wrapped) ?: return null
        try {
            return seal(recoveryKek, newDek)
        } finally {
            recoveryKek.zero()
        }
    }

    override fun adoptRotatedKey(newDataKey: ByteArray, generation: Int): RotationHandle? = synchronized(lock) {
        require(newDataKey.size == DEK_LEN) { "data key size" }
        val meta = readMeta() ?: return null
        val currentDek = dek ?: throw VaultLockedException()
        val activeKek = kek ?: return null
        if (generation <= meta.generation) return null

        val updated = meta.copy(
            wrappedDek = seal(activeKek, newDataKey),
            generation = generation,
            previousWrappedDek = seal(activeKek, currentDek),
            previousGeneration = meta.generation,
            recovery = rewrapRecovery(meta, activeKek, newDataKey)
        )
        writeMeta(updated)
        cached = updated
        previousDek?.zero()
        previousDek = currentDek.copyOf()
        dek = newDataKey.copyOf()
        return RotationHandle(meta.generation, generation)
    }

    override fun completeRotation(): Unit = synchronized(lock) {
        val meta = readMeta() ?: return
        if (meta.previousWrappedDek == null) return
        val updated = meta.copy(previousWrappedDek = null, previousGeneration = null)
        writeMeta(updated)
        cached = updated
        previousDek?.zero()
        previousDek = null
    }

    override fun hasDuressPassword(): Boolean = (cached ?: readMeta())?.duressVerifier != null

    override fun setDuressPassword(password: String): Unit = synchronized(lock) {
        require(password.isNotEmpty()) { "duress password" }
        val meta = readMeta() ?: return
        val salt = crypto.randomBytes(SALT_LEN)
        val duressKek = crypto.argon2id(password.encodeToByteArray(), salt, kdfParams, KEK_LEN)
        try {
            meta.duressKdf = KdfConfig(
                memoryKib = kdfParams.memoryKib,
                iterations = kdfParams.iterations,
                parallelism = kdfParams.parallelism,
                salt = salt.toHex()
            )
            meta.duressVerifier = seal(duressKek, DURESS_PLAINTEXT)
            writeMeta(meta)
            cached = meta
        } finally {
            duressKek.zero()
        }
    }

    override fun isDuressPassword(candidate: String): Boolean {
        val meta = cached ?: readMeta() ?: return false
        val config = meta.duressKdf ?: return false
        val verifier = meta.duressVerifier ?: return false
        val duressKek = runCatching {
            crypto.argon2id(candidate.encodeToByteArray(), config.salt.hexToBytes(), config.params(), KEK_LEN)
        }.getOrElse { return false }
        try {
            return open(duressKek, verifier)?.let { crypto.constantTimeEquals(it, DURESS_PLAINTEXT) } ?: false
        } finally {
            duressKek.zero()
        }
    }

    override fun clearDuressPassword(): Unit = synchronized(lock) {
        val meta = readMeta() ?: return
        meta.duressKdf = null
        meta.duressVerifier = null
        writeMeta(meta)
        cached = meta
    }

    override fun wipe(): Unit = synchronized(lock) {
        lock()
        cached = null
        runCatching { if (dbFile.exists()) secureDelete(dbFile) }
        runCatching { File(rootDir, "wault.db-wal").takeIf { it.exists() }?.let { secureDelete(it) } }
        runCatching { File(rootDir, "wault.db-shm").takeIf { it.exists() }?.let { secureDelete(it) } }
        runCatching { if (metaFile.exists()) secureDelete(metaFile) }
    }

    override fun autoLockSeconds(): Int = (cached ?: readMeta())?.policy?.autoLockSeconds ?: AutoLock.IMMEDIATE

    override fun setAutoLockSeconds(value: Int) = mutatePolicy { it.autoLockSeconds = value }

    override fun isClipboardClearEnabled(): Boolean = (cached ?: readMeta())?.policy?.clipboardClear ?: true

    override fun setClipboardClearEnabled(enabled: Boolean) = mutatePolicy { it.clipboardClear = enabled }

    override fun isScreenshotBlockingEnabled(): Boolean = (cached ?: readMeta())?.policy?.screenshotBlocking ?: true

    override fun setScreenshotBlockingEnabled(enabled: Boolean) = mutatePolicy { it.screenshotBlocking = enabled }

    override fun failedAttempts(): Int = (cached ?: readMeta())?.state?.failedAttempts ?: 0

    override fun lockoutUntilMillis(): Long = (cached ?: readMeta())?.state?.lockoutUntilMillis ?: 0L

    private fun mutatePolicy(change: (Policy) -> Unit): Unit = synchronized(lock) {
        val meta = readMeta() ?: return
        change(meta.policy)
        writeMeta(meta)
        cached = meta
    }

    private fun seal(key: ByteArray, plaintext: ByteArray): SealedBlob {
        val nonce = crypto.randomBytes(NONCE_LEN)
        return SealedBlob(nonce.toHex(), crypto.aeadSeal(key, nonce, plaintext).toHex())
    }

    private fun open(key: ByteArray, blob: SealedBlob): ByteArray? = runCatching {
        crypto.aeadOpen(key, blob.nonce.hexToBytes(), blob.ciphertext.hexToBytes())
    }.getOrNull()

    private fun lockoutFor(attempts: Int): Long {
        val delaySeconds = when {
            attempts < 3 -> 0L
            attempts == 3 -> 5L
            attempts == 4 -> 15L
            attempts == 5 -> 30L
            attempts == 6 -> 60L
            attempts == 7 -> 5L * 60L
            attempts == 8 -> 15L * 60L
            attempts == 9 -> 60L * 60L
            else -> 24L * 60L * 60L
        }
        return if (delaySeconds <= 0L) 0L else nowMillis() + delaySeconds * 1000L
    }

    private fun readMeta(): VaultMeta? {
        if (!metaFile.exists()) return null
        return runCatching {
            json.decodeFromString(VaultMeta.serializer(), metaFile.readText())
        }.getOrNull()
    }

    private fun writeMeta(meta: VaultMeta) {
        val encoded = json.encodeToString(VaultMeta.serializer(), meta)
        val tmp = File(rootDir, metaFile.name + ".tmp")
        RandomAccessFile(tmp, "rw").use { raf ->
            raf.setLength(0)
            raf.write(encoded.encodeToByteArray())
            raf.fd.sync()
        }
        if (!tmp.renameTo(metaFile)) {
            tmp.copyTo(metaFile, overwrite = true)
            tmp.delete()
        }
    }

    private fun formatRecoveryKey(bytes: ByteArray): String =
        bytes.toBase32().chunked(4).joinToString("-")

    private fun secureDelete(file: File) {
        if (!file.exists()) return
        runCatching {
            if (file.length() > 0) {
                RandomAccessFile(file, "rw").use { raf ->
                    val zeros = ByteArray(4096)
                    var remaining = raf.length()
                    raf.seek(0)
                    while (remaining > 0) {
                        val n = minOf(remaining, zeros.size.toLong()).toInt()
                        raf.write(zeros, 0, n)
                        remaining -= n
                    }
                    raf.fd.sync()
                }
            }
        }
        file.delete()
    }
}
