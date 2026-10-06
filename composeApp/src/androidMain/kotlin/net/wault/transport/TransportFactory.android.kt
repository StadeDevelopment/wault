package net.wault.transport

import net.wault.requireAppContext
import net.wault.transport.tor.AndroidTorBinaryLoader
import net.wault.transport.tor.EmbeddedTorManager
import java.io.File

actual fun platformTransports(
    nodeId: String,
    settings: TransportSettings
): List<TransportPlugin> {
    val context = requireAppContext()
    val appRoot = File(context.filesDir, "vault")
    val embedded = EmbeddedTorManager(
        appRoot = appRoot,
        virtualPort = LAN_DEFAULT_PORT,
        layoutProvider = { AndroidTorBinaryLoader.prepare(context, appRoot) }
    )
    return listOf(
        LanTransport(nodeId = nodeId),
        TorTransport(
            configProvider = { settings.get(TransportType.TOR).config },
            embedded = embedded
        )
    )
}
