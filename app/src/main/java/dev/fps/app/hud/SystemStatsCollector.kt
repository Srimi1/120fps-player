package dev.fps.app.hud

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A reading of how much thermal and power budget the device has left.
 *
 * Every field is nullable-by-sentinel rather than zero-defaulted, because zero is a
 * meaningful value for all of them and "no reading yet" is not the same as "no
 * headroom left". Defaulting thermal headroom to 0f -- as the first version did --
 * renders as `thermal: 0.00`, which reads as *ice cold* to one person and *at the
 * throttling limit* to another, on a HUD whose whole job is to be unambiguous.
 */
data class SystemStats(
    /** 0.0 is cool, 1.0 is the throttling threshold. NaN when unavailable. */
    val thermalHeadroom: Float = Float.NaN,
    /** [PowerManager.THERMAL_STATUS_NONE]..`THERMAL_STATUS_SHUTDOWN`, or null if unknown. */
    val thermalStatus: Int? = null,
    /** Instantaneous battery current in mA. Negative is discharge on most devices. Null if unsupported. */
    val batteryMa: Int? = null,
    /** Battery charge 0..100, or null if unsupported. */
    val batteryPercent: Int? = null,
) {
    companion object {

        /**
         * Largest current we will believe, in mA. A phone under sustained GPU load
         * draws on the order of 2000mA; anything past 100A is the device telling us
         * the property is unsupported, not a measurement.
         */
        private const val PLAUSIBLE_MA_LIMIT = 100_000

        /**
         * Builds a reading from the raw platform values.
         *
         * Split out from the polling loop and free of Android imports so the
         * sanitising is unit testable -- which matters, because every one of these
         * guards exists for a device that really does return the bad value.
         */
        fun from(
            rawHeadroom: Float,
            rawThermalStatus: Int?,
            rawCurrentMicroAmps: Int,
            rawBatteryPercent: Int,
        ): SystemStats = SystemStats(
            thermalHeadroom = if (rawHeadroom.isFinite()) rawHeadroom else Float.NaN,
            thermalStatus = rawThermalStatus?.takeIf { it >= 0 },
            batteryMa = sanitiseCurrent(rawCurrentMicroAmps),
            batteryPercent = rawBatteryPercent.takeIf { it in 0..100 },
        )

        /**
         * Converts microamps to milliamps, rejecting the sentinels devices return when
         * the fuel gauge is not wired up.
         *
         * `getIntProperty` documents Integer.MIN_VALUE for an unsupported property.
         * Dividing that by 1000 and printing it -- which the first version did -- puts
         * `battery: -2147483 mA` on screen, a two-thousand-amp discharge.
         */
        fun sanitiseCurrent(microAmps: Int): Int? {
            if (microAmps == Int.MIN_VALUE || microAmps == Int.MAX_VALUE) return null
            val milliAmps = microAmps / 1000
            return milliAmps.takeIf { kotlin.math.abs(it) <= PLAUSIBLE_MA_LIMIT }
        }
    }
}

private const val POLL_INTERVAL_MS = 2_000L

/**
 * The forecast window handed to `getThermalHeadroom`. Ten seconds is the shortest
 * horizon that is stable across devices, and matches the interval over which a
 * sustained interpolation load would actually build heat.
 */
private const val THERMAL_FORECAST_SECONDS = 10

/** Polls thermal headroom and instantaneous battery draw for the debug HUD. */
class SystemStatsCollector(context: Context) {

    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(PowerManager::class.java)
    private val batteryManager = appContext.getSystemService(BatteryManager::class.java)

    fun stats(): Flow<SystemStats> = flow {
        while (true) {
            emit(read())
            // getThermalHeadroom returns NaN if called more than once a second, so the
            // poll interval is a correctness constraint here, not just a battery one.
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun read(): SystemStats {
        val headroom = try {
            powerManager?.getThermalHeadroom(THERMAL_FORECAST_SECONDS) ?: Float.NaN
        } catch (e: IllegalArgumentException) {
            // Thrown when the forecast window is out of the range this device supports.
            Float.NaN
        } catch (e: UnsupportedOperationException) {
            // Some devices have no thermal sensor wired to this API at all.
            Float.NaN
        }

        val thermalStatus = try {
            powerManager?.currentThermalStatus
        } catch (e: UnsupportedOperationException) {
            null
        }

        return SystemStats.from(
            rawHeadroom = headroom,
            rawThermalStatus = thermalStatus,
            rawCurrentMicroAmps = batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                ?: Int.MIN_VALUE,
            rawBatteryPercent = batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?: -1,
        )
    }
}
