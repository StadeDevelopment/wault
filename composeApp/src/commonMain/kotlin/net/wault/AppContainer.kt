package net.wault

import app.cash.sqldelight.db.SqlDriver
import net.wault.crypto.CryptoApi
import net.wault.crypto.PqCrypto
import net.wault.crypto.platformCrypto
import net.wault.crypto.platformPq
import net.wault.db.DatabaseSchema
import net.wault.db.DatabaseSchemaException
import net.wault.db.DriverFactory
import net.wault.db.WaultDb
import net.wault.device.DeviceManager
import net.wault.folder.FolderManager
import net.wault.generator.PasswordGenerator
import net.wault.health.HealthReport
import net.wault.health.VaultHealth
import net.wault.item.ItemCodec
import net.wault.item.ItemManager
import net.wault.importer.ImportPreview
import net.wault.security.AutoLock
import net.wault.security.LockPolicy
import net.wault.security.SecretBox
import net.wault.security.SecretStore
import net.wault.security.Vault
import net.wault.security.VaultRotation
import net.wault.security.defaultDeviceLabel
import net.wault.security.platformName
import net.wault.sync.DeviceSessions
import net.wault.sync.PairingController
import net.wault.sync.PairingProtocol
import net.wault.sync.PairingService
import net.wault.sync.SyncEngine
import net.wault.sync.SyncService
import net.wault.totp.Totp
import net.wault.transport.ConnectionRegistry
import net.wault.transport.TransportPlugin
import net.wault.transport.TransportSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SHUTDOWN_GRACE_MS = 3_000L

