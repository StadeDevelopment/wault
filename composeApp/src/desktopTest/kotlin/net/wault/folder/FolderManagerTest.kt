package net.wault.folder

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.wault.crypto.KdfParams
import net.wault.crypto.platformCrypto
import net.wault.db.WaultDb
import net.wault.item.ItemCodec
import net.wault.item.ItemContent
import net.wault.item.ItemManager
import net.wault.security.FileVault
import net.wault.security.SecretBox
import net.wault.security.UnlockOutcome
import net.wault.security.VaultRotation
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val FAST_KDF = KdfParams(memoryKib = 1024, iterations = 1, parallelism = 1)
private const val PASSWORD = "master password here"

class FolderManagerTest {

    private val crypto = platformCrypto()
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db: WaultDb
    private val time = AtomicLong(1_000L)

    private val vault = FileVault(
        rootDir = Files.createTempDirectory("wault-folders").toFile(),
        crypto = crypto,
        kdfParams = FAST_KDF
    )

    init {
        WaultDb.Schema.create(driver)
        db = WaultDb(driver)
        vault.setup(PASSWORD)
    }

    private val codec = ItemCodec(crypto)
    private val secretBox = SecretBox(crypto, vault)
    private val clock: () -> Long = { time.incrementAndGet() }
    private val deviceId: () -> String = { "device-a" }
    private val folders = FolderManager(db, crypto, vault, codec, deviceId, clock)
    private val items = ItemManager(db, crypto, vault, codec, deviceId, clock)
    private val rotation = VaultRotation(db, vault, codec, secretBox)

    @AfterTest
    fun tearDown() {
        runCatching { driver.close() }
    }

    @Test
    fun `a folder round-trips through the database`() {
        val created = folders.create("Banking")
        folders.load()

        assertEquals(listOf("Banking"), folders.folders.value.map { it.name })
        assertEquals(created.id, folders.byId(created.id)?.id)
    }

    @Test
    fun `the folder name is encrypted at rest`() {
        folders.create("Offshore Accounts")
        val row = db.waultDbQueries.selectAllFolders().executeAsList().single()
        assertFalse(row.payload.decodeToString().contains("Offshore Accounts"))
    }

    @Test
    fun `an item remembers its folder across a reload`() {
        val folder = folders.create("Work")
        items.load()
        val item = items.create(ItemContent.Login(title = "Intranet"), folderId = folder.id)

        items.load()

        assertEquals(folder.id, items.byId(item.id)?.folderId)
    }

    @Test
    fun `an item remembers being a favourite across a reload`() {
        items.load()
        val item = items.create(ItemContent.Note(title = "Codes", body = "x"), favorite = true)

        items.load()

        assertTrue(items.byId(item.id)?.favorite == true)
    }

    @Test
    fun `moving an item between folders persists`() {
        val work = folders.create("Work")
        val home = folders.create("Home")
        items.load()
        val item = items.create(ItemContent.Login(title = "Router"), folderId = work.id)

        items.moveToFolder(item.id, home.id)
        items.load()

        assertEquals(home.id, items.byId(item.id)?.folderId)
    }

    @Test
    fun `deleting a folder leaves its items in the vault`() {
        val folder = folders.create("Temporary")
        items.load()
        val item = items.create(ItemContent.Login(title = "Kept"), folderId = folder.id)

        folders.delete(folder.id)
        items.clearFolder(folder.id)
        folders.load()
        items.load()

        assertTrue(folders.folders.value.isEmpty())
        assertEquals(1, items.items.value.size)
        assertNull(items.byId(item.id)?.folderId)
    }

    @Test
    fun `renaming a folder persists`() {
        val folder = folders.create("Old name")
        folders.rename(folder.id, "New name")
        folders.load()

        assertEquals(listOf("New name"), folders.folders.value.map { it.name })
    }

    @Test
    fun `folders and item placement survive a key rotation`() {
        val folder = folders.create("Banking")
        items.load()
        val item = items.create(
            ItemContent.Login(title = "Bank", password = "s3cr3t"),
            folderId = folder.id,
            favorite = true
        )

        assertTrue(rotation.rotate()!!.succeeded)
        folders.load()
        items.load()

        assertEquals(listOf("Banking"), folders.folders.value.map { it.name })
        val reloaded = items.byId(item.id)
        assertEquals(folder.id, reloaded?.folderId)
        assertTrue(reloaded?.favorite == true)
        assertEquals("s3cr3t", assertIs<ItemContent.Login>(reloaded!!.content).password)
    }

    @Test
    fun `folders survive a lock and unlock cycle`() {
        folders.create("Banking")
        vault.lock()

        assertIs<UnlockOutcome.Success>(vault.unlock(PASSWORD))
        folders.load()

        assertEquals(listOf("Banking"), folders.folders.value.map { it.name })
    }
}
