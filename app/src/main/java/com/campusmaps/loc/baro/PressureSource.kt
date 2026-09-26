package com.campusmaps.loc.baro

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/** Air pressure readings in hPa, with the reading's time in ms (elapsed-realtime clock). */
interface PressureSource {
    /** True when the device has a barometer. */
    val available: Boolean

    /** Starts delivering readings (on the main thread). Returns false when there is no barometer. */
    fun start(onReading: (hPa: Double, timeMs: Long) -> Unit): Boolean

    fun stop()
}

/** The phone's barometer (Sensor.TYPE_PRESSURE) at SENSOR_DELAY_UI, about 15 readings a second. */
class SensorPressureSource(context: Context) : PressureSource {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private var listener: SensorEventListener? = null

    override val available: Boolean get() = sensor != null

    override fun start(onReading: (hPa: Double, timeMs: Long) -> Unit): Boolean {
        val s = sensor ?: return false
        stop()
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) = onReading(e.values[0].toDouble(), e.timestamp / 1_000_000)
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        listener = l
        return sm.registerListener(l, s, SensorManager.SENSOR_DELAY_UI)
    }

    override fun stop() {
        listener?.let { sm.unregisterListener(it) }
        listener = null
    }

    /** Same clock as the readings' times. */
    fun nowMs(): Long = SystemClock.elapsedRealtime()
}
