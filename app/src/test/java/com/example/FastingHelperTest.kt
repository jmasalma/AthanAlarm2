package com.example

import com.example.data.AppSettings
import com.example.data.RamadanMode
import com.example.engine.FastingHelper
import com.example.engine.HijriCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FastingHelperTest {

    @Test
    fun testFastingModeOffReturnsNull() {
        val settings = AppSettings(ramadanMode = RamadanMode.OFF)
        assertFalse(FastingHelper.isFastingActive(settings))

        val status = FastingHelper.calculateFastingStatus(
            settings = settings,
            todayFajr = 5 to 0,
            todayMaghrib = 18 to 0,
            tomorrowFajr = 5 to 1
        )
        assertNull(status)
    }

    @Test
    fun testFastingDaysModeAlwaysActive() {
        val settings = AppSettings(ramadanMode = RamadanMode.FASTING_DAYS)
        assertTrue(FastingHelper.isFastingActive(settings))

        val status = FastingHelper.calculateFastingStatus(
            settings = settings,
            todayFajr = 5 to 0,
            todayMaghrib = 18 to 0,
            tomorrowFajr = 5 to 1
        )
        assertNotNull(status)
        assertTrue(status!!.isActive)
    }

    @Test
    fun testRamadanAutoDetection() {
        // Find a day when Hijri month is 9 (Ramadan)
        val todayHijri = HijriCalendarHelper.getTodayHijri(0)
        val settingsAuto = AppSettings(ramadanMode = RamadanMode.AUTO, hijriDayOffset = 0)

        if (todayHijri.month == 9) {
            assertTrue("Should be active in Ramadan", FastingHelper.isFastingActive(settingsAuto))
        } else {
            assertFalse("Should be inactive outside Ramadan", FastingHelper.isFastingActive(settingsAuto))
        }
    }

    @Test
    fun testBeforeFajrMilestoneIsImsakToday() {
        val settings = AppSettings(ramadanMode = RamadanMode.FASTING_DAYS)
        // 4:30 AM (before Fajr at 5:00 AM)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 4)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
        }

        val status = FastingHelper.calculateFastingStatus(
            settings = settings,
            todayFajr = 5 to 0,
            todayMaghrib = 18 to 15,
            tomorrowFajr = 5 to 2,
            now = cal
        )

        assertNotNull(status)
        assertEquals("Imsak", status!!.milestoneName)
        assertFalse(status.isIftar)
        assertEquals(5, status.targetHour)
        assertEquals(0, status.targetMinute)
        assertEquals(30 * 60L, status.secondsRemaining)
    }

    @Test
    fun testBetweenFajrAndMaghribMilestoneIsIftarToday() {
        val settings = AppSettings(ramadanMode = RamadanMode.FASTING_DAYS)
        // 1:00 PM (13:00) (between Fajr 5:00 AM and Maghrib 6:00 PM / 18:00)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        val status = FastingHelper.calculateFastingStatus(
            settings = settings,
            todayFajr = 5 to 0,
            todayMaghrib = 18 to 0,
            tomorrowFajr = 5 to 1,
            now = cal
        )

        assertNotNull(status)
        assertEquals("Iftar", status!!.milestoneName)
        assertTrue(status.isIftar)
        assertEquals(18, status.targetHour)
        assertEquals(0, status.targetMinute)
        assertEquals(5 * 3600L, status.secondsRemaining) // 13:00 to 18:00 is 5 hours
    }

    @Test
    fun testAfterMaghribMilestoneIsImsakTomorrow() {
        val settings = AppSettings(ramadanMode = RamadanMode.FASTING_DAYS)
        // 9:00 PM (21:00) (after Maghrib 18:00, before tomorrow's Fajr 5:00)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        val status = FastingHelper.calculateFastingStatus(
            settings = settings,
            todayFajr = 5 to 0,
            todayMaghrib = 18 to 0,
            tomorrowFajr = 5 to 5,
            now = cal
        )

        assertNotNull(status)
        assertEquals("Imsak", status!!.milestoneName)
        assertFalse(status.isIftar)
        assertEquals(5, status.targetHour)
        assertEquals(5, status.targetMinute)
        // From 21:00 to 24:00 (3 hours) + 00:00 to 05:05 (5h 5m) = 8h 5m = 29100 seconds
        assertEquals(29100L, status.secondsRemaining)
    }
}
