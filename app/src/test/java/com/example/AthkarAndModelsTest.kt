package com.example

import com.example.model.AlarmMode
import com.example.model.AthkarCategory
import com.example.model.AthkarRepository
import com.example.model.PrayerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AthkarAndModelsTest {

    @Test
    fun testAthkarCategoriesPopulated() {
        for (category in AthkarCategory.entries) {
            val items = AthkarRepository.getAthkar(category)
            assertTrue("Category ${category.name} should have items", items.isNotEmpty())

            for (item in items) {
                assertTrue("Item id must not be blank", item.id.isNotBlank())
                assertTrue("Arabic text must not be blank for ${item.id}", item.arabic.isNotBlank())
                assertTrue("Transliteration must not be blank for ${item.id}", item.transliteration.isNotBlank())
                assertTrue("English translation must not be blank for ${item.id}", item.english.isNotBlank())
                assertTrue("Target count must be at least 1 for ${item.id}", item.targetCount >= 1)
            }
        }
    }

    @Test
    fun testTasbeehPresets() {
        val presets = AthkarRepository.TASBEEH_PRESETS
        assertTrue("Tasbeeh presets should have at least 5 items", presets.size >= 5)

        for (preset in presets) {
            assertTrue("Preset English name must not be blank", preset.nameEnglish.isNotBlank())
            assertTrue("Preset Arabic name must not be blank", preset.nameArabic.isNotBlank())
            assertTrue("Preset target count must be positive (33, 34, 100)", preset.target in listOf(33, 34, 100))
        }
    }

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
