package net.wault

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.wault.db.platformDriverFactory
import net.wault.security.createVault
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class StartupTest {

    @Test
    fun theActivityReachesResumedWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun theDatabaseOpensWhereTheVaultSaysItIs() {
        val vault = createVault()
        val driver = platformDriverFactory().create(vault.databasePath())
        try {
            net.wault.db.DatabaseSchema.requireCompatible(driver)
        } finally {
            runCatching { driver.close() }
        }

        val dbFile = File(vault.databasePath())
        assertTrue(dbFile.exists(), "no database at ${dbFile.absolutePath}")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val strayDir = File(context.applicationInfo.dataDir, "databases")
        val strays = strayDir.listFiles()?.filter { it.name.startsWith("wault") }.orEmpty()
        assertTrue(strays.isEmpty(), "database escaped the vault directory: $strays")
    }
}
