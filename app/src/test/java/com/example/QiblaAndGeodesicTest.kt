package com.example

import com.example.engine.QiblaHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaAndGeodesicTest {

    @Test
    fun testKaabaCoordinatesConstants() {
        assertEquals(21.422487, QiblaHelper.KAABA_LATITUDE, 0.0001)
        assertEquals(39.826206, QiblaHelper.KAABA_LONGITUDE, 0.0001)
    }

    @Test
    fun testQiblaBearingFromWorldwideLocations() {
        // Waterloo, ON, Canada (lat: 43.46, lon: -80.52)
        // Qibla over the great circle points Northeast (~53.7°)
        val qiblaWaterloo = QiblaHelper.calculateQiblaDirection(43.4643, -80.5204)
        assertTrue("Waterloo Qibla bearing should be ~50-58°", qiblaWaterloo in 50.0..58.0)

        // London, UK (lat: 51.5074, lon: -0.1278)
        // Qibla points Southeast (~118° - 121°)
        val qiblaLondon = QiblaHelper.calculateQiblaDirection(51.5074, -0.1278)
        assertTrue("London Qibla bearing should be ~118-121°", qiblaLondon in 118.0..121.0)

        // Tokyo, Japan (lat: 35.6762, lon: 139.6503)
        // Qibla points West-Northwest (~292° - 296°)
        val qiblaTokyo = QiblaHelper.calculateQiblaDirection(35.6762, 139.6503)
        assertTrue("Tokyo Qibla bearing should be ~292-296°", qiblaTokyo in 292.0..296.0)

        // Sydney, Australia (lat: -33.8688, lon: 151.2093)
        // Qibla points West-Northwest (~276° - 280°)
        val qiblaSydney = QiblaHelper.calculateQiblaDirection(-33.8688, 151.2093)
        assertTrue("Sydney Qibla bearing should be ~276-280°", qiblaSydney in 276.0..280.0)

        // Cairo, Egypt (lat: 30.0444, lon: 31.2357)
        // Qibla points Southeast (~134° - 138°)
        val qiblaCairo = QiblaHelper.calculateQiblaDirection(30.0444, 31.2357)
        assertTrue("Cairo Qibla bearing should be ~134-138°", qiblaCairo in 134.0..138.0)
    }

    @Test
    fun testKaabaDistanceCalculations() {
        // Distance from Mecca to Mecca should be 0 km
        val distanceMecca = QiblaHelper.calculateDistanceToKaaba(
            QiblaHelper.KAABA_LATITUDE,
            QiblaHelper.KAABA_LONGITUDE
        )
        assertEquals(0.0, distanceMecca, 0.01)

        // London to Mecca (~4790 km)
        val distLondon = QiblaHelper.calculateDistanceToKaaba(51.5074, -0.1278)
        assertTrue("London to Mecca distance should be ~4700-4900 km", distLondon in 4700.0..4900.0)

        // Waterloo to Mecca (~10,582 km)
        val distWaterloo = QiblaHelper.calculateDistanceToKaaba(43.4643, -80.5204)
        assertTrue("Waterloo to Mecca distance should be ~10,000-11,000 km", distWaterloo in 10000.0..11000.0)

        // Cairo to Mecca (~1280 km)
        val distCairo = QiblaHelper.calculateDistanceToKaaba(30.0444, 31.2357)
        assertTrue("Cairo to Mecca distance should be ~1200-1400 km", distCairo in 1200.0..1400.0)
    }
}
