package dev.fps.playback

import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.video.VideoFrameMetadataListener

data class PlaybackStats(
    val renderedFps: Float = 0f,
    val droppedFrames: Int = 0,
    /** Frame rate the decoder reports for the current video, or 0 when unknown. */
    val sourceFrameRate: Float = 0f,
)

/** Frames older than this stop counting toward the rate. */
private const val FPS_WINDOW_NANOS = 1_000_000_000L

/**
 * Feeds the debug HUD: rendered-frame rate over a rolling one-second window, plus a
 * running total of frames ExoPlayer decided to skip.
 *
 * The rate is *sampled by the reader* rather than pushed from the render callback.
 * Pushing looks simpler but has a bug that matters for a diagnostics HUD: when
 * playback pauses or stalls, the callback stops firing, so the last value pushed
 * stands forever and the HUD confidently reports 24fps over a frozen picture. The
 * number the HUD exists to show is exactly the number that would be wrong.
 */
@UnstableApi
class PlaybackStatsCollector : AnalyticsListener, VideoFrameMetadataListener {

    // Written on the playback thread, read by the sampler. Single writer, single
    // reader, and Long/Int writes are atomic on the JVM, so volatile is sufficient.
    @Volatile
    private var renderedFrames = 0L

    @Volatile
    private var totalDropped = 0

    @Volatile
    private var sourceFrameRate = 0f

    // Touched only by the sampler.
    private val samples = ArrayDeque<Sample>()

    private data class Sample(val nanos: Long, val frames: Long)

    fun attachTo(player: ExoPlayer) {
        player.addAnalyticsListener(this)
        player.setVideoFrameMetadataListener(this)
    }

    /**
     * Clears the counters for a newly opened video. Without this the dropped-frame
     * total carries across files, so the second video you open starts out looking
     * like it dropped hundreds of frames it never saw.
     */
    fun reset() {
        renderedFrames = 0L
        totalDropped = 0
        sourceFrameRate = 0f
        samples.clear()
    }

    override fun onVideoFrameAboutToBeRendered(
        presentationTimeUs: Long,
        releaseTimeNs: Long,
        format: Format,
        mediaFormat: MediaFormat?,
    ) {
        renderedFrames++
        val declared = format.frameRate
        if (declared > 0f && declared.isFinite()) sourceFrameRate = declared
    }

    override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
        totalDropped += droppedFrames
    }

    /**
     * Takes a reading. Call on a steady ticker; the window is derived from the
     * timestamps of the samples themselves, so an irregular tick is not a problem.
     *
     * [isPlaying] is the difference between "stopped" and "struggling". A first
     * attempt blanked the reading whenever no frame had arrived recently, which is
     * wrong in the one case the HUD matters most: a device rendering 1.6fps because
     * it cannot keep up is not a device with no reading, and hiding that number hides
     * the finding. Now a paused player reports a true zero, and a playing one reports
     * whatever the window measured however bad it is.
     *
     * Not thread safe with respect to itself -- one sampler only.
     */
    fun sample(isPlaying: Boolean): PlaybackStats {
        val now = System.nanoTime()
        samples.addLast(Sample(now, renderedFrames))
        while (samples.size > 2 && now - samples.first().nanos > FPS_WINDOW_NANOS) {
            samples.removeFirst()
        }

        val oldest = samples.first()
        val elapsed = now - oldest.nanos
        val fps = when {
            !isPlaying -> 0f
            elapsed <= 0L -> 0f
            // Frames rendered across the window, over the window's real duration. With
            // playback stopped this decays to zero on its own as the window slides.
            else -> (renderedFrames - oldest.frames) * 1_000_000_000f / elapsed
        }

        return PlaybackStats(
            renderedFps = fps,
            droppedFrames = totalDropped,
            sourceFrameRate = sourceFrameRate,
        )
    }
}
