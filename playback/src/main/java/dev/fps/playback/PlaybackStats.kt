package dev.fps.playback

import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

data class PlaybackStats(
    val renderedFps: Float = 0f,
    val droppedFrames: Int = 0,
)

private const val FPS_WINDOW_NANOS = 1_000_000_000L
private const val EMIT_INTERVAL_NANOS = 200_000_000L

/**
 * Feeds the debug HUD: rendered-frame rate from a rolling 1s window of frame-release
 * timestamps, plus a running total of frames ExoPlayer decided to skip.
 */
@UnstableApi
class PlaybackStatsCollector : AnalyticsListener, VideoFrameMetadataListener {

    private val _stats = MutableStateFlow(PlaybackStats())
    val stats: StateFlow<PlaybackStats> = _stats

    private val recentFrameTimestamps = ArrayDeque<Long>()
    private var totalDropped = 0
    private var lastEmitNanos = 0L

    fun attachTo(player: ExoPlayer) {
        player.addAnalyticsListener(this)
        player.setVideoFrameMetadataListener(this)
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: MediaFormat?,
    ) {
        val now = System.nanoTime()
        recentFrameTimestamps.addLast(now)
        while (recentFrameTimestamps.isNotEmpty() && now - recentFrameTimestamps.peekFirst() > FPS_WINDOW_NANOS) {
            recentFrameTimestamps.removeFirst()
        }
        if (now - lastEmitNanos >= EMIT_INTERVAL_NANOS) {
            lastEmitNanos = now
            _stats.value = PlaybackStats(
                renderedFps = recentFrameTimestamps.size.toFloat(),
                droppedFrames = totalDropped,
            )
        }
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        totalDropped += droppedFrames
    }
}
