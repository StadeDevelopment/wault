package net.wault

import net.wault.db.platformDriverFactory
import net.wault.security.createVault
import net.wault.transport.platformTransports

object WaultSession {

    @Volatile
    private var container: AppContainer? = null

    @Synchronized
    fun require(): AppContainer {
        val existing = container
        if (existing != null && !existing.isClosed) return existing

        val created = AppContainer(
            driverFactory = platformDriverFactory(),
            vault = createVault(),
            transportFactory = { nodeId, settings -> platformTransports(nodeId, settings) }
        )
        container = created
        return created
    }

    fun peek(): AppContainer? = container?.takeIf { !it.isClosed }

    fun unlocked(): AppContainer? = container?.takeIf { it.isSessionValid() }

    @Synchronized
    fun discard() {
        container = null
    }
}
