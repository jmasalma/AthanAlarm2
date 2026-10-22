package com.example

import android.content.Context
import android.hardware.SensorManager
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import com.example.util.CompassSensorManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompassSensorRemapTest {

    @Test
    fun testRemapCoordinateSystemAdjustsAzimuthByExactDisplayRotation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = CompassSensorManager(context)

        // Identity rotation matrix (device flat, physical top pointing North, base azimuth = 0°)
        val identityMatrix = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )

        val baseOrientation = FloatArray(3)
        SensorManager.getOrientation(identityMatrix, baseOrientation)
        val baseAzimuthDeg = (Math.toDegrees(baseOrientation[0].toDouble()) + 360.0) % 360.0
        assertEquals(0.0, baseAzimuthDeg, 0.01)

        // In ROTATION_0 (Portrait): heading remains 0°
        val out0 = FloatArray(9)
        manager.remapMatrixForDisplayRotation(identityMatrix, out0, Surface.ROTATION_0)
        val orient0 = FloatArray(3)
        SensorManager.getOrientation(out0, orient0)
        val az0 = (Math.toDegrees(orient0[0].toDouble()) + 360.0) % 360.0
        assertEquals(0.0, az0, 0.01)

        // In ROTATION_90 (Landscape): forward heading is rotated by +90°
        val out90 = FloatArray(9)
        manager.remapMatrixForDisplayRotation(identityMatrix, out90, Surface.ROTATION_90)
        val orient90 = FloatArray(3)
        SensorManager.getOrientation(out90, orient90)
        val az90 = (Math.toDegrees(orient90[0].toDouble()) + 360.0) % 360.0
        assertEquals(90.0, az90, 0.01)

        // In ROTATION_180 (Reverse Portrait): forward heading is rotated by +180°
        val out180 = FloatArray(9)
        manager.remapMatrixForDisplayRotation(identityMatrix, out180, Surface.ROTATION_180)
        val orient180 = FloatArray(3)
        SensorManager.getOrientation(out180, orient180)
        val az180 = (Math.toDegrees(orient180[0].toDouble()) + 360.0) % 360.0
        assertEquals(180.0, az180, 0.01)

        // In ROTATION_270 (Reverse Landscape): forward heading is rotated by +270°
        val out270 = FloatArray(9)
        manager.remapMatrixForDisplayRotation(identityMatrix, out270, Surface.ROTATION_270)
        val orient270 = FloatArray(3)
        SensorManager.getOrientation(out270, orient270)
        val az270 = (Math.toDegrees(orient270[0].toDouble()) + 360.0) % 360.0
        assertEquals(270.0, az270, 0.01)
    }

    @Test
    fun testLandscapeModeCompensatesWhenPhoneIsHeldSideways() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = CompassSensorManager(context)

        // When a user holds a phone in landscape (rotated 90° CCW) pointing North,
        // the physical top of the phone (+Y) points West (270°):
        // [  0 -1  0 ]
        // [  1  0  0 ]
        // [  0  0  1 ]
        val sidewaysMatrix = floatArrayOf(
            0f, -1f, 0f,
            1f,  0f, 0f,
            0f,  0f, 1f
        )

        // Without coordinate remapping (as in portrait mode), the sensor calculates 270° (skewed by 90°):
        val unremappedOrientation = FloatArray(3)
        SensorManager.getOrientation(sidewaysMatrix, unremappedOrientation)
        val unremappedAz = (Math.toDegrees(unremappedOrientation[0].toDouble()) + 360.0) % 360.0
        assertEquals(270.0, unremappedAz, 0.01)

        // With coordinate remapping for ROTATION_90, the +90° rotation compensation aligns
        // the landscape forward heading directly with true North (0°):
        val remappedMatrix = FloatArray(9)
        manager.remapMatrixForDisplayRotation(sidewaysMatrix, remappedMatrix, Surface.ROTATION_90)
        val remappedOrientation = FloatArray(3)
        SensorManager.getOrientation(remappedMatrix, remappedOrientation)
        val remappedAz = (Math.toDegrees(remappedOrientation[0].toDouble()) + 360.0) % 360.0

        assertEquals("Remapped azimuth in landscape must be 0° North", 0.0, remappedAz, 0.01)
    }
}
