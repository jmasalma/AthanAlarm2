package com.example

import com.example.model.AlarmMode
import com.example.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerModelsTest {

    @Test
    fun testPrayerTypeEnglishAndArabicNames() {
        assertEquals("Fajr", PrayerType.FAJR.englishName)
        assertEquals("Sunrise", PrayerType.SUNRISE.englishName)
        assertEquals("Dhuhr", PrayerType.DHUHR.englishName)
        assertEquals("Asr", PrayerType.ASR.englishName)
        assertEquals("Maghrib", PrayerType.MAGHRIB.englishName)
        assertEquals("Isha", PrayerType.ISHA.englishName)

        assertEquals("الفجر", PrayerType.FAJR.arabicName)
        assertEquals("الشروق", PrayerType.SUNRISE.arabicName)
        assertEquals("الظهر", PrayerType.DHUHR.arabicName)
        assertEquals("العصر", PrayerType.ASR.arabicName)
        assertEquals("المغرب", PrayerType.MAGHRIB.arabicName)
        assertEquals("العشاء", PrayerType.ISHA.arabicName)
    }

    @Test
    fun testAlarmModeEnums() {
        val modes = AlarmMode.entries
        assertTrue(modes.contains(AlarmMode.ATHAN))
        assertTrue(modes.contains(AlarmMode.BEEP))
        assertTrue(modes.contains(AlarmMode.SILENT))
        assertTrue(modes.contains(AlarmMode.MUTED))
    }
}
