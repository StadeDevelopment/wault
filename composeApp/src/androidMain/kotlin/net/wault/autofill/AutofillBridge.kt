package net.wault.autofill

import net.wault.AppContainer
import net.wault.WaultSession

object AutofillBridge {

    fun unlocked(): AppContainer? = WaultSession.unlocked()
}
