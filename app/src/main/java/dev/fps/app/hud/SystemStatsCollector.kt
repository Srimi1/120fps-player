package dev.fps.app.hud

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class SystemStats(
    val thermalHeadroom: Float = 0f,
    val batteryMa: Int = 0,
)

private const val POLL_INTERVAL_MS = 2_000L
private const val THERMAL_FORECAST_SECONDS = 10

/** Polls thermal headroom and instantaneous battery draw for the debug HUD. */
class SystemStatsCollector(context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)

    fun stats(): Flow<SystemStats> = flow {
        while (true) {
            val headroom = try {
                powerManager.getThermalHeadroom(THERMAL_FORECAST_SECONDS)
            } catch (e: IllegalArgumentException) {
                Float.NaN
            }
            val currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            emit(SystemStats(thermalHeadroom = headroom, batteryMa = currentUa / 1000))
            delay(POLL_INTERVAL_MS)
        }
    }
}
