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

    @Test
    fun testAlbertaPermanentTimeEffectiveOffset() {
        val calgary = City.CITIES.first { it.name.equals("Calgary", ignoreCase = true) }
        val edmonton = City.CITIES.first { it.name.equals("Edmonton", ignoreCase = true) }

        // Base timezone must now be -6.0 (ABT)
        assertEquals(-6.0, calgary.timezone, 0.001)
        assertEquals(-6.0, edmonton.timezone, 0.001)

        // Date on or after November 1, 2026 (effective date of permanent Alberta Time)
        val calNov1 = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.NOVEMBER, 1, 12, 0, 0)
        }
        assertEquals(-6.0, calgary.getEffectiveTimezone(calNov1), 0.001)
        assertEquals(-6.0, edmonton.getEffectiveTimezone(calNov1), 0.001)

        // Winter date in December 2026: must remain -6.0, NOT fall back to -7.0
        val calDec15 = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.DECEMBER, 15, 12, 0, 0)
        }
        assertEquals(-6.0, calgary.getEffectiveTimezone(calDec15), 0.001)
        assertEquals(-6.0, edmonton.getEffectiveTimezone(calDec15), 0.001)

        // Custom GPS coordinate in Alberta (e.g. Red Deer: 52.2681, -113.8112)
        val gpsAlberta = City(
            name = "GPS (52.27°, -113.81°)",
            country = "Alberta, Canada",
            latitude = 52.2681,
            longitude = -113.8112,
            timezone = -7.0 // Even if set with old -7.0
        )
        assertTrue(gpsAlberta.isAlberta())
        assertEquals(-6.0, gpsAlberta.getEffectiveTimezone(calNov1), 0.001)
        assertEquals(-6.0, gpsAlberta.getEffectiveTimezone(calDec15), 0.001)
    }

    @Test
    fun testAlbertaPrayerTimeCalculationOnNov12026() {
        val edmonton = City.CITIES.first { it.name.equals("Edmonton", ignoreCase = true) }
        val calNov1 = java.util.Calendar.getInstance().apply {
            clear()
            set(2026, java.util.Calendar.NOVEMBER, 1, 12, 0, 0)
        }

        val result = com.example.engine.PrayerCalculationEngine.calculate(
            date = calNov1,
            city = edmonton
        )

        // In Edmonton at lon ~ -113.49° on Nov 1 with UTC-6 (ABT):
        // Mean solar noon is at ~13:34 (since -113.5° is 23.5° west of the 90°W meridian).
        // With equation of time (~-16 min on Nov 1), solar transit (Dhuhr) is at ~13:18 (1:18 PM).
        // If it were wrongly calculated with UTC-7 (MST), Dhuhr would have been at 12:18 PM!
        assertEquals("Dhuhr hour should be 13 (1:18 PM) under ABT (UTC-6)", 13, result.dhuhr.first)
        assertTrue("Dhuhr minute should be between 15 and 25", result.dhuhr.second in 15..25)
    }
}
