package net.wault.transport.tor

import kotlinx.coroutines.flow.StateFlow

interface EmbeddedTorRuntime {
    suspend fun ensureReady(localPort: Int, bridges: TorBridgeConfig = TorBridgeConfig()): TorReady
    suspend fun shutdown()
    suspend fun republishOnion(): Boolean
    fun isAlive(): Boolean
    fun invalidate()
    val statusFlow: StateFlow<TorStatus>
}

data class TorReady(
    val socksHost: String,
    val socksPort: Int,
    val onionHostname: String?,
    val onionVirtualPort: Int,
    val onionLocalPort: Int,
    val onionPublished: Boolean = true
)

sealed interface TorStatus {
    data object Idle : TorStatus
    data class Bootstrapping(val percent: Int, val summary: String) : TorStatus
    data class Ready(val onion: String?, val published: Boolean = true) : TorStatus
    data class Failed(val reason: String) : TorStatus
}
