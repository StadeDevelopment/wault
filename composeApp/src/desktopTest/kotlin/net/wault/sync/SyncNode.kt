package net.wault.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.wault.crypto.KdfParams
import net.wault.crypto.platformCrypto
import net.wault.crypto.platformPq
import net.wault.db.WaultDb
import net.wault.device.DeviceManager
import net.wault.item.ItemCodec
import net.wault.item.ItemManager
import net.wault.security.FileVault
import net.wault.security.SecretBox
import net.wault.security.Vault
import net.wault.security.VaultRotation
import net.wault.transport.Connection
import net.wault.transport.ConnectionRegistry
import net.wault.transport.TransportSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

internal val FAST_KDF = KdfParams(memoryKib = 1024, iterations = 1, parallelism = 1)

internal class SyncNode(
    val label: String,
    scope: CoroutineScope,
    private val timeSource: AtomicLong
) {
    val crypto = platformCrypto()
    private val pq = platformPq()

    val vault: Vault = FileVault(
        rootDir = Files.createTempDirectory("wault-node-$label").toFile(),
        crypto = crypto,
        kdfParams = FAST_KDF
    )

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val db: WaultDb

    init {
        WaultDb.Schema.create(driver)
        db = WaultDb(driver)
    }

    private val clock: () -> Long = { timeSource.incrementAndGet() }

    val secretBox by lazy { SecretBox(crypto, vault) }
    val codec = ItemCodec(crypto)
    val devices by lazy { DeviceManager(db, crypto, secretBox, "test", label, clock) }
    val items by lazy { ItemManager(db, crypto, vault, codec, { devices.localDevice().id }, clock) }
    val sessions by lazy { DeviceSessions(db, crypto, pq, devices, secretBox, clock) }
    val engine by lazy { SyncEngine(db, crypto, items, clock) }
    val rotation by lazy { VaultRotation(db, vault, codec, secretBox) }
    val pairing = PairingService(crypto)
    val transports = ConnectionRegistry()
    private val settings = TransportSettings(db)

    val service by lazy {
        SyncService(
            transports = transports,
            settings = settings,
            devices = devices,
            sessions = sessions,
            engine = engine,
            vault = vault,
            rotation = rotation,
            scope = scope,
            localDeviceId = { devices.localDevice().id },
            isEnabled = { true },
            clock = clock
        )
    }

    val deviceId: String get() = devices.localDevice().id

    fun close() {
        runCatching { driver.close() }
    }
}

internal class PipeConnection(
    private val inbox: Channel<ByteArray>,
    private val outbox: Channel<ByteArray>,
    override val remoteAddress: String
) : Connection {

    override suspend fun send(frame: ByteArray) {
        outbox.send(frame)
    }

    override suspend fun receive(): ByteArray? = runCatching { inbox.receive() }.getOrNull()

    override suspend fun close() {
        inbox.close()
        outbox.close()
    }
}

internal fun connectedPipes(): Pair<Connection, Connection> {
    val aToB = Channel<ByteArray>(Channel.UNLIMITED)
    val bToA = Channel<ByteArray>(Channel.UNLIMITED)
    return PipeConnection(bToA, aToB, "pipe://b") to PipeConnection(aToB, bToA, "pipe://a")
}

internal fun pair(left: SyncNode, right: SyncNode) {
    val leftOffer = left.pairing.createOffer("vault-1", left.devices.localDevice(), null, null)
    val rightOffer = right.pairing.createOffer("vault-1", right.devices.localDevice(), null, null)

    val leftSession = left.pairing.sessionKey(left.devices.localDevice(), rightOffer, leftOffer.pairingNonce)
    val rightSession = right.pairing.sessionKey(right.devices.localDevice(), leftOffer, rightOffer.pairingNonce)

    val (initiator, responder) =
        if (leftOffer.deviceId <= rightOffer.deviceId) leftOffer to rightOffer else rightOffer to leftOffer
    val transcript = left.pairing.transcript(initiator, responder)

    val leftRoot = left.pairing.rootKeyFor(leftSession, transcript)
    val rightRoot = right.pairing.rootKeyFor(rightSession, transcript)

    left.devices.remember(
        id = rightOffer.deviceId,
        label = rightOffer.label,
        platform = rightOffer.platform,
        signingKey = rightOffer.signingKey,
        agreementKey = rightOffer.agreementKey,
        rootKey = leftRoot,
        isInitiator = initiator.deviceId == leftOffer.deviceId,
        onionAddress = null,
        lanAddress = null,
        keyGeneration = left.vault.keyGeneration()
    )

    right.devices.remember(
        id = leftOffer.deviceId,
        label = leftOffer.label,
        platform = leftOffer.platform,
        signingKey = leftOffer.signingKey,
        agreementKey = leftOffer.agreementKey,
        rootKey = rightRoot,
        isInitiator = initiator.deviceId == rightOffer.deviceId,
        onionAddress = null,
        lanAddress = null,
        keyGeneration = right.vault.keyGeneration()
    )
}
