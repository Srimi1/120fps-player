package dev.fps.interp.core

/**
 * A single frame the player will present.
 *
 * The interpolator is asked to synthesise the image lying [phaseNum]/[phaseDen] of
 * the way between source frames [sourceIndex] and [sourceIndex] + 1. A phase of
 * zero means the output lands exactly on a source frame, so it is shown as-is and
 * costs nothing.
 */
data class OutputFrame(
    /** Output frame counter, starting at zero. */
    val index: Long,
    /** Interpolate between this source frame and the next. */
    val sourceIndex: Long,
    /** Exact position within the source interval, as [phaseNum]/[phaseDen] in [0, 1). */
    val phaseNum: Long,
    val phaseDen: Long,
    /** When to present this frame, in microseconds from the start of the stream. */
    val presentationTimeUs: Long,
) {
    /** Position within the source interval as a float, for shader uniforms. */
    val phase: Double get() = phaseNum.toDouble() / phaseDen.toDouble()

    /** True when this output is an unmodified source frame — no interpolation needed. */
    val isSourceFrame: Boolean get() = phaseNum == 0L
}

/**
 * Maps output frames onto source frames for frame-rate up-conversion.
 *
 * Everything is exact integer arithmetic derived from the output frame index, so
 * timestamps are computed rather than accumulated and cannot drift no matter how
 * long playback runs. See [CadencePlannerTest] for the two-hour drift check and
 * the comparison against a naive accumulator.
 *
 * Cadences worth knowing:
 *  - 24 -> 120 is a clean 5x; every source frame gets phases 0, .2, .4, .6, .8
 *  - 24 -> 60 is 2.5x, so it alternates 3 and 2 outputs per source frame and
 *    requires arbitrary-phase interpolation, not just midpoints
 *  - 60 -> 120 is a plain 2x — the cheapest case
 */
class CadencePlanner(
    val sourceFps: Rational,
    val outputFps: Rational,
) {

    /**
     * Describes the output frame at [index].
     *
     * Pure function of [index] — this is what makes the timeline drift-free.
     *
     * @throws ArithmeticException if [index] is far enough out that the exact
     *   arithmetic would overflow, rather than silently wrapping to a bad timestamp.
     */
    fun frameAt(index: Long): OutputFrame {
        require(index >= 0) { "output frame index must be non-negative, was $index" }

        // Position along the source timeline, measured in source frames:
        //   t          = index / outputFps
        //   position   = t * sourceFps
        //              = (index * outputFps.den * sourceFps.num) / (outputFps.num * sourceFps.den)
        val positionNum = Math.multiplyExact(
            Math.multiplyExact(index, outputFps.den),
            sourceFps.num,
        )
        val positionDen = Math.multiplyExact(outputFps.num, sourceFps.den)

        val sourceIndex = Math.floorDiv(positionNum, positionDen)
        val phaseNum = Math.floorMod(positionNum, positionDen)

        return OutputFrame(
            index = index,
            sourceIndex = sourceIndex,
            phaseNum = phaseNum,
            phaseDen = positionDen,
            presentationTimeUs = presentationTimeUsAt(index),
        )
    }

    /**
     * Presentation timestamp of output frame [index], in microseconds, rounded to
     * nearest. Computed from [index] directly — never by adding a per-frame delta.
     */
    fun presentationTimeUsAt(index: Long): Long {
        require(index >= 0) { "output frame index must be non-negative, was $index" }
        val numerator = Math.multiplyExact(
            Math.multiplyExact(index, MICROS_PER_SECOND),
            outputFps.den,
        )
        // Round half up rather than truncating, so error stays within half a
        // microsecond instead of biasing consistently early.
        return Math.floorDiv(
            Math.addExact(numerator, outputFps.num / 2),
            outputFps.num,
        )
    }

    /** The output frames of the stream, lazily, starting at zero. */
    fun frames(): Sequence<OutputFrame> = generateSequence(0L) { it + 1 }.map(::frameAt)

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
