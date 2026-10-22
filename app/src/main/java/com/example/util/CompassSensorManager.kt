package com.example.util

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager

/**
 * Manages device compass sensors (Rotation Vector, with Accelerometer + Magnetometer fallback)
 * and accurately remaps the coordinate system for device display rotation (portrait, landscape,
 * reverse landscape, and reverse portrait) so North and Qibla remain true to the user's forward heading.
 */
class CompassSensorManager(
    private val context: Context,
    var displayRotationProvider: (() -> Int)? = null
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private val lastAccelerometer = FloatArray(3)
    private val lastMagnetometer = FloatArray(3)
    private var lastAccelerometerSet = false
    private var lastMagnetometerSet = false

    private var currentAzimuth = 0f
    private val alpha = 0.15f // Low-pass filter smoothing coefficient

    var onAzimuthChanged: ((Float) -> Unit)? = null

    fun start() {
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Resolves the current display rotation (Surface.ROTATION_0, ROTATION_90, etc.)
     */
    fun getDisplayRotation(): Int {
        displayRotationProvider?.let { return it.invoke() }
        return try {
            val activity = context as? Activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.rotation
                    ?: activity?.display?.rotation
                    ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
                    ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                val windowManager = activity?.windowManager
                    ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    /**
     * Transforms the sensor coordinate system to align with the current display orientation.
     * When the user rotates their phone into landscape (90° clockwise display rotation),
     * the top of the screen points along the phone's physical -X axis. Remapping aligns the
     * forward vector to the user's line of sight regardless of device orientation.
     */
    fun remapMatrixForDisplayRotation(inMatrix: FloatArray, outMatrix: FloatArray, rotation: Int = getDisplayRotation()) {
        when (rotation) {
            Surface.ROTATION_90 -> {
                SensorManager.remapCoordinateSystem(
                    inMatrix,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    outMatrix
                )
            }
            Surface.ROTATION_180 -> {
                SensorManager.remapCoordinateSystem(
                    inMatrix,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    outMatrix
                )
            }
            Surface.ROTATION_270 -> {
                SensorManager.remapCoordinateSystem(
                    inMatrix,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    outMatrix
                )
            }
            else -> {
                // Surface.ROTATION_0 (Natural Portrait)
                System.arraycopy(inMatrix, 0, outMatrix, 0, 9)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            remapMatrixForDisplayRotation(rotationMatrix, remappedMatrix)
            SensorManager.getOrientation(remappedMatrix, orientationAngles)
            val azimuthRad = orientationAngles[0]
            val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0
            smoothAndNotify(azimuthDeg.toFloat())
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.size)
            lastAccelerometerSet = true
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.size)
            lastMagnetometerSet = true
        }

        if (lastAccelerometerSet && lastMagnetometerSet) {
            val success = SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer)
            if (success) {
                remapMatrixForDisplayRotation(rotationMatrix, remappedMatrix)
                SensorManager.getOrientation(remappedMatrix, orientationAngles)
                val azimuthRad = orientationAngles[0]
                val azimuthDeg = (Math.toDegrees(azimuthRad.toDouble()) + 360.0) % 360.0
                smoothAndNotify(azimuthDeg.toFloat())
            }
        }
    }

    private fun smoothAndNotify(newAzimuth: Float) {
        // Circular smoothing to prevent 0-360 flip jitter
        val diff = (newAzimuth - currentAzimuth + 180f + 360f) % 360f - 180f
        currentAzimuth = (currentAzimuth + alpha * diff + 360f) % 360f
        onAzimuthChanged?.invoke(currentAzimuth)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }
}
