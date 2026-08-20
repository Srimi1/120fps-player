package dev.fps.interp.core

import kotlin.math.abs

/**
 * Snaps a measured refresh rate onto an exact [Rational].
 *
 * Panels and containers do not report round numbers. `Display.getRefreshRate()`
 * returns values like 120.00001 or 59.94006, and a container's frame rate comes
 * back as 23.976025. Comparing those with `==` never matches, and feeding the raw
 * float into the cadence arithmetic reintroduces exactly the drift [CadencePlanner]
 * exists to prevent.
 *
 * Snapping is deliberately conservative: an unrecognised rate returns null rather
 * than being coerced to the nearest guess, because Article 5 requires the app to
 * adapt to what the display actually granted, and a wrong guess about the granted
 * rate is worse than admitting it is unknown.
 */
object DisplayRate {

    /**
     * Rates a phone panel or a video container realistically reports. The NTSC
     * family carries the 1001 denominator; everything else is an integer rate.
     */
    val KNOWN: List<Rational> = listOf(
        Rational(24_000, 1001), // 23.976
        Rational(24, 1),
        Rational(25, 1),
        Rational(30_000, 1001), // 29.97
        Rational(30, 1),
        Rational(48, 1),
        Rational(50, 1),
        Rational(60_000, 1001), // 59.94
        Rational(60, 1),
        Rational(90, 1),
        Rational(100, 1),
        Rational(120, 1),
        Rational(144, 1),
    )

    /**
     * How far a measurement may sit from a known rate and still snap to it.
     *
     * 0.2 Hz is wide enough to absorb panel jitter and container rounding, and
     * narrow enough that 90 can never be mistaken for 120.
     *
     * 59.94 and 60 sit 0.06 Hz apart, so they are NOT separable at this tolerance
     * and the nearest simply wins. That is harmless: [Cadence] treats that pair as
     * passthrough either way. The NTSC-family 119.88 is deliberately absent from
     * [KNOWN] for the same reason inverted — no phone panel runs it and almost no
     * content carries it, so listing it would only put an ambiguity zone 0.12 Hz
     * from 120, the one reading this whole project turns on.
     */
    const val TOLERANCE_HZ: Double = 0.2

    /** The known rate nearest [measuredHz], or null if nothing is within [TOLERANCE_HZ]. */
    fun snap(measuredHz: Float): Rational? = snap(measuredHz.toDouble())

    fun snap(measuredHz: Double): Rational? {
        if (!measuredHz.isFinite() || measuredHz <= 0.0) return null
        val nearest = KNOWN.minByOrNull { abs(it.toDouble() - measuredHz) } ?: return null
        return if (abs(nearest.toDouble() - measuredHz) <= TOLERANCE_HZ) nearest else null
    }
}
