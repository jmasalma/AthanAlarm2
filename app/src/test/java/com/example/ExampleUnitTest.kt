package com.example

import com.example.data.AppSettings
import com.example.engine.HijriCalendarHelper
import com.example.engine.PrayerCalculationEngine
import com.example.engine.QiblaHelper
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.JuristicMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testPrayerCalculationForWaterlooWithElevation() {
        val waterloo = City.DEFAULT
        assertEquals("Waterloo", waterloo.name)
        assertEquals(329.0, waterloo.elevation, 0.01)

        val result = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 5,
            day = 1,
            latitude = waterloo.latitude,
            longitude = waterloo.longitude,
            timezone = waterloo.timezone,
            elevation = waterloo.elevation,
            method = CalculationMethod.ISNA,
            juristic = JuristicMethod.STANDARD
        )

        // All prayer times must be within 0..23 hours and 0..59 minutes
        assertTrue("Fajr hour valid", result.fajr.first in 0..23)
        assertTrue("Sunrise hour valid", result.sunrise.first in 0..23)
        assertTrue("Dhuhr hour valid", result.dhuhr.first in 0..23)
        assertTrue("Asr hour valid", result.asr.first in 0..23)
        assertTrue("Maghrib hour valid", result.maghrib.first in 0..23)
        assertTrue("Isha hour valid", result.isha.first in 0..23)

        // Chronological order: Fajr < Sunrise < Dhuhr < Asr < Maghrib < Isha
        val fajrMin = result.fajr.first * 60 + result.fajr.second
        val sunrMin = result.sunrise.first * 60 + result.sunrise.second
        val dhuhrMin = result.dhuhr.first * 60 + result.dhuhr.second
        val asrMin = result.asr.first * 60 + result.asr.second
        val maghribMin = result.maghrib.first * 60 + result.maghrib.second
        val ishaMin = result.isha.first * 60 + result.isha.second

        assertTrue("Fajr before Sunrise", fajrMin < sunrMin)
        assertTrue("Sunrise before Dhuhr", sunrMin < dhuhrMin)
        assertTrue("Dhuhr before Asr", dhuhrMin < asrMin)
        assertTrue("Asr before Maghrib", asrMin < maghribMin)
        assertTrue("Maghrib before Isha", maghribMin < ishaMin)
    }

    @Test
    fun testDefaultSettings() {
        val settings = AppSettings()
        assertEquals("Waterloo", settings.city.name)
        assertEquals(CalculationMethod.ISNA, settings.calculationMethod)
        assertFalse(settings.prePrayerReminderEnabled)
        assertEquals(20, settings.prePrayerReminderMinutes)
    }

    @Test
    fun testQiblaCalculation() {
        // From Waterloo to Mecca (~21.42N, 39.82E)
        // From Waterloo Ontario (lat 43.46, lon -80.52), Qibla bearing is roughly ~55-60 degrees (North-East over great circle)
        val qiblaWaterloo = QiblaHelper.calculateQiblaDirection(43.4643, -80.5204)
        assertTrue("Qibla from Waterloo is roughly 50 to 65 degrees", qiblaWaterloo in 50.0..65.0)

        val distance = QiblaHelper.calculateDistanceToKaaba(43.4643, -80.5204)
        assertTrue("Distance Waterloo to Mecca is roughly 10,000-11,000 km", distance in 9500.0..11500.0)
    }

    @Test
    fun testHijriCalendarConversion() {
        val hijri = HijriCalendarHelper.getHijriDate(2026, 4, 15)
        assertTrue("Hijri year is ~1447 or 1448 AH", hijri.year in 1447..1449)
        assertTrue("Hijri day is 1..30", hijri.day in 1..30)
        assertNotNull(hijri.monthEnglish)
        assertNotNull(hijri.monthArabic)
    }

    @Test
    fun testCityDatabaseIncludesWaterlooAndCanadianCities() {
        assertTrue("City list is populated", City.CITIES.size >= 50)
        val waterloo = City.CITIES.find { it.name == "Waterloo" }
        assertNotNull("Waterloo is in city list", waterloo)
        val toronto = City.CITIES.find { it.name == "Toronto" }
        assertNotNull("Toronto is in city list", toronto)
    }

    @Test
    fun testWaterlooOctoberPrayerTimesAndDst() {
        val waterloo = City.DEFAULT
        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.OCTOBER, 15, 12, 0, 0)
        }

        val effectiveTz = waterloo.getEffectiveTimezone(cal)
        assertEquals("Waterloo in October should be EDT (UTC-4.0)", -4.0, effectiveTz, 0.01)

        val result = PrayerCalculationEngine.calculate(
            date = cal,
            city = waterloo,
            method = CalculationMethod.ISNA,
            juristic = JuristicMethod.STANDARD
        )

        // Dhuhr in Waterloo on Oct 15 is around 1:08 - 1:09 PM (13:08 or 13:09)
        assertEquals("Dhuhr hour must be 13 (1 PM)", 13, result.dhuhr.first)
        assertTrue("Dhuhr minute must be 8 or 9 (1:08 or 1:09 PM)", result.dhuhr.second in 8..9)

        // Fajr ~ 6:17 AM, Sunrise ~ 7:32 AM, Asr ~ 4:10 PM (16:10), Maghrib ~ 6:42 PM (18:42), Isha ~ 7:58 PM (19:58)
        assertEquals(6, result.fajr.first)
        assertEquals(7, result.sunrise.first)
        assertEquals(16, result.asr.first)
        assertEquals(18, result.maghrib.first)
        assertEquals(19, result.isha.first)
    }
}
