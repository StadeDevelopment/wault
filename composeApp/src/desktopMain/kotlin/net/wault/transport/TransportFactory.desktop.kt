package net.wault.transport

import net.wault.security.vaultDirectory
import net.wault.transport.tor.EmbeddedTorManager
import net.wault.transport.tor.TorBinaryLoader

actual fun platformTransports(
    nodeId: String,
    settings: TransportSettings
): List<TransportPlugin> {
    val appRoot = vaultDirectory()
    val embedded = EmbeddedTorManager(
        appRoot = appRoot,
        virtualPort = LAN_DEFAULT_PORT,
        layoutProvider = { TorBinaryLoader.prepare(appRoot) }
    )
    return listOf(
        LanTransport(nodeId = nodeId),
        TorTransport(
            configProvider = { settings.get(TransportType.TOR).config },
            embedded = embedded
        )
    )
}
