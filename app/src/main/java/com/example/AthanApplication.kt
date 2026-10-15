package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.alarm.AthanAlarmScheduler
import com.example.alarm.AthanAudioService
import com.example.data.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AthanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        scheduleInitialAlarms()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val athanChannel = NotificationChannel(
                AthanAudioService.CHANNEL_ID,
                "Prayer Times & Athan",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies and plays Athan for Islamic prayer times"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(athanChannel)
        }
    }

    private fun scheduleInitialAlarms() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AppSettingsRepository(applicationContext)
                val settings = repository.settingsFlow.first()
                AthanAlarmScheduler.scheduleNextAlarm(applicationContext, settings)
            } catch (_: Exception) {
            }
        }
    }
}
