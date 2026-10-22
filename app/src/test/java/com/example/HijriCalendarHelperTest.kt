package com.example

import com.example.engine.HijriCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HijriCalendarHelperTest {

    @Test
    fun testHijriConversionAndMonthRange() {
        // Test date: April 15, 2026
        val hijri = HijriCalendarHelper.getHijriDate(2026, 4, 15)

        assertTrue("Hijri day must be in 1..30", hijri.day in 1..30)
        assertTrue("Hijri month must be in 1..12", hijri.month in 1..12)
        assertTrue("Hijri year must be ~1447-1448 AH", hijri.year in 1447..1449)
        assertTrue("Formatted English contains AH", hijri.formattedEnglish.endsWith("AH"))
        assertTrue("Formatted Arabic contains هـ", hijri.formattedArabic.endsWith("هـ"))
    }

    @Test
    fun testDayOffsetAdjustments() {
        val baseDate = HijriCalendarHelper.getHijriDate(2026, 5, 10, dayOffset = 0)
        val plus1 = HijriCalendarHelper.getHijriDate(2026, 5, 10, dayOffset = 1)
        val minus1 = HijriCalendarHelper.getHijriDate(2026, 5, 10, dayOffset = -1)

        // Day offsets should shift the date by 1 day
        assertEquals("Plus 1 offset should be base day + 1 (or roll to 1 if month end)", (baseDate.day % 30) + 1, plus1.day)
        assertEquals("Minus 1 offset should be base day - 1 (or 29/30 if month start)", if (baseDate.day == 1) 29 else baseDate.day - 1, minus1.day)
    }

    @Test
    fun testAllTwelveIslamicMonthsPresent() {
        val expectedEnglishMonths = listOf(
            "Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani",
            "Jumada al-Awwal", "Jumada al-Thani", "Rajab", "Sha'ban",
            "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
        )

        // Sample dates across the year to verify month names are valid
        for (m in 1..12) {
            val h = HijriCalendarHelper.getHijriDate(2026, m, 1)
            assertTrue("Month English should be in list: ${h.monthEnglish}", expectedEnglishMonths.contains(h.monthEnglish))
            assertTrue("Month Arabic must not be blank", h.monthArabic.isNotBlank())
        }
    }
}
