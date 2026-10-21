package app.astra

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.astra.system.Notifications
import app.astra.ui.AppRoot

class MainActivity : ComponentActivity() {
    /** Tab requested by a notification tap; consumed by AppRoot. */
    val openTab = mutableStateOf<String?>(null)
    /** True when this launch came from tapping one of ASTRA's notifications. */
    var openedFromPush = false
        private set

    private fun handlePush(intent: Intent?) {
        val loop = intent?.getStringExtra(Notifications.EXTRA_LOOP) ?: return
        openedFromPush = true
        (application as App).analytics.log("push_opened", "loop" to loop)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openTab.value = intent?.getStringExtra(Notifications.EXTRA_TAB)
        if (savedInstanceState == null) handlePush(intent)
        setContent { AppRoot(application as App, this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifications.EXTRA_TAB)?.let { openTab.value = it }
        handlePush(intent)
    }
}
