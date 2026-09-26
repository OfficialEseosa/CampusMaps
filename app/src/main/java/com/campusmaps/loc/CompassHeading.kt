package com.campusmaps.loc

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The phone's compass bearing of the direction the back camera looks (true north, degrees clockwise), for automatic
 * placement. Uses the rotation vector (gyro + magnetometer + accelerometer, referenced to magnetic north), or the
 * geomagnetic rotation vector when the phone has no gyro, and adds the magnetic declination at [lat], [lng].
 *
 * The camera looks along the device's -Z. When the phone is nearly flat (looking at the floor) that has no useful
 * horizontal part, so the top of the screen (+Y) is used, the same rule the AR layer uses for ARCore's heading.
 */
class CompassHeading(context: Context, lat: Double, lng: Double) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sm?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val declinationDeg: Double =
        GeomagneticField(lat.toFloat(), lng.toFloat(), 300f, System.currentTimeMillis()).declination.toDouble()
    private val r = FloatArray(9)

    /** Latest true bearing of the camera, and when it was read (ms, wall clock). */
    @Volatile var bearingDeg: Double? = null
        private set
    @Volatile var timeMs: Long = 0L
        private set

    val available: Boolean get() = sensor != null

    fun start(): Boolean {
        val s = sensor ?: return false
        Log.i(TAG, "compass ${s.name}, declination %.1f deg".format(declinationDeg))
        return sm!!.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() { sm?.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(r, e.values)
        // r maps device axes to (east, north, up). Column 2 is device +Z, column 1 device +Y.
        var east = -r[2].toDouble(); var north = -r[5].toDouble()
        if (hypot(east, north) < 0.3) { east = r[1].toDouble(); north = r[4].toDouble() }
        bearingDeg = AutoPlace.wrap360(Math.toDegrees(atan2(east, north)) + declinationDeg)
        timeMs = System.currentTimeMillis()
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {
        Log.i(TAG, "compass accuracy $accuracy")
    }

    private companion object { const val TAG = "ArGuidanceView" }
}
