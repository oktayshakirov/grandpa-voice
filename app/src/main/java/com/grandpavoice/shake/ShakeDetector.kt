package com.grandpavoice.shake

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.grandpavoice.state.CompanionState
import kotlin.math.sqrt

/** Feeds the accelerometer into [ShakeLogic] and reports a live reading for the settings screen. */
class ShakeDetector(
    private val sensorManager: SensorManager,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private val logic = ShakeLogic()
    private var peakSinceReport = 0f
    private var lastReportMs = 0L
    private var running = false

    fun updateConfig(newConfig: ShakeLogic.Config) {
        logic.config = newConfig
    }

    /** @return false if the phone has no accelerometer. */
    fun start(): Boolean {
        if (running) return true
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return false
        running = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        return running
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        running = false
        logic.reset()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val nowMs = event.timestamp / 1_000_000L
        reportLive(g, nowMs)
        if (logic.onSample(g, nowMs)) onShake()
    }

    private fun reportLive(g: Float, nowMs: Long) {
        if (g > peakSinceReport) peakSinceReport = g
        if (nowMs - lastReportMs >= 150) {
            CompanionState.liveShakeG.value = peakSinceReport
            peakSinceReport = 0f
            lastReportMs = nowMs
        }
    }
}
