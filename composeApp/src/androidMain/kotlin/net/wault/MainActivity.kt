package net.wault

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import net.wault.ui.WaultApp

class MainActivity : FragmentActivity() {

    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentActivity = this

        container = WaultSession.require()

        setContent {
            WaultApp(container)
        }
    }

    override fun onStart() {
        super.onStart()
        if (::container.isInitialized) container.onEnterForeground()
    }

    override fun onStop() {
        super.onStop()
        if (::container.isInitialized) container.onEnterBackground()
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
