package dev.fps.app.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.fps.playback.PlaybackStats

@Composable
fun StatsHud(
    playbackStats: PlaybackStats,
    systemStats: SystemStats,
    grantedRefreshHz: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(8.dp),
    ) {
        val style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
        Text("render: %.0f fps".format(playbackStats.renderedFps), style = style)
        Text("dropped: ${playbackStats.droppedFrames}", style = style)
        Text("display: %.0f Hz".format(grantedRefreshHz), style = style)
        val headroomText = if (systemStats.thermalHeadroom.isNaN()) "n/a" else "%.2f".format(systemStats.thermalHeadroom)
        Text("thermal: $headroomText", style = style)
        Text("battery: ${systemStats.batteryMa} mA", style = style)
    }
}
