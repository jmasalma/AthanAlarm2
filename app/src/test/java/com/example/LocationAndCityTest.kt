package com.example

import com.example.model.City
import com.example.util.LocationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAndCityTest {

    @Test
    fun testDefaultCityIsWaterloo() {
        val waterloo = City.DEFAULT
        assertEquals("Waterloo", waterloo.name)
        assertEquals("Ontario, Canada", waterloo.country)
        assertEquals(43.4643, waterloo.latitude, 0.001)
        assertEquals(-80.5204, waterloo.longitude, 0.001)
        assertEquals(329.0, waterloo.elevation, 0.01)
        assertEquals("America/Toronto", waterloo.timeZoneId)
    }

    @Test
    fun testCityDatabaseIntegrity() {
        assertTrue("Cities database must contain at least 50 cities", City.CITIES.size >= 50)

        for (city in City.CITIES) {
            assertTrue("City name must not be blank", city.name.isNotBlank())
            assertTrue("City country must not be blank", city.country.isNotBlank())
            assertTrue("Latitude must be between -90 and 90: ${city.name}", city.latitude in -90.0..90.0)
            assertTrue("Longitude must be between -180 and 180: ${city.name}", city.longitude in -180.0..180.0)
            assertTrue("Timezone ID must be valid", city.timeZoneId.isNotBlank())
        }

        // Verify key major cities are present
        val expected = listOf("Waterloo", "Toronto", "Montreal", "Ottawa", "Vancouver", "Makkah", "Madinah", "London", "Cairo")
        for (name in expected) {
            val found = City.CITIES.find { it.name.equals(name, ignoreCase = true) }
            assertNotNull("City $name should exist in database", found)
        }
    }

    @Test
    fun testFindClosestCityNearWaterloo() {
        // Approximate coordinates for University of Waterloo / Kitchener area
        val closest = LocationHelper.findClosestCity(43.4723, -80.5449)
        assertEquals("Waterloo", closest.name)
    }

    @Test
    fun testFindClosestCityNearMecca() {
        // Approximate coordinates in Mecca (Makkah)
        val closest = LocationHelper.findClosestCity(21.42, 39.83)
        assertEquals("Makkah", closest.name)
    }

    @Test
    fun testFindClosestCityFarCoordinatesReturnsCurrentLocation() {
        // Mid-Atlantic Ocean (0.0, -30.0)
        val result = LocationHelper.findClosestCity(0.0, -30.0)
        assertEquals("Current Location", result.name)
        assertTrue(result.country.startsWith("GPS"))
    }
}
