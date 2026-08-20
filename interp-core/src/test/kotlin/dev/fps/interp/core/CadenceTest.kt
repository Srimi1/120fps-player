package dev.fps.interp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CadenceTest {

    @Test
    fun `24 to 120 needs 96 synthesised frames a second`() {
        val cadence = Cadence(Rational.FILM_24, Rational.DISPLAY_120)
        assertEquals(5.0, cadence.ratio, 1e-9)
        assertEquals(96.0, cadence.generatedFramesPerSecond, 1e-9)
        // 8.33 ms per output frame is the budget the feasibility report measures against.
        assertEquals(8.3333, cadence.budgetPerGeneratedFrameMs, 1e-3)
        assertFalse(cadence.isPassthrough)
    }

    @Test
    fun `24 to 60 needs 36 synthesised frames a second`() {
        val cadence = Cadence(Rational.FILM_24, Rational.DISPLAY_60)
        assertEquals(36.0, cadence.generatedFramesPerSecond, 1e-9)
        assertEquals(16.6666, cadence.budgetPerGeneratedFrameMs, 1e-3)
    }

    @Test
    fun `matching rates are passthrough and cost nothing`() {
        // Article 4: when the source already matches the display, invent no work.
        val cadence = Cadence(Rational.VIDEO_60, Rational.DISPLAY_60)
        assertTrue(cadence.isPassthrough)
        assertEquals(0.0, cadence.generatedFramesPerSecond, 1e-9)
        assertNull(cadence.planner())
    }

    @Test
    fun `59_94 source on a 60 Hz panel is passthrough, not a 1_001x upconversion`() {
        // The 0.06 Hz gap is real but synthesising frames for it is pure cost.
        val cadence = Cadence(Rational(60_000, 1001), Rational.DISPLAY_60)
        assertTrue(cadence.isPassthrough)
        assertEquals(0.0, cadence.generatedFramesPerSecond, 1e-9)
    }

    @Test
    fun `a display slower than the source is passthrough`() {
        // The panel content-matched down below the source. There is nothing to add.
        val cadence = Cadence(Rational.VIDEO_60, Rational(30, 1))
        assertTrue(cadence.isPassthrough)
        assertNull(cadence.planner())
    }

    @Test
    fun `a real cadence produces a planner that agrees with it`() {
        val cadence = Cadence(Rational.FILM_24, Rational.DISPLAY_120)
        val planner = assertNotNull(cadence.planner())
        assertEquals(Rational.FILM_24, planner.sourceFps)
        assertEquals(Rational.DISPLAY_120, planner.outputFps)
        // one in five outputs is a real frame, matching the 5x ratio
        assertEquals(24, (0L until 120L).count { planner.frameAt(it).isSourceFrame })
    }

    @Test
    fun `fromMeasured snaps both jittery readings`() {
        // Exactly the pair the HUD gets: a container rate and a panel rate, neither round.
        val cadence = assertNotNull(Cadence.fromMeasured(23.976025f, 120.00001f))
        assertEquals(Rational.NTSC_FILM_23_976, cadence.source)
        assertEquals(Rational.DISPLAY_120, cadence.output)
    }

    @Test
    fun `fromMeasured refuses to invent a cadence from an unreadable rate`() {
        // Article 5: better to show "unknown" in the HUD than to retime to a rate the
        // panel is not actually running.
        assertNull(Cadence.fromMeasured(23.976f, 0f))
        assertNull(Cadence.fromMeasured(0f, 120f))
        assertNull(Cadence.fromMeasured(Float.NaN, 120f))
        assertNull(Cadence.fromMeasured(23.976f, 75f))
    }
}
