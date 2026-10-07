package net.wault

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import net.wault.ui.WaultApp

class MainActivity : FragmentActivity() {

    private var container: AppContainer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentActivity = this

        val initial = WaultSession.require()
        container = initial

        setContent {
            WaultApp(
                container = initial,
                rebuild = {
                    WaultSession.discard()
                    runCatching { WaultSession.require() }.getOrNull()?.also { container = it }
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        container?.onEnterForeground()
    }

    override fun onStop() {
        super.onStop()
        container?.onEnterBackground()
    }

    override fun onDestroy() {
        if (currentActivity === this) currentActivity = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        internal var currentActivity: FragmentActivity? = null
    }
}
