package com.example.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object QiblaHelper {
    const val KAABA_LATITUDE = 21.422487
    const val KAABA_LONGITUDE = 39.826206

    /**
     * Calculates the Qibla angle in degrees (0 to 360) from North.
     */
    fun calculateQiblaDirection(userLatitude: Double, userLongitude: Double): Double {
        val lat1 = Math.toRadians(userLatitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLon = Math.toRadians(KAABA_LONGITUDE - userLongitude)

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)

        return (bearingDeg + 360.0) % 360.0
    }

    /**
     * Calculates distance to Kaaba in kilometers using the Haversine formula.
     */
    fun calculateDistanceToKaaba(userLatitude: Double, userLongitude: Double): Double {
        val earthRadiusKm = 6371.0
        val dLat = Math.toRadians(KAABA_LATITUDE - userLatitude)
        val dLon = Math.toRadians(KAABA_LONGITUDE - userLongitude)

        val lat1 = Math.toRadians(userLatitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusKm * c
    }
}
