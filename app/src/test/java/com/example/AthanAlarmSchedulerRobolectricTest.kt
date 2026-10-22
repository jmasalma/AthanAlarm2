package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.AthanAlarmScheduler
import com.example.data.AppSettings
import com.example.model.AlarmMode
import com.example.model.PrayerType
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AthanAlarmSchedulerRobolectricTest {

    @Test
    fun testScheduleNextAlarmReturnsFutureTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val defaultSettings = AppSettings()

        val scheduled = AthanAlarmScheduler.scheduleNextAlarm(context, defaultSettings)
        assertNotNull("Scheduled alarm info must not be null with default settings", scheduled)

        val triggerTime = scheduled!!.triggerTimeMillis
        val currentTime = System.currentTimeMillis()

        assertTrue(
            "Alarm trigger time ($triggerTime) must be strictly in the future (> $currentTime)",
            triggerTime > currentTime
        )
        // Sunrise should never be scheduled because its default alarmMode is MUTED
        assertTrue("Scheduled prayer must not be Sunrise", scheduled.prayerType != PrayerType.SUNRISE)
    }

    @Test
    fun testScheduleNextAlarmWhenAllMutedReturnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val allMutedSettings = AppSettings(
            alarmModes = PrayerType.entries.associateWith { AlarmMode.MUTED }
        )

        val scheduled = AthanAlarmScheduler.scheduleNextAlarm(context, allMutedSettings)
        assertNull("When all prayer alarms are muted, scheduler should return null", scheduled)
    }

    @Test
    fun testScheduleNextAlarmWithOnlyFajrEnabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val onlyFajrSettings = AppSettings(
            alarmModes = mapOf(
                PrayerType.FAJR to AlarmMode.ATHAN,
                PrayerType.SUNRISE to AlarmMode.MUTED,
                PrayerType.DHUHR to AlarmMode.MUTED,
                PrayerType.ASR to AlarmMode.MUTED,
                PrayerType.MAGHRIB to AlarmMode.MUTED,
                PrayerType.ISHA to AlarmMode.MUTED
            )
        )

        val scheduled = AthanAlarmScheduler.scheduleNextAlarm(context, onlyFajrSettings)
        assertNotNull(scheduled)
        assertTrue("Only Fajr should be scheduled", scheduled!!.prayerType == PrayerType.FAJR)
        assertTrue(scheduled.triggerTimeMillis > System.currentTimeMillis())
    }
}
