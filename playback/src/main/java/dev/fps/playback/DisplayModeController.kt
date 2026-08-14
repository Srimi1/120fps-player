package dev.fps.playback

import android.app.Activity
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val TAG = "DisplayModeController"

/**
 * Requests a high refresh rate for the video surface and reports back whatever the
 * system actually grants. Samsung's LTPO panels content-match "video-looking" surfaces
 * down to the source frame rate, so downstream code must retime to [grantedRefreshHz]
 * rather than assume the requested rate was honored.
 */
class DisplayModeController(private val activity: Activity) {

    private val _grantedRefreshHz = MutableStateFlow(currentRefreshRate())
    val grantedRefreshHz: StateFlow<Float> = _grantedRefreshHz

    private val displayManager = activity.getSystemService(DisplayManager::class.java)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            _grantedRefreshHz.value = currentRefreshRate()
        }
    }

    fun start() {
        displayManager.registerDisplayListener(displayListener, null)
        _grantedRefreshHz.value = currentRefreshRate()
    }

    fun stop() {
        displayManager.unregisterDisplayListener(displayListener)
    }

    /** Requests [targetHz] on the given surface. This is a hint the OS may not honor. */
    fun requestFrameRate(surface: Surface, targetHz: Float) {
        try {
            surface.setFrameRate(
                targetHz,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "setFrameRate rejected the requested rate", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "setFrameRate failed: surface already abandoned", e)
        }
    }

    private fun currentRefreshRate(): Float = activity.display?.refreshRate ?: 0f
}
