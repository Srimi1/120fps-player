package dev.fps.interp.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DisplayRateTest {

    @Test
    fun `real panel readings snap to the exact rate`() {
        // Values in the shape Display.getRefreshRate() actually returns on shipping
        // hardware -- none of them are round, which is why == never works.
        assertEquals(Rational.DISPLAY_120, DisplayRate.snap(120.00001f))
        assertEquals(Rational.DISPLAY_120, DisplayRate.snap(119.9999f))
        assertEquals(Rational.DISPLAY_60, DisplayRate.snap(60.000004f))
        assertEquals(Rational(90, 1), DisplayRate.snap(89.9998f))
    }

    @Test
    fun `container frame rates snap to the exact rate`() {
        assertEquals(Rational.NTSC_FILM_23_976, DisplayRate.snap(23.976025f))
        assertEquals(Rational.FILM_24, DisplayRate.snap(24.0f))
        assertEquals(Rational.NTSC_29_97, DisplayRate.snap(29.970030f))
        assertEquals(Rational.PAL_25, DisplayRate.snap(25.0f))
    }

    @Test
    fun `an unrecognised rate returns null rather than a guess`() {
        // Article 5: the app adapts to the granted rate. Coercing 75 Hz to "probably
        // 60" would have the cadence planner retime to a rate the panel is not running.
        assertNull(DisplayRate.snap(75f))
        assertNull(DisplayRate.snap(0f))
        assertNull(DisplayRate.snap(-60f))
        assertNull(DisplayRate.snap(Float.NaN))
        assertNull(DisplayRate.snap(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `every known rate snaps to itself`() {
        for (rate in DisplayRate.KNOWN) {
            assertEquals(rate, DisplayRate.snap(rate.toDouble()), "round trip for $rate")
        }
    }

    @Test
    fun `rates just outside tolerance are rejected`() {
        // 120 +- 0.2 snaps; beyond that it is not this rate.
        assertEquals(Rational.DISPLAY_120, DisplayRate.snap(119.81f))
        assertNull(DisplayRate.snap(119.5f))
    }
}
