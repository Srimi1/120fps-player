package dev.fps.app.hud

import java.util.Locale
import kotlin.math.abs

/**
 * Formatters for the diagnostics HUD.
 *
 * Pure and Android-free so they can be unit tested. Every one of them pins
 * [Locale.US]: these are instrument readings, and a HUD that renders "١٢٠ Hz" under
 * an Arabic locale is unreadable next to the Latin-digit numbers around it, while a
 * locale using comma decimals turns "8.33" into "8,33" in a log someone will later
 * try to parse.
 */
object Hud {

    private const val UNKNOWN = "--"

    /** `1:04:07` past an hour, `4:07` under it. Negative and unset clamp to `0:00`. */
    fun time(millis: Long): String {
        if (millis <= 0L) return "0:00"
        val totalSeconds = millis / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    /**
     * A *measured* rate, to one decimal. This is a live reading that jitters, so more
     * precision would be noise pretending to be signal.
     */
    fun fps(value: Float): String =
        if (!value.isFinite() || value <= 0f) UNKNOWN
        else String.format(Locale.US, "%.1f", value)

    /**
     * A *declared* rate, to whatever precision it actually needs.
     *
     * One decimal is not enough here: 23.976 and 24 both round to "24.0", and telling
     * those two apart is the entire reason [dev.fps.interp.core.Rational] exists. NTSC
     * rates need three decimals, integer rates need none, so trailing zeros are
     * trimmed and 24 prints as "24" rather than "24.000".
     */
    fun exactFps(value: Float): String {
        if (!value.isFinite() || value <= 0f) return UNKNOWN
        return String.format(Locale.US, "%.3f", value)
            .trimEnd('0')
            .trimEnd('.')
    }

    fun hz(value: Float): String =
        if (!value.isFinite() || value <= 0f) UNKNOWN
        else String.format(Locale.US, "%.0f", value)

    /** 0.00 cool, 1.00 at the throttling threshold. */
    fun headroom(value: Float): String =
        if (!value.isFinite()) UNKNOWN else String.format(Locale.US, "%.2f", value)

    /**
     * Signed milliamps with an explicit direction word, because the sign convention
     * for `BATTERY_PROPERTY_CURRENT_NOW` is not consistent across OEMs -- Android
     * documents positive as charging, and several vendors ship the opposite. Printing
     * the magnitude alongside the raw sign lets a device report be interpreted later
     * even if the phone that produced it inverts it.
     */
    fun current(milliAmps: Int?): String = when {
        milliAmps == null -> UNKNOWN
        milliAmps == 0 -> "0 mA"
        else -> String.format(Locale.US, "%,d mA", abs(milliAmps)) +
            if (milliAmps < 0) " out" else " in"
    }

    fun percent(value: Int?): String =
        if (value == null) UNKNOWN else String.format(Locale.US, "%d%%", value)

    /**
     * Names the platform thermal status. Values match `PowerManager.THERMAL_STATUS_*`;
     * they are inlined rather than referenced so this file stays testable off-device.
     */
    fun thermalStatus(status: Int?): String = when (status) {
        null -> UNKNOWN
        0 -> "none"
        1 -> "light"
        2 -> "moderate"
        3 -> "severe"
        4 -> "critical"
        5 -> "emergency"
        6 -> "shutdown"
        else -> "unknown($status)"
    }

    /** `1920x1080`, or `--` before the decoder reports a size. */
    fun resolution(width: Int, height: Int): String =
        if (width <= 0 || height <= 0) UNKNOWN else "${width}x$height"

    /** `5.00x`, the output-to-source frame ratio. */
    fun ratio(value: Double): String =
        if (!value.isFinite() || value <= 0.0) UNKNOWN
        else String.format(Locale.US, "%.2fx", value)
}
