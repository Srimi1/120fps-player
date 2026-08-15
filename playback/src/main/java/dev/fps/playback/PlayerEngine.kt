package dev.fps.playback

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Thin wrapper around ExoPlayer. Carries no interpolation knowledge -- Phase 0 is
 * plain playback plus the instrumentation hooks later phases build on.
 */
class PlayerEngine(context: Context) {

    val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    fun attachTo(surfaceView: SurfaceView) {
        player.setVideoSurfaceView(surfaceView)
    }

    fun open(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlayPause() {
        player.playWhenReady = !player.playWhenReady
    }

    fun addListener(listener: Player.Listener) {
        player.addListener(listener)
    }

    fun release() {
        player.release()
    }
}
