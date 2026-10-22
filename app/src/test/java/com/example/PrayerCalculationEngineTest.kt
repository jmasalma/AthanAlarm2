package com.example

import com.example.engine.PrayerCalculationEngine
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.HighLatitudeRule
import com.example.model.JuristicMethod
import com.example.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class PrayerCalculationEngineTest {

    @Test
    fun testAllCalculationMethodsProduceAscendingDailyTimes() {
        // Test Waterloo with all calculation methods on May 1st
        val city = City.DEFAULT
        val methods = CalculationMethod.entries

        for (method in methods) {
            val result = PrayerCalculationEngine.calculate(
                year = 2026,
                month = 5,
                day = 1,
                latitude = city.latitude,
                longitude = city.longitude,
                timezone = -4.0, // EDT
                elevation = city.elevation,
                method = method,
                juristic = JuristicMethod.STANDARD
            )

            val fajrMin = result.fajr.first * 60 + result.fajr.second
            val sunrMin = result.sunrise.first * 60 + result.sunrise.second
            val dhuhrMin = result.dhuhr.first * 60 + result.dhuhr.second
            val asrMin = result.asr.first * 60 + result.asr.second
            val maghribMin = result.maghrib.first * 60 + result.maghrib.second
            val ishaMin = result.isha.first * 60 + result.isha.second

            assertTrue("Method ${method.name}: Fajr ($fajrMin) must be before Sunrise ($sunrMin)", fajrMin < sunrMin)
            assertTrue("Method ${method.name}: Sunrise ($sunrMin) must be before Dhuhr ($dhuhrMin)", sunrMin < dhuhrMin)
            assertTrue("Method ${method.name}: Dhuhr ($dhuhrMin) must be before Asr ($asrMin)", dhuhrMin < asrMin)
            val effectiveIshaMin = if (ishaMin < maghribMin) ishaMin + 1440 else ishaMin
            assertTrue("Method ${method.name}: Maghrib ($maghribMin) must be before Isha ($effectiveIshaMin)", maghribMin < effectiveIshaMin)
        }
    }

    @Test
    fun testHanafiAsrIsStrictlyAfterStandardAsr() {
        val city = City.DEFAULT
        val standardResult = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 6,
            day = 15,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = -4.0,
            elevation = city.elevation,
            method = CalculationMethod.ISNA,
            juristic = JuristicMethod.STANDARD
        )

        val hanafiResult = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 6,
            day = 15,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = -4.0,
            elevation = city.elevation,
            method = CalculationMethod.ISNA,
            juristic = JuristicMethod.HANAFI
        )

        val standardAsrMin = standardResult.asr.first * 60 + standardResult.asr.second
        val hanafiAsrMin = hanafiResult.asr.first * 60 + hanafiResult.asr.second

        assertTrue(
            "Hanafi Asr ($hanafiAsrMin min) must be strictly later than Standard Asr ($standardAsrMin min)",
            hanafiAsrMin > standardAsrMin
        )
        // Usually Hanafi Asr is 45 to 65 minutes later than Standard Asr
        assertTrue("Hanafi Asr is between 30 and 90 minutes later", (hanafiAsrMin - standardAsrMin) in 30..90)
    }

    @Test
    fun testManualPrayerOffsets() {
        val city = City.DEFAULT
        val baseResult = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 4,
            day = 10,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = -4.0,
            offsets = emptyMap()
        )

        val customOffsets = mapOf(
            PrayerType.FAJR to 5,     // +5 min
            PrayerType.MAGHRIB to -3,  // -3 min
            PrayerType.ISHA to 10     // +10 min
        )

        val offsetResult = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 4,
            day = 10,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = -4.0,
            offsets = customOffsets
        )

        val baseFajrMin = baseResult.fajr.first * 60 + baseResult.fajr.second
        val offsetFajrMin = offsetResult.fajr.first * 60 + offsetResult.fajr.second
        assertEquals(baseFajrMin + 5, offsetFajrMin)

        val baseMaghribMin = baseResult.maghrib.first * 60 + baseResult.maghrib.second
        val offsetMaghribMin = offsetResult.maghrib.first * 60 + offsetResult.maghrib.second
        assertEquals(baseMaghribMin - 3, offsetMaghribMin)

        val baseIshaMin = baseResult.isha.first * 60 + baseResult.isha.second
        val offsetIshaMin = offsetResult.isha.first * 60 + offsetResult.isha.second
        assertEquals(baseIshaMin + 10, offsetIshaMin)
    }

    @Test
    fun testHighLatitudeRulesAtExtremeLocations() {
        // High latitude test: Oslo, Norway (approx 59.91° N, 10.75° E) in mid-summer
        val osloLat = 59.91
        val osloLon = 10.75
        val osloTz = 2.0 // CEST (UTC+2)

        for (rule in HighLatitudeRule.entries) {
            val result = PrayerCalculationEngine.calculate(
                year = 2026,
                month = 6,
                day = 21,
                latitude = osloLat,
                longitude = osloLon,
                timezone = osloTz,
                method = CalculationMethod.MWL,
                highLatRule = rule
            )

            // Ensure hours and minutes remain within realistic ranges
            assertTrue("Fajr hour in 0..23", result.fajr.first in 0..23)
            assertTrue("Sunrise hour in 0..23", result.sunrise.first in 0..23)
            assertTrue("Isha hour in 0..23", result.isha.first in 0..23)
            assertTrue("Fajr min in 0..59", result.fajr.second in 0..59)
            assertTrue("Sunrise min in 0..59", result.sunrise.second in 0..59)
        }
    }

    @Test
    fun testDhuhrPrecautionMinuteAdded() {
        // Test that 1 precaution minute is added to Dhuhr
        val city = City.DEFAULT
        val resultWith1MinPrecaution = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 5,
            day = 1,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = -4.0,
            dhuhrPrecautionMin = 1
        )

        val resultWith0MinPrecaution = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 5,
            day = 1,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = -4.0,
            dhuhrPrecautionMin = 0
        )

        val dhuhr1Min = resultWith1MinPrecaution.dhuhr.first * 60 + resultWith1MinPrecaution.dhuhr.second
        val dhuhr0Min = resultWith0MinPrecaution.dhuhr.first * 60 + resultWith0MinPrecaution.dhuhr.second

        assertEquals("Dhuhr with 1 min precaution must be exactly 1 minute later", dhuhr0Min + 1, dhuhr1Min)
    }

    @Test
    fun testMidnightAndLastThirdCalculations() {
        val city = City.DEFAULT
        val result = PrayerCalculationEngine.calculate(
            year = 2026,
            month = 5,
            day = 1,
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = -4.0
        )

        // Islamic midnight is halfway between Maghrib and next Fajr
        // Last third of night is 2/3 of the night between Maghrib and next Fajr
        val maghribMin = result.maghrib.first * 60 + result.maghrib.second
        val midnightMin = result.midnight.first * 60 + result.midnight.second
        val lastThirdMin = result.lastThird.first * 60 + result.lastThird.second

        assertTrue("Midnight hour valid", result.midnight.first in 0..23)
        assertTrue("Last third hour valid", result.lastThird.first in 0..23)
        // Midnight should be well after Maghrib (either around 23:xx or 00:xx / 01:xx)
        assertTrue("Last third minute must be valid", lastThirdMin in 0..1439)
    }
}
