package net.wault.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.wault.crypto.CryptoApi
import net.wault.crypto.Envelope
import net.wault.crypto.KdfParams
import net.wault.crypto.platformCrypto
import net.wault.db.WaultDb
import net.wault.device.DeviceManager
import net.wault.item.ItemCodec
import net.wault.item.ItemContent
import net.wault.item.ItemManager
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val FAST_KDF = KdfParams(memoryKib = 1024, iterations = 1, parallelism = 1)

class VaultRotationTest {

    private val crypto: CryptoApi = platformCrypto()
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db: WaultDb
    private val time = AtomicLong(1_000L)

    private val vault = FileVault(
        rootDir = Files.createTempDirectory("wault-rotation").toFile(),
        crypto = crypto,
        kdfParams = FAST_KDF
    )

    init {
        WaultDb.Schema.create(driver)
        db = WaultDb(driver)
    }

    private val codec = ItemCodec(crypto)
    private val secretBox = SecretBox(crypto, vault)
    private val clock: () -> Long = { time.incrementAndGet() }
    private val devices = DeviceManager(db, crypto, secretBox, "test", "node", clock)
    private val items = ItemManager(db, crypto, vault, codec, { devices.localDevice().id }, clock)
    private val rotation = VaultRotation(db, vault, codec, secretBox)

    @AfterTest
    fun tearDown() {
        runCatching { driver.close() }
    }

    private fun setupWithItems(): List<String> {
        vault.setup("master password here")
        items.load()
        return listOf(
            items.create(ItemContent.Login(title = "GitHub", username = "eren", password = "one")).id,
            items.create(ItemContent.Note(title = "Recovery codes", body = "abc-def")).id,
            items.create(ItemContent.Card(title = "Visa", number = "4111111111111111")).id
        )
    }

    @Test
    fun `rotation advances the generation`() {
        setupWithItems()
        assertEquals(0, vault.keyGeneration())

        val report = rotation.rotate()

        assertNotNull(report)
        assertTrue(report.succeeded)
        assertEquals(0, report.previousGeneration)
        assertEquals(1, report.newGeneration)
        assertEquals(1, vault.keyGeneration())
    }

    @Test
    fun `every item is readable after rotation`() {
        setupWithItems()
        val before = items.items.value.map { it.title }.toSet()

        assertTrue(rotation.rotate()!!.succeeded)
        items.load()

        assertEquals(before, items.items.value.map { it.title }.toSet())
        assertTrue(items.undecodable.value.isEmpty())
        val login = items.items.value.first { it.title == "GitHub" }.content
        assertEquals("one", assertIs<ItemContent.Login>(login).password)
    }

    @Test
    fun `every stored payload carries the new generation`() {
        setupWithItems()
        rotation.rotate()

        val generations = db.waultDbQueries.selectAllItemsIncludingDeleted().executeAsList()
            .mapNotNull { Envelope.generationOf(it.payload) }
            .toSet()

        assertEquals(setOf(1), generations)
    }

    @Test
    fun `the data key actually changes`() {
        setupWithItems()
        val before = vault.withDataKey { it.copyOf() }

        rotation.rotate()

        val after = vault.withDataKey { it.copyOf() }
        assertFalse(before.contentEquals(after))
    }

    @Test
    fun `the old key can no longer open a rotated record`() {
        setupWithItems()
        val oldKey = vault.withDataKey { it.copyOf() }
        val row = db.waultDbQueries.selectAllItems().executeAsList().first()

        rotation.rotate()

        val rotatedRow = db.waultDbQueries.selectItem(row.id).executeAsOne()
        val opened = runCatching {
            codec.openItem(oldKey, net.wault.item.SealedRecord(rotatedRow.id, rotatedRow.revision, rotatedRow.payload))
        }.getOrNull()

        assertNull(opened)
    }

    @Test
    fun `device keys survive rotation`() {
        vault.setup("master password here")
        val localBefore = devices.localDevice()

        assertTrue(rotation.rotate()!!.succeeded)

        val peer = crypto.randomBytes(32)
        devices.remember(
            id = "peer-1", label = "Phone", platform = "android",
            signingKey = crypto.generateSigningKeyPair().publicKey,
            agreementKey = crypto.generateAgreementKeyPair().publicKey,
            rootKey = peer, isInitiator = false,
            onionAddress = null, lanAddress = null, keyGeneration = vault.keyGeneration()
        )

        assertTrue(rotation.rotate()!!.succeeded)

        val reopened = devices.peer("peer-1")
        assertNotNull(reopened)
        assertContentEquals(peer, reopened.rootKey)
        assertEquals(localBefore.id, devices.localDevice().id)
    }

    @Test
    fun `rotation is not left pending after success`() {
        setupWithItems()
        rotation.rotate()
        assertFalse(vault.isRotationPending())
    }

    @Test
    fun `an interrupted rotation is resumable`() {
        setupWithItems()

        val handle = vault.beginRotation()
        assertNotNull(handle)
        assertTrue(vault.isRotationPending())

        val mixed = db.waultDbQueries.selectAllItemsIncludingDeleted().executeAsList()
            .mapNotNull { Envelope.generationOf(it.payload) }
            .toSet()
        assertEquals(setOf(0), mixed, "records should still be on the old generation mid-rotation")

        items.load()
        assertEquals(3, items.items.value.size, "old-generation records stay readable mid-rotation")

        val report = rotation.resumeIfPending()
        assertNotNull(report)
        assertTrue(report.succeeded)
        assertFalse(vault.isRotationPending())

        items.load()
        assertEquals(3, items.items.value.size)
        assertTrue(items.undecodable.value.isEmpty())
    }

    @Test
    fun `rotation survives a lock and unlock cycle`() {
        setupWithItems()
        rotation.rotate()
        vault.lock()

        assertIs<UnlockOutcome.Success>(vault.unlock("master password here"))
        assertEquals(1, vault.keyGeneration())

        items.load()
        assertEquals(3, items.items.value.size)
        assertTrue(items.undecodable.value.isEmpty())
    }

    @Test
    fun `the recovery key still works after rotation`() {
        vault.setup("master password here").let { setup ->
            items.load()
            items.create(ItemContent.Note(title = "Kept", body = "x"))

            assertTrue(rotation.rotate()!!.succeeded)
            vault.lock()

            assertIs<UnlockOutcome.Success>(vault.unlockWithRecoveryKey(setup.recoveryKey))
            items.load()
            assertEquals("Kept", items.items.value.single().title)
        }
    }

    @Test
    fun `changing the master password after rotation keeps everything readable`() {
        setupWithItems()
        rotation.rotate()

        assertTrue(vault.changeMasterPassword("master password here", "a different master password"))
        vault.lock()

        assertIs<UnlockOutcome.Success>(vault.unlock("a different master password"))
        items.load()
        assertEquals(3, items.items.value.size)
        assertTrue(items.undecodable.value.isEmpty())
    }

    @Test
    fun `rotating twice keeps records readable`() {
        setupWithItems()
        rotation.rotate()
        rotation.rotate()

        assertEquals(2, vault.keyGeneration())
        items.load()
        assertEquals(3, items.items.value.size)
        assertTrue(items.undecodable.value.isEmpty())
    }
}
