package net.wault.security

import net.wault.crypto.KdfParams
import net.wault.crypto.platformCrypto
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val FAST_KDF = KdfParams(memoryKib = 1024, iterations = 1, parallelism = 1)

class FileVaultTest {

    private fun newVault() = FileVault(
        rootDir = Files.createTempDirectory("wault-test").toFile(),
        crypto = platformCrypto(),
        kdfParams = FAST_KDF
    )

    @Test
    fun `setup then unlock with the same password succeeds`() {
        val vault = newVault()
        assertFalse(vault.isInitialized())

        val outcome = vault.setup("correct horse battery staple")
        assertTrue(vault.isInitialized())
        assertTrue(vault.isUnlocked())
        assertTrue(outcome.recoveryKey.isNotBlank())

        val dataKey = vault.withDataKey { it.copyOf() }
        vault.lock()
        assertFalse(vault.isUnlocked())

        assertIs<UnlockOutcome.Success>(vault.unlock("correct horse battery staple"))
        assertContentEquals(dataKey, vault.withDataKey { it.copyOf() })
    }

    @Test
    fun `wrong password never yields the data key`() {
        val vault = newVault()
        vault.setup("correct horse battery staple")
        vault.lock()

        val outcome = vault.unlock("wrong password entirely")
        assertIs<UnlockOutcome.Wrong>(outcome)
        assertFalse(vault.isUnlocked())
        assertFailsWith<VaultLockedException> { vault.withDataKey { it } }
    }

    @Test
    fun `repeated wrong passwords escalate into a lockout`() {
        val vault = newVault()
        vault.setup("correct horse battery staple")
        vault.lock()

        repeat(3) { vault.unlock("nope") }

        val outcome = vault.unlock("nope")
        assertTrue(outcome is UnlockOutcome.LockedOut || outcome is UnlockOutcome.Wrong)
        assertTrue(vault.lockoutUntilMillis() > vault.nowMillis())
    }

    @Test
    fun `recovery key unlocks the same data key`() {
        val vault = newVault()
        val setup = vault.setup("correct horse battery staple")
        val dataKey = vault.withDataKey { it.copyOf() }
        vault.lock()

        assertIs<UnlockOutcome.Success>(vault.unlockWithRecoveryKey(setup.recoveryKey))
        assertContentEquals(dataKey, vault.withDataKey { it.copyOf() })
    }

    @Test
    fun `changing the master password preserves the data key`() {
        val vault = newVault()
        vault.setup("first password here")
        val dataKey = vault.withDataKey { it.copyOf() }

        assertTrue(vault.changeMasterPassword("first password here", "second password here"))
        vault.lock()

        assertIs<UnlockOutcome.Wrong>(vault.unlock("first password here"))
        assertIs<UnlockOutcome.Success>(vault.unlock("second password here"))
        assertContentEquals(dataKey, vault.withDataKey { it.copyOf() })
    }

    @Test
    fun `duress password is recognised without unlocking`() {
        val vault = newVault()
        vault.setup("real password here")
        vault.setDuressPassword("duress password here")
        vault.lock()

        assertTrue(vault.hasDuressPassword())
        assertEquals(UnlockOutcome.Duress, vault.unlock("duress password here"))
        assertFalse(vault.isUnlocked())
    }

    @Test
    fun `policy survives a lock cycle`() {
        val vault = newVault()
        vault.setup("real password here")
        vault.setAutoLockSeconds(300)
        vault.setClipboardClearEnabled(false)
        vault.lock()

        assertIs<UnlockOutcome.Success>(vault.unlock("real password here"))
        assertEquals(300, vault.autoLockSeconds())
        assertFalse(vault.isClipboardClearEnabled())
    }

    @Test
    fun `wipe removes the meta file`() {
        val vault = newVault()
        vault.setup("real password here")
        vault.wipe()

        assertFalse(vault.isInitialized())
        assertFalse(vault.isUnlocked())
    }
}
