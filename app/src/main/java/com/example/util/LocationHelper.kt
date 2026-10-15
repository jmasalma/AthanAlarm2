package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.model.City
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null

        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine { continuation ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            continuation.resume(loc)
                        } else {
                            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                            val gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            val netLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                            continuation.resume(gpsLoc ?: netLoc)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun findClosestCity(latitude: Double, longitude: Double): City {
        var closest = City.CITIES.first()
        var minDistance = Double.MAX_VALUE

        for (city in City.CITIES) {
            val d = hypot(city.latitude - latitude, city.longitude - longitude)
            if (d < minDistance) {
                minDistance = d
                closest = city
            }
        }

        // If reasonably close to a known city (< 0.8 degrees ~ 80km), return named city,
        // otherwise return a custom GPS location with device's local timezone.
        val tzHours = TimeZone.getDefault().rawOffset / (1000.0 * 60.0 * 60.0)
        return if (minDistance < 0.8) {
            closest
        } else {
            City(
                name = "Current Location",
                country = "GPS (%.2f°, %.2f°)".format(latitude, longitude),
                latitude = latitude,
                longitude = longitude,
                timezone = tzHours
            )
        }
    }
}
