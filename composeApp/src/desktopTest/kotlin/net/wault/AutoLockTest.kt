package net.wault

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import net.wault.crypto.KdfParams
import net.wault.crypto.platformCrypto
import net.wault.db.DriverFactory
import net.wault.db.WaultDb
import net.wault.security.AutoLock
import net.wault.security.FileVault
import net.wault.security.UnlockOutcome
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val FAST_KDF = KdfParams(memoryKib = 1024, iterations = 1, parallelism = 1)
private const val PASSWORD = "master password here"

class AutoLockTest {

    private val now = AtomicLong(1_000_000L)
    private val opened = mutableListOf<AppContainer>()

    @AfterTest
    fun tearDown() {
        opened.forEach { runCatching { it.vault.lock() } }
        opened.clear()
    }

    private fun newContainer(): AppContainer {
        val vault = FileVault(
            rootDir = Files.createTempDirectory("wault-autolock").toFile(),
            crypto = platformCrypto(),
            kdfParams = FAST_KDF
        )
        vault.setup(PASSWORD)

        val factory = object : DriverFactory {
            override fun create(path: String): SqlDriver =
                JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { WaultDb.Schema.create(it) }
        }
        return AppContainer(factory, vault, clock = { now.get() }).also { opened.add(it) }
    }

    private fun advanceSeconds(seconds: Long) {
        now.addAndGet(seconds * 1000L)
    }

    @Test
    fun `immediate locks as soon as the app leaves the foreground`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(AutoLock.IMMEDIATE)

        assertTrue(container.vault.isUnlocked())
        container.onEnterBackground()

        assertFalse(container.vault.isUnlocked())
        assertTrue(container.lockRequested.value)
    }

    @Test
    fun `never keeps the vault open however long the app is away`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(AutoLock.NEVER)

        container.onEnterBackground()
        advanceSeconds(60L * 60L * 24L)
        container.onEnterForeground()

        assertTrue(container.vault.isUnlocked())
        assertFalse(container.lockRequested.value)
    }

    @Test
    fun `a timeout keeps the vault open when the user comes straight back`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        container.onEnterBackground()
        advanceSeconds(30)
        container.onEnterForeground()

        assertTrue(container.vault.isUnlocked())
        assertFalse(container.lockRequested.value)
    }

    @Test
    fun `a timeout locks once it has elapsed`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        container.onEnterBackground()
        advanceSeconds(61)
        container.onEnterForeground()

        assertFalse(container.vault.isUnlocked())
        assertTrue(container.lockRequested.value)
    }

    @Test
    fun `the timeout boundary locks exactly at the limit`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        container.onEnterBackground()
        advanceSeconds(60)
        container.onEnterForeground()

        assertFalse(container.vault.isUnlocked())
    }

    @Test
    fun `the auto-lock choice survives a lock and unlock cycle`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(5 * 60)

        container.lockNow()
        assertIs<UnlockOutcome.Success>(container.vault.unlock(PASSWORD))

        assertTrue(container.secrets.autoLockSeconds() == 5 * 60)
    }

    @Test
    fun `returning without having left does not lock`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        advanceSeconds(10_000)
        container.onEnterForeground()

        assertTrue(container.vault.isUnlocked())
    }

    @Test
    fun `an app-initiated picker does not trip the immediate lock`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(AutoLock.IMMEDIATE)

        container.beginExternalFlow()
        container.onEnterBackground()

        assertTrue(container.vault.isUnlocked(), "the file picker must not lock the vault")

        container.onEnterForeground()
        container.endExternalFlow()

        assertTrue(container.vault.isUnlocked())
        assertFalse(container.lockRequested.value)
    }

    @Test
    fun `leaving for real still locks once the picker is done`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(AutoLock.IMMEDIATE)

        container.beginExternalFlow()
        container.onEnterBackground()
        container.onEnterForeground()
        container.endExternalFlow()

        container.onEnterBackground()

        assertFalse(container.vault.isUnlocked(), "a genuine background must still lock")
    }

    @Test
    fun `a timeout still applies while a picker is open`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        container.beginExternalFlow()
        container.onEnterBackground()
        advanceSeconds(120)
        container.onEnterForeground()
        container.endExternalFlow()

        assertFalse(container.vault.isUnlocked(), "a long absence must lock even mid-picker")
    }

    @Test
    fun `nested external flows balance out`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(AutoLock.IMMEDIATE)

        container.beginExternalFlow()
        container.beginExternalFlow()
        container.endExternalFlow()
        container.onEnterBackground()
        assertTrue(container.vault.isUnlocked())

        container.onEnterForeground()
        container.endExternalFlow()
        container.onEnterBackground()
        assertFalse(container.vault.isUnlocked())
    }

    @Test
    fun `locking by hand raises the lock request`() {
        val container = newContainer()

        container.lockNow()

        assertFalse(container.vault.isUnlocked())
        assertTrue(container.lockRequested.value)
    }

    @Test
    fun `a backgrounded session stays valid for autofill until the timeout`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(5 * 60)

        container.onEnterBackground()

        advanceSeconds(60)
        assertTrue(container.isSessionValid(), "autofill one minute later must not need a password")

        advanceSeconds(3 * 60)
        assertTrue(container.isSessionValid(), "four minutes in, still inside the five minute window")

        advanceSeconds(60)
        assertFalse(container.isSessionValid(), "five minutes exactly is the limit")
    }

    @Test
    fun `an expired session is refused even if the app never came back`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        container.onEnterBackground()
        advanceSeconds(61)

        assertFalse(
            container.isSessionValid(),
            "expiry must be decided from the clock, not from the app being resumed"
        )
        assertFalse(container.vault.isUnlocked(), "checking an expired session must zero the key")
    }

    @Test
    fun `never keeps the session valid for autofill indefinitely`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(AutoLock.NEVER)

        container.onEnterBackground()
        advanceSeconds(60L * 60L * 24L * 7L)

        assertTrue(container.isSessionValid())
    }

    @Test
    fun `immediate leaves no session for autofill to use`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(AutoLock.IMMEDIATE)

        container.onEnterBackground()

        assertFalse(container.isSessionValid())
    }

    @Test
    fun `filling a credential extends the session`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(5 * 60)

        container.onEnterBackground()
        advanceSeconds(4 * 60)

        container.touchSession()
        advanceSeconds(4 * 60)

        assertTrue(
            container.isSessionValid(),
            "using autofill is activity, so it should push the deadline out"
        )
    }

    @Test
    fun `touching an already expired session does not resurrect it`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        container.onEnterBackground()
        advanceSeconds(61)

        container.touchSession()

        assertFalse(container.isSessionValid())
        assertFalse(container.vault.isUnlocked())
    }

    @Test
    fun `returning to the foreground clears the deadline`() {
        val container = newContainer()
        container.secrets.setAutoLockSeconds(60)

        container.onEnterBackground()
        advanceSeconds(30)
        container.onEnterForeground()

        advanceSeconds(60L * 60L)
        assertTrue(
            container.isSessionValid(),
            "an open app must not expire; the countdown only runs while it is away"
        )
    }

    @Test
    fun `a session is not valid before anything is unlocked`() {
        val container = newContainer()
        container.lockNow()

        assertFalse(container.isSessionValid())
    }
}