class AppContainer(
    driverFactory: DriverFactory,
    val vault: Vault,
    private val clock: () -> Long = { currentTimeMillis() },
    transportFactory: (String, TransportSettings) -> List<TransportPlugin> = { _, _ -> emptyList() }
) {
    val crypto: CryptoApi = platformCrypto()
    val pq: PqCrypto = platformPq()

    val db: WaultDb
    private val driver: SqlDriver

    init {
        val createdDriver = driverFactory.create(vault.databasePath())
        try {
            DatabaseSchema.requireCompatible(createdDriver)
        } catch (error: DatabaseSchemaException) {
            runCatching { createdDriver.close() }
            throw error
        }
        driver = createdDriver
        db = WaultDb(createdDriver)
    }

    val codec = ItemCodec(crypto)
    val secretBox = SecretBox(crypto, vault)
    val secrets = SecretStore(db, vault)
    val preferences = net.wault.ui.AppPreferences(db)
    val devices = DeviceManager(db, crypto, secretBox, platformName(), defaultDeviceLabel(), clock)

    private val deviceId: () -> String = { devices.localDevice().id }

    val items = ItemManager(db, crypto, vault, codec, deviceId, clock)
    val folders = FolderManager(db, crypto, vault, codec, deviceId, clock)
    val generator = PasswordGenerator(crypto)
    val totp = Totp(crypto)
    val pairing = PairingService(crypto)
    val sessions = DeviceSessions(db, crypto, pq, devices, secretBox, clock)
    val engine = SyncEngine(db, crypto, items, clock)
    val rotation = VaultRotation(db, vault, codec, secretBox)

    val transportSettings = TransportSettings(db)
    val transports = ConnectionRegistry()

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + backgroundFailures)

    val sync = SyncService(
        transports = transports,
        settings = transportSettings,
        devices = devices,
        sessions = sessions,
        engine = engine,
        vault = vault,
        rotation = rotation,
        scope = appScope,
        localDeviceId = deviceId,
        isEnabled = { secrets.isSyncEnabled() && vault.isUnlocked() },
        clock = clock
    )

    val pairingProtocol = PairingProtocol(pairing, devices, vault) { items.items.value.size }

    val pairingController = PairingController(
        protocol = pairingProtocol,
        pairing = pairing,
        devices = devices,
        vault = vault,
        transports = transports,
        sync = sync
    )

    private val buildTransports = transportFactory

    val isAppInForeground = MutableStateFlow(true)
    val pendingAutofillQuery = MutableStateFlow<String?>(null)

    val lockRequested = MutableStateFlow(false)

    @Volatile
    private var sessionDeadline: Long = LockPolicy.NO_DEADLINE

    @Volatile
    private var externalFlowDepth: Int = 0

    private var expiryJob: Job? = null

    fun beginExternalFlow() {
        externalFlowDepth += 1
    }

    fun endExternalFlow() {
        if (externalFlowDepth > 0) externalFlowDepth -= 1
    }

    fun lockNow() {
        clearDeadline()
        runCatching { vault.lock() }
        lockRequested.value = true
    }

    fun onEnterBackground() {
        isAppInForeground.value = false
        if (!vault.isUnlocked()) return

        val timeout = autoLockSeconds()
        if (LockPolicy.locksOnBackground(timeout)) {
            if (externalFlowDepth == 0) lockNow()
            return
        }

        sessionDeadline = LockPolicy.deadlineFrom(clock(), timeout)
        scheduleExpiry()
    }

    fun onEnterForeground() {
        isAppInForeground.value = true
        expiryJob?.cancel()
        expiryJob = null
        if (!vault.isUnlocked()) return

        if (LockPolicy.isExpired(clock(), sessionDeadline)) {
            lockNow()
            return
        }
        sessionDeadline = LockPolicy.NO_DEADLINE
    }

    fun isSessionValid(): Boolean {
        if (isClosed) return false
        if (!runCatching { vault.isUnlocked() }.getOrDefault(false)) return false
        if (LockPolicy.isExpired(clock(), sessionDeadline)) {
            lockNow()
            return false
        }
        return true
    }

    fun touchSession() {
        if (!isSessionValid()) return
        if (isAppInForeground.value) return

        val timeout = autoLockSeconds()
        if (LockPolicy.locksOnBackground(timeout)) return

        sessionDeadline = LockPolicy.deadlineFrom(clock(), timeout)
        scheduleExpiry()
    }

    fun sessionDeadlineMillis(): Long = sessionDeadline

    private fun clearDeadline() {
        sessionDeadline = LockPolicy.NO_DEADLINE
        expiryJob?.cancel()
        expiryJob = null
    }

    private fun scheduleExpiry() {
        expiryJob?.cancel()
        val deadline = sessionDeadline
        if (deadline == LockPolicy.NO_DEADLINE) {
            expiryJob = null
            return
        }
        expiryJob = appScope.launch {
            val wait = deadline - clock()
            if (wait > 0) delay(wait)
            if (!isAppInForeground.value && LockPolicy.isExpired(clock(), sessionDeadline)) {
                lockNow()
            }
        }
    }

    private fun autoLockSeconds(): Int =
        runCatching { secrets.autoLockSeconds() }.getOrDefault(AutoLock.IMMEDIATE)

    @Volatile
    var isClosed = false
        private set

    suspend fun onUnlocked() {
        runCatching { rotation.resumeIfPending() }
        devices.load()
        folders.load()
        items.load()
        if (transports.all().isEmpty()) {
            buildTransports(deviceId(), transportSettings).forEach { transports.register(it) }
        }
        if (secrets.isSyncEnabled()) {
            runCatching { sync.start() }
        }
    }

    suspend fun onLocked() {
        runCatching { sync.stop() }
    }

    fun onUnlockedByUser() {
        lockRequested.value = false
        clearDeadline()
    }

    fun importTotpCodes(codes: List<net.wault.totp.ImportedCode>): Int {
        codes.forEach { imported ->
            items.create(
                content = net.wault.item.ItemContent.Authenticator(
                    title = imported.label,
                    secret = imported.secret
                )
            )
        }
        items.load()
        return codes.size
    }

    fun health(): HealthReport = VaultHealth.analyze(items.items.value, clock())

    fun applyImport(preview: ImportPreview): Int {
        val existing = folders.folders.value.associateBy { it.name.lowercase() }
        val resolved = HashMap<String, String>()
        preview.folderNames.forEach { name ->
            val key = name.lowercase()
            resolved[key] = existing[key]?.id ?: folders.create(name).id
        }
        preview.items.forEach { imported ->
            items.create(
                content = imported.content,
                folderId = imported.folderName?.let { resolved[it.lowercase()] },
                favorite = imported.favorite
            )
        }
        folders.load()
        items.load()
        return preview.items.size
    }

    suspend fun revokeDevice(deviceId: String): net.wault.security.RotationReport? {
        devices.revoke(deviceId)
        runCatching { sessions.forget(deviceId) }
        val report = runCatching { rotation.rotate() }.getOrNull()
        items.load()
        if (report != null && report.succeeded) {
            runCatching { sync.syncNow() }
        }
        return report
    }

    suspend fun wipeAllData() {
        isClosed = true
        quiesce()
        runCatching {
            db.waultDbQueries.transaction {
                db.waultDbQueries.wipeItems()
                db.waultDbQueries.wipeFolders()
                db.waultDbQueries.wipeDevices()
                db.waultDbQueries.wipeRatchetSessions()
                db.waultDbQueries.wipeOutbox()
                db.waultDbQueries.wipeProcessedEnvelope()
                db.waultDbQueries.wipeSyncCursors()
                db.waultDbQueries.wipeKeyValue()
                db.waultDbQueries.wipeTransportConfig()
            }
        }
        runCatching { driver.close() }
        runCatching { net.wault.security.clearBiometricUnlock() }
        runCatching { vault.wipe() }
    }

    suspend fun close() {
        if (isClosed) return
        isClosed = true
        quiesce()
        runCatching { vault.lock() }
        runCatching { driver.close() }
    }

    private suspend fun quiesce() {
        runCatching { sync.stop() }
        val job = appScope.coroutineContext[Job]
        job?.cancel()
        runCatching { withTimeoutOrNull(SHUTDOWN_GRACE_MS) { job?.join() } }
    }
}

expect fun currentTimeMillis(): Long
