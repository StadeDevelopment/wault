package net.wault

import android.app.Application
import android.content.Context

class WaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: Context
            private set
    }
}

internal fun requireAppContext(): Context = WaultApplication.appContext
