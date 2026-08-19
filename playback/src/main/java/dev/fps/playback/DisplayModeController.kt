package dev.fps.playback

import android.app.Activity
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "DisplayModeController"

/**
 * What the app asked the display for, and what it got.
 *
 * These are separate fields on purpose. Article 5: a target rate may be *requested*
 * as a constant but may never be *assumed* as the output rate, so the two readings
 * are never collapsed into one and the HUD shows both.
 */
data class DisplayModeState(
    /** Last rate passed to [DisplayModeController.requestFrameRate], or 0 if none yet. */
    val requestedHz: Float = 0f,
    /** Rate the display is actually running at, read back from the system. */
    val grantedHz: Float = 0f,
    /** Highest rate this panel advertises, for context on a downgrade. */
    val panelMaxHz: Float = 0f,
) {
    /** True when a request was made and the system is running slower than asked. */
    val wasDowngraded: Boolean
        get() = requestedHz > 0f && grantedHz > 0f && grantedHz < requestedHz - DOWNGRADE_EPSILON_HZ

    private companion object {
        // 120 vs 119.99 is not a downgrade; 120 vs 60 is.
        const val DOWNGRADE_EPSILON_HZ = 1f
    }
}

/**
 * Requests a high refresh rate for the video surface and reports back whatever the
 * system actually grants. Samsung's LTPO panels content-match "video-looking" surfaces
 * down to the source frame rate, so downstream code must retime to the granted rate
 * rather than assume the requested rate was honored.
 */
class DisplayModeController(private val activity: Activity) {

    private val _state = MutableStateFlow(DisplayModeState())
    val state: StateFlow<DisplayModeState> = _state.asStateFlow()

    private val displayManager = activity.getSystemService(DisplayManager::class.java)

    private var registered = false

    /**
     * Surviving copy of the last request, so a surface that gets recreated (which
     * happens on every background/foreground cycle) can be re-asked without the
     * caller having to remember the rate.
     */
    private var lastRequestedHz = 0f

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (registered) return
        displayManager.registerDisplayListener(displayListener, null)
        registered = true
        refresh()
    }

    fun stop() {
        if (!registered) return
        displayManager.unregisterDisplayListener(displayListener)
        registered = false
    }

    /**
     * Requests [targetHz] on the given surface. This is a hint the OS may not honor,
     * which is the entire reason [DisplayModeState.grantedHz] is read back separately.
     */
    fun requestFrameRate(surface: Surface, targetHz: Float) {
        if (!surface.isValid) {
            Log.w(TAG, "skipping setFrameRate on an invalid surface")
            return
        }
        try {
            surface.setFrameRate(
                targetHz,
                Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                Surface.CHANGE_FRAME_RATE_ALWAYS,
            )
            lastRequestedHz = targetHz
            refresh()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "setFrameRate rejected the requested rate", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "setFrameRate failed: surface already abandoned", e)
        }
    }

    /**
     * Re-asks for the rate most recently requested. Worth doing once the decoder has
     * produced frames: a panel that content-matches decides based on what is actually
     * on the surface, so the answer before and after playback starts can differ.
     */
    fun reassert(surface: Surface) {
        if (lastRequestedHz > 0f) requestFrameRate(surface, lastRequestedHz)
    }

    /** Re-reads the granted rate from the system. */
    fun refresh() {
        val display = activity.display
        _state.value = DisplayModeState(
            requestedHz = lastRequestedHz,
            grantedHz = display?.refreshRate ?: 0f,
            panelMaxHz = display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f,
        )
    }
}
