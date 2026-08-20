package dev.fps.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import dev.fps.app.player.PlayerScreen
import dev.fps.app.ui.PlayerTheme

class MainActivity : ComponentActivity() {

    /** A video handed to us by another app via ACTION_VIEW, if any. */
    private var incomingUri by mutableStateOf<Uri?>(null)

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        goImmersive()
        incomingUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data

        setContent {
            PlayerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PlayerScreen(
                        activity = this,
                        incomingUri = incomingUri,
                        onIncomingUriHandled = { incomingUri = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) incomingUri = intent.data
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The bars come back after a swipe or any dialog; re-hide once focus returns
        // so a full-screen player does not silently degrade into a windowed one.
        if (hasFocus) goImmersive()
    }

    /**
     * Hides the status and navigation bars, leaving them available on a swipe.
     *
     * The previous build called only [enableEdgeToEdge], which draws *behind* the
     * system bars but does not hide them -- so the clock and the nav pill sat on top
     * of the picture for the whole film.
     */
    private fun goImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
