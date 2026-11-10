package app.godishcover

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import app.godishcover.ui.DishcoverRoot
import app.godishcover.ui.DishcoverTheme

class MainActivity : ComponentActivity() {
    /** Set when an alert was tapped, so the app opens on the feed. */
    private val openFeed = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openFeed.value = intent?.getStringExtra("open") == "feed"
        val prefs = (application as DishcoverApp).prefs
        setContent {
            val settings by prefs.settings.collectAsState()
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            DishcoverTheme(settings) {
                DishcoverRoot(openFeed = openFeed.value, onOpenedFeed = { openFeed.value = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("open") == "feed") openFeed.value = true
    }
}
