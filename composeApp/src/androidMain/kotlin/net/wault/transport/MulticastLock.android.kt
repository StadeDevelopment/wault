package net.wault.transport

import android.content.Context
import android.net.wifi.WifiManager
import net.wault.requireAppContext

internal actual fun acquireMulticastLock(): (() -> Unit)? = runCatching {
    val wifi = requireAppContext().applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager
    val lock = wifi.createMulticastLock("wault-discovery")
    lock.setReferenceCounted(false)
    lock.acquire()
    return@runCatching { runCatching { if (lock.isHeld) lock.release() }; Unit }
}.getOrNull()
