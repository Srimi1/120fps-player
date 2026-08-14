package dev.fps.app.player

import android.app.Activity
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.util.UnstableApi
import dev.fps.app.hud.StatsHud
import dev.fps.app.hud.SystemStats
import dev.fps.app.hud.SystemStatsCollector
import dev.fps.playback.DisplayModeController
import dev.fps.playback.PlaybackStatsCollector
import dev.fps.playback.PlayerEngine

private const val TARGET_REFRESH_HZ = 120f

@UnstableApi
@Composable
fun PlayerScreen(activity: Activity, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val playerEngine = remember { PlayerEngine(context) }
    val displayModeController = remember { DisplayModeController(activity) }
    val statsCollector = remember { PlaybackStatsCollector() }
    val systemStatsFlow = remember { SystemStatsCollector(context).stats() }

    DisposableEffect(Unit) {
        statsCollector.attachTo(playerEngine.player)
        displayModeController.start()
        onDispose {
            displayModeController.stop()
            playerEngine.release()
        }
    }

    val playbackStats by statsCollector.stats.collectAsState()
    val grantedHz by displayModeController.grantedRefreshHz.collectAsState()
    val systemStats by systemStatsFlow.collectAsState(initial = SystemStats())

    var currentUri by remember { mutableStateOf<Uri?>(null) }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            currentUri = uri
            playerEngine.open(uri)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val surfaceView = SurfaceView(ctx)
                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        playerEngine.attachTo(surfaceView)
                        displayModeController.requestFrameRate(holder.surface, TARGET_REFRESH_HZ)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                })
                surfaceView
            },
        )

        StatsHud(
            playbackStats = playbackStats,
            systemStats = systemStats,
            grantedRefreshHz = grantedHz,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        if (currentUri == null) {
            Button(
                onClick = { pickFileLauncher.launch(arrayOf("video/*")) },
                modifier = Modifier.align(Alignment.Center),
            ) {
                Text("Open video file")
            }
        }
    }
}
