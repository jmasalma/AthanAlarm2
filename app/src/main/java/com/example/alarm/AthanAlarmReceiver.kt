package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettingsRepository
import com.example.model.AlarmMode
import com.example.model.PrayerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AthanAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AthanAlarmReceiver"
        const val PRE_ALERT_CHANNEL_ID = "athan_pre_alert_channel"
        const val PRE_ALERT_NOTIFICATION_ID = 3001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive triggered with action: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AthanAlarm::ReceiverWakeLock"
        )
        wakeLock.acquire(15000L) // 15 seconds to dispatch

        try {
            when (action) {
                AthanAlarmScheduler.ACTION_PRE_PRAYER_ALERT -> {
                    handlePrePrayerAlert(context, intent)
                }
                AthanAlarmScheduler.ACTION_PRAYER_ALARM -> {
                    handlePrayerAlarm(context, intent)
                }
            }
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun handlePrePrayerAlert(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AthanAlarmScheduler.EXTRA_PRAYER_NAME) ?: "Prayer"
        val preMinutes = intent.getIntExtra(AthanAlarmScheduler.EXTRA_PRE_MINUTES, 20)
        val prayerTimeMillis = intent.getLongExtra(
            AthanAlarmScheduler.EXTRA_PRAYER_TIME_MILLIS,
            System.currentTimeMillis() + (preMinutes * 60 * 1000L)
        )

        createPreAlertChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, PRE_ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Approaching $prayerName Prayer")
            .setContentText("$prayerName starts in ~$preMinutes minutes. Prepare for Salah.")
            .setWhen(prayerTimeMillis)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(PRE_ALERT_NOTIFICATION_ID, notification)
    }

    private fun handlePrayerAlarm(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(AthanAlarmScheduler.EXTRA_PRAYER_NAME) ?: PrayerType.FAJR.name
        val modeName = intent.getStringExtra(AthanAlarmScheduler.EXTRA_ALARM_MODE) ?: AlarmMode.ATHAN.name
        val soundChoice = intent.getStringExtra(AthanAlarmScheduler.EXTRA_ATHAN_SOUND) ?: "adhan"

        val alarmMode = AlarmMode.entries.firstOrNull { it.name == modeName } ?: AlarmMode.ATHAN

        // Cancel previous pre-alert notification if showing
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(PRE_ALERT_NOTIFICATION_ID)

        if (alarmMode != AlarmMode.MUTED) {
            val serviceIntent = Intent(context, AthanAudioService::class.java).apply {
                action = AthanAudioService.ACTION_START_ALARM
                putExtra(AthanAudioService.EXTRA_PRAYER, prayerName)
                putExtra(AthanAudioService.EXTRA_MODE, alarmMode.name)
                putExtra(AthanAudioService.EXTRA_SOUND, soundChoice)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }

        // Immediately reschedule next prayer alarm
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AppSettingsRepository(context)
                val settings = repository.settingsFlow.first()
                AthanAlarmScheduler.scheduleNextAlarm(context, settings)
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling next alarm: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createPreAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PRE_ALERT_CHANNEL_ID,
                "Pre-Prayer Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows live countdown alerts before prayer times"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
