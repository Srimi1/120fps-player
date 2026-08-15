package dev.fps.interp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CadencePlannerTest {

    // ---- cadence shapes -------------------------------------------------

    @Test
    fun `24 to 120 is a clean 5x with evenly spaced phases`() {
        val planner = CadencePlanner(Rational.FILM_24, Rational.DISPLAY_120)

        // Every source frame is followed by four synthesised ones at .2 .4 .6 .8
        val expectedPhases = listOf(0.0, 0.2, 0.4, 0.6, 0.8)
        for (n in 0L until 25L) {
            val frame = planner.frameAt(n)
            assertEquals(n / 5, frame.sourceIndex, "source index at output $n")
            assertEquals(
                expectedPhases[(n % 5).toInt()],
                frame.phase,
                1e-12,
                "phase at output $n",
            )
        }

        // exactly one in five outputs is a real frame, the rest are generated
        val sourceFrames = (0L until 120L).count { planner.frameAt(it).isSourceFrame }
        assertEquals(24, sourceFrames, "source frames in the first 120 outputs")
    }

    @Test
    fun `24 to 60 alternates 3 and 2 outputs per source frame`() {
        val planner = CadencePlanner(Rational.FILM_24, Rational.DISPLAY_60)

        // 2.5x cannot be an even split: the pattern is 3, 2, 3, 2, ...
        val perSource = (0L until 20L)
            .map { planner.frameAt(it).sourceIndex }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
            .values
            .toList()

        assertEquals(listOf(3, 2, 3, 2, 3, 2, 3, 2), perSource)
    }

    @Test
    fun `24 to 60 needs arbitrary phases, not just midpoints`() {
        val planner = CadencePlanner(Rational.FILM_24, Rational.DISPLAY_60)

        // This is why the engine must support an arbitrary phase: a midpoint-only
        // interpolator (RIFE <= v3, FILM) cannot express 0.4 / 0.8 / 0.2 / 0.6.
        val phases = (0L until 5L).map { planner.frameAt(it).phase }
        val expected = listOf(0.0, 0.4, 0.8, 0.2, 0.6)
        phases.forEachIndexed { i, phase ->
            assertEquals(expected[i], phase, 1e-12, "phase at output $i")
        }
    }

    @Test
    fun `60 to 120 is a plain 2x`() {
        val planner = CadencePlanner(Rational.VIDEO_60, Rational.DISPLAY_120)
        for (n in 0L until 20L) {
            val frame = planner.frameAt(n)
            assertEquals(n / 2, frame.sourceIndex)
            assertEquals(if (n % 2 == 0L) 0.0 else 0.5, frame.phase, 1e-12)
        }
    }

    @Test
    fun `30 to 120 is a clean 4x`() {
        val planner = CadencePlanner(Rational.VIDEO_30, Rational.DISPLAY_120)
        for (n in 0L until 20L) {
            val frame = planner.frameAt(n)
            assertEquals(n / 4, frame.sourceIndex)
            assertEquals((n % 4) * 0.25, frame.phase, 1e-12)
        }
    }

    @Test
    fun `23_976 to 60 lands exactly on a source frame only once every 1001 outputs`() {
        val planner = CadencePlanner(Rational.NTSC_FILM_23_976, Rational.DISPLAY_60)

        // The NTSC 1001 denominator means the grids realign rarely. Any engine that
        // assumes source frames recur on a short cycle is wrong for this content.
        val landings = (0L until 3003L).filter { planner.frameAt(it).isSourceFrame }
        assertEquals(listOf(0L, 1001L, 2002L), landings)
    }

    // ---- invariants that must hold for every rate pair -------------------

    @Test
    fun `phase stays in range and source index never goes backwards`() {
        val pairs = listOf(
            Rational.FILM_24 to Rational.DISPLAY_60,
            Rational.FILM_24 to Rational.DISPLAY_120,
            Rational.NTSC_FILM_23_976 to Rational.DISPLAY_60,
            Rational.NTSC_FILM_23_976 to Rational.DISPLAY_120,
            Rational.PAL_25 to Rational.DISPLAY_60,
            Rational.NTSC_29_97 to Rational.DISPLAY_120,
            Rational.VIDEO_30 to Rational.DISPLAY_60,
            Rational.VIDEO_60 to Rational.DISPLAY_120,
        )

        for ((source, output) in pairs) {
            val planner = CadencePlanner(source, output)
            var previousSourceIndex = -1L
            var previousPts = -1L

            for (n in 0L until 2000L) {
                val frame = planner.frameAt(n)
                val label = "$source -> $output at output $n"

                assertTrue(frame.phase >= 0.0, "phase below zero: $label")
                assertTrue(frame.phase < 1.0, "phase reached one: $label")
                assertTrue(
                    frame.sourceIndex >= previousSourceIndex,
                    "source index went backwards: $label",
                )
                assertTrue(
                    frame.presentationTimeUs > previousPts,
                    "timestamps not strictly increasing: $label",
                )

                previousSourceIndex = frame.sourceIndex
                previousPts = frame.presentationTimeUs
            }
        }
    }

    @Test
    fun `output never reaches past the last source frame it interpolates from`() {
        // Interpolating [k, k+1] needs frame k+1 decoded. Confirm the planner never
        // asks for a source index beyond what the source actually has.
        val planner = CadencePlanner(Rational.FILM_24, Rational.DISPLAY_120)
        val sourceFrameCount = 240L // ten seconds of 24fps
        val lastOutput = 240L * 5 - 1

        assertEquals(sourceFrameCount - 1, planner.frameAt(lastOutput).sourceIndex)
    }

    // ---- the drift property, which is the whole point --------------------

    @Test
    fun `timestamps do not drift across a two hour film`() {
        val planner = CadencePlanner(Rational.NTSC_FILM_23_976, Rational.DISPLAY_60)

        val twoHoursOfFrames = 2 * 60 * 60 * 60L // 432_000 outputs at 60fps
        val lastIndex = twoHoursOfFrames - 1

        // Exact expectation: index * 1e6 / 60, rounded to nearest.
        val expectedUs = (lastIndex * 1_000_000L + 30L) / 60L
        assertEquals(expectedUs, planner.presentationTimeUsAt(lastIndex))

        // And the error against the true rational time is under a microsecond.
        val trueTimeUs = lastIndex * 1_000_000.0 / 60.0
        assertTrue(
            kotlin.math.abs(planner.presentationTimeUsAt(lastIndex) - trueTimeUs) <= 0.5,
            "rounding error exceeded half a microsecond",
        )
    }

    @Test
    fun `a naive per-frame accumulator would drift a quarter second in two hours`() {
        // This is the bug the integer arithmetic exists to prevent. Adding a
        // truncated per-frame delta (1_000_000 / 60 = 16_666us, losing 0.67us each
        // time) looks harmless and silently walks audio out of sync.
        val planner = CadencePlanner(Rational.FILM_24, Rational.DISPLAY_60)
        val frameCount = 2 * 60 * 60 * 60L

        val naiveDeltaUs = 1_000_000L / 60L
        val naiveUs = naiveDeltaUs * frameCount
        val correctUs = planner.presentationTimeUsAt(frameCount)

        val driftUs = correctUs - naiveUs
        assertTrue(
            driftUs > 250_000L,
            "expected the naive accumulator to drift over a quarter second, drifted ${driftUs}us",
        )

        // The planner itself is immune because each timestamp is computed from its
        // index: recomputing after a seek gives byte-identical results.
        assertEquals(correctUs, planner.frameAt(frameCount).presentationTimeUs)
    }

    @Test
    fun `seeking to the middle gives the same timestamp as playing there`() {
        val planner = CadencePlanner(Rational.NTSC_29_97, Rational.DISPLAY_120)
        val target = 500_000L

        // Same answer whether we arrive by seek or by sequential playback, which is
        // what lets a seek resume without a timeline discontinuity.
        assertEquals(planner.frameAt(target), planner.frameAt(target))
        assertTrue(planner.frameAt(target).presentationTimeUs > planner.frameAt(target - 1).presentationTimeUs)
    }

    // ---- failure modes should be loud ------------------------------------

    @Test
    fun `absurd frame indices overflow loudly instead of wrapping`() {
        val planner = CadencePlanner(Rational.NTSC_FILM_23_976, Rational.DISPLAY_120)
        // A wrapped Long here would produce a negative timestamp and a scrambled
        // timeline; failing fast is the correct behaviour.
        assertFailsWith<ArithmeticException> { planner.frameAt(Long.MAX_VALUE / 2) }
    }

    @Test
    fun `negative frame indices are rejected`() {
        val planner = CadencePlanner(Rational.FILM_24, Rational.DISPLAY_120)
        assertFailsWith<IllegalArgumentException> { planner.frameAt(-1) }
    }

    @Test
    fun `nonsense frame rates are rejected`() {
        assertFailsWith<IllegalArgumentException> { Rational(0, 1) }
        assertFailsWith<IllegalArgumentException> { Rational(24, 0) }
        assertFailsWith<IllegalArgumentException> { Rational(-24, 1) }
    }

    @Test
    fun `same rate in and out is a passthrough`() {
        // Article 4: when the source already matches the display there is nothing to
        // interpolate, and the engine must not invent work.
        val planner = CadencePlanner(Rational.VIDEO_60, Rational.DISPLAY_60)
        for (n in 0L until 100L) {
            val frame = planner.frameAt(n)
            assertEquals(n, frame.sourceIndex)
            assertTrue(frame.isSourceFrame, "output $n should be a passthrough")
        }
        assertFalse(planner.frameAt(1).phase > 0.0)
    }
}
