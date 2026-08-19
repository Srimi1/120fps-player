package dev.fps.app.hud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HudFormattingTest {

    @Test
    fun `time crosses the hour boundary correctly`() {
        assertEquals("0:00", Hud.time(0))
        assertEquals("0:07", Hud.time(7_400))
        assertEquals("4:07", Hud.time(247_000))
        assertEquals("59:59", Hud.time(3_599_000))
        assertEquals("1:00:00", Hud.time(3_600_000))
        assertEquals("2:13:20", Hud.time(8_000_000))
    }

    @Test
    fun `time clamps unset and negative durations instead of printing nonsense`() {
        // ExoPlayer reports C.TIME_UNSET as Long.MIN_VALUE before the duration is
        // known, which naive arithmetic turns into a wildly negative clock.
        assertEquals("0:00", Hud.time(Long.MIN_VALUE))
        assertEquals("0:00", Hud.time(-1))
    }

    @Test
    fun `a measured rate reads to one decimal`() {
        assertEquals("24.0", Hud.fps(24f))
        assertEquals("119.9", Hud.fps(119.88f))
        assertEquals("59.9", Hud.fps(59.94f))
        assertEquals("--", Hud.fps(0f))
        assertEquals("--", Hud.fps(Float.NaN))
        assertEquals("--", Hud.fps(-5f))
    }

    @Test
    fun `a declared rate keeps the precision that separates 23_976 from 24`() {
        // The distinction the whole Rational type exists to preserve. At one decimal
        // both of these print "24.0" and the HUD cannot tell you which file you opened.
        assertEquals("23.976", Hud.exactFps(23.976f))
        assertEquals("24", Hud.exactFps(24f))
        assertEquals("29.97", Hud.exactFps(29.97f))
        assertEquals("30", Hud.exactFps(30f))
        assertEquals("25", Hud.exactFps(25f))
        assertEquals("--", Hud.exactFps(0f))
        assertEquals("--", Hud.exactFps(Float.NaN))
    }

    @Test
    fun `unavailable readings render as dashes, never as zero`() {
        // A HUD that shows 0.00 for "no sensor" and 0.00 for "ice cold" is useless.
        assertEquals("--", Hud.headroom(Float.NaN))
        assertEquals("0.00", Hud.headroom(0f))
        assertEquals("0.87", Hud.headroom(0.8712f))
        assertEquals("--", Hud.current(null))
        assertEquals("--", Hud.percent(null))
        assertEquals("--", Hud.hz(0f))
        assertEquals("--", Hud.resolution(0, 1080))
    }

    @Test
    fun `battery current states its direction`() {
        assertEquals("1,240 mA out", Hud.current(-1240))
        assertEquals("980 mA in", Hud.current(980))
        assertEquals("0 mA", Hud.current(0))
    }

    @Test
    fun `thermal status names cover the full platform range`() {
        assertEquals("none", Hud.thermalStatus(0))
        assertEquals("severe", Hud.thermalStatus(3))
        assertEquals("shutdown", Hud.thermalStatus(6))
        assertEquals("--", Hud.thermalStatus(null))
        assertTrue(Hud.thermalStatus(99).startsWith("unknown"))
    }

    @Test
    fun `resolution and ratio format as expected`() {
        assertEquals("1920x1080", Hud.resolution(1920, 1080))
        assertEquals("5.00x", Hud.ratio(5.0))
        assertEquals("2.50x", Hud.ratio(2.5))
        assertEquals("--", Hud.ratio(0.0))
    }

    // ---- the sanitising that keeps garbage off the HUD --------------------

    @Test
    fun `an unsupported fuel gauge reads as unavailable, not as minus two thousand amps`() {
        // getIntProperty documents Integer.MIN_VALUE for an unsupported property.
        // Dividing that by 1000 and printing it puts "-2147483 mA" on screen.
        assertNull(SystemStats.sanitiseCurrent(Int.MIN_VALUE))
        assertNull(SystemStats.sanitiseCurrent(Int.MAX_VALUE))
    }

    @Test
    fun `plausible currents survive sanitising`() {
        assertEquals(-1240, SystemStats.sanitiseCurrent(-1_240_000))
        assertEquals(980, SystemStats.sanitiseCurrent(980_000))
        assertEquals(0, SystemStats.sanitiseCurrent(120))
    }

    @Test
    fun `implausible currents are rejected`() {
        assertNull(SystemStats.sanitiseCurrent(500_000_000))
        assertNull(SystemStats.sanitiseCurrent(-500_000_000))
    }

    @Test
    fun `stats builder rejects every out-of-range platform value`() {
        val stats = SystemStats.from(
            rawHeadroom = Float.NEGATIVE_INFINITY,
            rawThermalStatus = -1,
            rawCurrentMicroAmps = Int.MIN_VALUE,
            rawBatteryPercent = -1,
        )
        assertTrue(stats.thermalHeadroom.isNaN())
        assertNull(stats.thermalStatus)
        assertNull(stats.batteryMa)
        assertNull(stats.batteryPercent)
    }

    @Test
    fun `stats builder keeps good platform values`() {
        val stats = SystemStats.from(
            rawHeadroom = 0.42f,
            rawThermalStatus = 2,
            rawCurrentMicroAmps = -1_500_000,
            rawBatteryPercent = 73,
        )
        assertEquals(0.42f, stats.thermalHeadroom)
        assertEquals(2, stats.thermalStatus)
        assertEquals(-1500, stats.batteryMa)
        assertEquals(73, stats.batteryPercent)
    }
}
