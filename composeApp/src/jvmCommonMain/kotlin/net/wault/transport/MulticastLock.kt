package net.wault.transport

internal expect fun acquireMulticastLock(): (() -> Unit)?
