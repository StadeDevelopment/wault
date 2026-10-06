package net.wault.transport

expect fun platformTransports(
    nodeId: String,
    settings: TransportSettings
): List<TransportPlugin>
