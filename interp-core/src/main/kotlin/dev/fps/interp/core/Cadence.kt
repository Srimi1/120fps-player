package dev.fps.interp.core

/**
 * What up-converting [source] to [output] actually costs.
 *
 * Constructed from rates that were *observed* — the container's frame rate and the
 * refresh rate the OS granted — never from a target constant. Article 5: a rate may
 * be requested as a constant, but the cadence must be computed from what was
 * granted, because Samsung's LTPO panels content-match a video surface back down to
 * the source rate and the system revokes high rates under thermal pressure.
 */
data class Cadence(
    val source: Rational,
    val output: Rational,
) {
    /** Output frames per source frame, e.g. 5.0 for 24 -> 120. */
    val ratio: Double get() = output.toDouble() / source.toDouble()

    /**
     * True when the display is running at (or below) the source rate, so there is
     * nothing to gain. Article 4: the engine must not invent work.
     */
    val isPassthrough: Boolean get() = output.toDouble() <= source.toDouble() + PASSTHROUGH_EPSILON_HZ

    /**
     * Frames that must be synthesised each second — the actual per-second budget the
     * interpolator has to hit. This is the number that decides feasibility: 24 -> 120
     * needs 96/s, 24 -> 60 needs 36/s, 60 -> 120 needs 60/s.
     */
    val generatedFramesPerSecond: Double
        get() = if (isPassthrough) 0.0 else output.toDouble() - source.toDouble()

    /**
     * Wall-clock budget for one generated frame, in milliseconds — how long the GPU
     * has per synthesised frame before it starts dropping them.
     */
    val budgetPerGeneratedFrameMs: Double
        get() = if (generatedFramesPerSecond <= 0.0) Double.POSITIVE_INFINITY
        else 1_000.0 / output.toDouble()

    /** A planner for this cadence, or null when there is nothing to plan. */
    fun planner(): CadencePlanner? =
        if (isPassthrough) null else CadencePlanner(source, output)

    override fun toString(): String = "$source -> $output"

    companion object {
        /**
         * 59.94 vs 60 is a 0.06 Hz difference. Treating that as "there is upconversion
         * to do" would have the engine synthesise frames for a 1.001x ratio, which is
         * pure cost for no visible gain, so anything under a tenth of a hertz counts
         * as passthrough.
         */
        const val PASSTHROUGH_EPSILON_HZ: Double = 0.1

        /**
         * Builds a cadence from two *measured* rates, snapping each onto an exact
         * [Rational]. Returns null when either measurement is unrecognisable, which is
         * the honest answer — a guessed granted rate is worse than no reading.
         */
        fun fromMeasured(sourceHz: Float, grantedHz: Float): Cadence? {
            val source = DisplayRate.snap(sourceHz) ?: return null
            val output = DisplayRate.snap(grantedHz) ?: return null
            return Cadence(source, output)
        }
    }
}
