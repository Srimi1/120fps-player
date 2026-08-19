package dev.fps.playback

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the UI needs to know about the player, as one immutable snapshot.
 *
 * [error] is a user-facing sentence, not a stack trace: a codec the device cannot
 * decode is a normal outcome for a player that opens arbitrary files, and silently
 * showing a black rectangle is the wrong answer to it.
 */
data class PlayerState(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val hasEnded: Boolean = false,
    val durationMs: Long = C.TIME_UNSET,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val error: String? = null,
) {
    val hasKnownDuration: Boolean get() = durationMs != C.TIME_UNSET && durationMs > 0
}

/** Where the playhead is right now. Polled, because the player does not emit this. */
data class PlayerProgress(
    val positionMs: Long = 0L,
    val bufferedMs: Long = 0L,
)

/**
 * Thin wrapper around ExoPlayer. Carries no interpolation knowledge -- Phase 0 is
 * plain playback plus the instrumentation hooks later phases build on.
 */
class PlayerEngine(context: Context) {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        // Request audio focus so the player ducks and pauses for calls and other
        // apps, and pause when headphones are unplugged rather than blaring out of
        // the speaker. Both are table stakes for anything calling itself a player.
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = pushState()

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                error = describe(error),
                isPlaying = false,
                isBuffering = false,
            )
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) = pushState()
    }

    init {
        player.addListener(listener)
    }

    private fun pushState() {
        val existingError = _state.value.error
        _state.value = PlayerState(
            hasMedia = player.currentMediaItem != null,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            hasEnded = player.playbackState == Player.STATE_ENDED,
            durationMs = player.duration,
            videoWidth = player.videoSize.width,
            videoHeight = player.videoSize.height,
            // A fresh error arrives via onPlayerError; anything already showing stays
            // until the next open() clears it.
            error = existingError,
        )
    }

    fun attachTo(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    fun open(uri: Uri) {
        _state.value = PlayerState()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
        pushState()
    }

    fun togglePlayPause() {
        if (!_state.value.hasMedia) return
        // Replaying from the end is the expected behaviour of a play button on a
        // finished video; without this the button looks dead.
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.playWhenReady = true
            return
        }
        player.playWhenReady = !player.playWhenReady
    }

    fun play() {
        if (_state.value.hasMedia) player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    /** Seeks to [positionMs], clamped into the media. */
    fun seekTo(positionMs: Long) {
        if (!_state.value.hasMedia) return
        val duration = player.duration
        val target = if (duration == C.TIME_UNSET) positionMs.coerceAtLeast(0L)
        else positionMs.coerceIn(0L, duration)
        player.seekTo(target)
    }

    /** Seeks [deltaMs] from the current position. Negative rewinds. */
    fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun progress(): PlayerProgress = PlayerProgress(
        positionMs = player.currentPosition.coerceAtLeast(0L),
        bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
    )

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        ->
            "This device has no decoder for that video."

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "That file is gone or is no longer readable."

        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "No permission to read that file. Pick it again."

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        ->
            "That file is not a video this player can parse."

        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
        ->
            "The decoder failed on that file."

        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
        ->
            "That file could not be read to the end."

        // Deliberately still names the code: an unmapped failure is one we want back
        // in a device report verbatim, and a bare "something went wrong" loses it.
        else -> "Playback failed (${error.errorCodeName})."
    }
}
