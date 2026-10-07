package net.wault

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import net.wault.db.platformDriverFactory
import net.wault.security.createVault
import net.wault.security.installShutdownLock
import net.wault.transport.platformTransports
import net.wault.ui.WaultApp
import kotlinx.coroutines.runBlocking

private fun openContainer(vault: net.wault.security.Vault): AppContainer? = runCatching {
    AppContainer(
        driverFactory = platformDriverFactory(),
        vault = vault,
        transportFactory = { nodeId, settings -> platformTransports(nodeId, settings) }
    )
}.getOrNull()

fun main() = application {
    val vault = createVault()
    val container = openContainer(vault)

    installShutdownLock {
        runCatching { vault.lock() }
        runCatching { runBlocking { container?.close() } }
    }

    Window(
        onCloseRequest = {
            runCatching { runBlocking { container?.close() } }
            exitApplication()
        },
        title = "Wault",
        icon = painterResource("wault_icon.png"),
        state = rememberWindowState(size = DpSize(420.dp, 780.dp))
    ) {
        if (container == null) {
            MaterialTheme { Text("Wault could not open its vault directory.") }
        } else {
            WaultApp(container = container, rebuild = { openContainer(vault) })
        }
    }
}
