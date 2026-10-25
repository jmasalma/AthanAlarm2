package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettingsRepository
import com.example.model.AlarmMode
import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.PrayerType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

import androidx.datastore.preferences.core.edit
import com.example.data.dataStore
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AthanSettingsRepositoryTest {

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.dataStore.edit { it.clear() }
    }

    @Test
    fun testDefaultSettingsFlow() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppSettingsRepository(context)
        val settings = repository.settingsFlow.first()

        assertEquals("Waterloo", settings.city.name)
        assertEquals(CalculationMethod.ISNA, settings.calculationMethod)
        assertFalse("Pre-prayer reminder must be disabled by default", settings.prePrayerReminderEnabled)
        assertEquals(20, settings.prePrayerReminderMinutes)
        assertEquals("chime", settings.athanSound)
        assertFalse("Chromecast casting must be disabled by default", settings.castEnabled)
        assertEquals(null, settings.castDeviceId)
        assertEquals(null, settings.castDeviceName)
        assertEquals("athan", settings.castAlertSound)

        // Sunrise should default to MUTED; all other daily prayers should default to ATHAN
        assertEquals(AlarmMode.MUTED, settings.alarmModes[PrayerType.SUNRISE])
        assertEquals(AlarmMode.ATHAN, settings.alarmModes[PrayerType.FAJR])
        assertEquals(AlarmMode.ATHAN, settings.alarmModes[PrayerType.DHUHR])
        assertEquals(AlarmMode.ATHAN, settings.alarmModes[PrayerType.ASR])
        assertEquals(AlarmMode.ATHAN, settings.alarmModes[PrayerType.MAGHRIB])
        assertEquals(AlarmMode.ATHAN, settings.alarmModes[PrayerType.ISHA])
    }

    @Test
    fun testUpdateCityAndCalculationMethod() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppSettingsRepository(context)

        val toronto = City.CITIES.first { it.name == "Toronto" }
        repository.updateCity(toronto)
        repository.updateCalculationMethod(CalculationMethod.MWL)

        val updated = repository.settingsFlow.first()
        assertEquals("Toronto", updated.city.name)
        assertEquals(CalculationMethod.MWL, updated.calculationMethod)
    }

    @Test
    fun testUpdateAthanSoundAndPreReminder() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppSettingsRepository(context)

        repository.updateAthanSound("athan")
        repository.updatePreReminder(enabled = true, minutes = 15)

        val updated = repository.settingsFlow.first()
        assertEquals("athan", updated.athanSound)
        assertTrue(updated.prePrayerReminderEnabled)
        assertEquals(15, updated.prePrayerReminderMinutes)
    }

    @Test
    fun testUpdatePrayerAlarmModeAndOffset() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppSettingsRepository(context)

        repository.updateAlarmMode(PrayerType.FAJR, AlarmMode.BEEP)
        repository.updateOffset(PrayerType.FAJR, 3)

        val updated = repository.settingsFlow.first()
        assertEquals(AlarmMode.BEEP, updated.alarmModes[PrayerType.FAJR])
        assertEquals(3, updated.offsets[PrayerType.FAJR])
    }

    @Test
    fun testUpdateCastPreferences() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppSettingsRepository(context)

        repository.updateCastEnabled(true)
        repository.updateCastDevice("device_living_room_1", "Living Room Nest Mini")
        repository.updateCastAlertSound("chime")

        val updated = repository.settingsFlow.first()
        assertTrue(updated.castEnabled)
        assertEquals("device_living_room_1", updated.castDeviceId)
        assertEquals("Living Room Nest Mini", updated.castDeviceName)
        assertEquals("chime", updated.castAlertSound)

        // Clear device
        repository.updateCastDevice(null, null)
        val cleared = repository.settingsFlow.first()
        assertEquals(null, cleared.castDeviceId)
        assertEquals(null, cleared.castDeviceName)
    }
}
