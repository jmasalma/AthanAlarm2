package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettingsRepository
import com.example.model.AlarmMode
import com.example.model.PrayerType
import com.example.util.AppLogger
import com.example.util.LogCategory
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AthanAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AthanAlarmReceiver"
        const val PRE_ALERT_CHANNEL_ID = "pre_prayer_alert_channel"
        const val PRE_ALERT_NOTIFICATION_ID = 2002
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLogger.i(LogCategory.ALARM, TAG, "Alarm broadcast received with action: $action")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AthanAlarm::ReceiverWakeLock"
        )
        wakeLock.acquire(15000L) // 15 seconds to dispatch

        if (action == AthanAlarmScheduler.ACTION_PRAYER_ALARM) {
            try {
                @Suppress("DEPRECATION")
                val screenLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "AthanAlarm::ReceiverScreenWakeLock"
                )
                screenLock.acquire(15000L)
            } catch (_: Exception) {}
        }

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
        val soundChoice = intent.getStringExtra(AthanAlarmScheduler.EXTRA_PRE_SOUND) ?: "chime"
        val prayerTimeMillis = intent.getLongExtra(
            AthanAlarmScheduler.EXTRA_PRAYER_TIME_MILLIS,
            System.currentTimeMillis() + (preMinutes * 60 * 1000L)
        )

        AppLogger.i(LogCategory.ALARM, TAG, "Posting pre-prayer alert notification for $prayerName (~$preMinutes min before, sound: $soundChoice)")
        createPreAlertChannel(context)

        // Play the chosen pre-prayer notification sound if not set to silent
        if (soundChoice != "silent") {
            playPreAlertSound(context, soundChoice)
        }

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
            .setSmallIcon(R.drawable.ic_notification_athan)
            .setContentTitle("$prayerName Soon")
            .setContentText("Starts in ~$preMinutes min")
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
        AppLogger.i(LogCategory.ALARM, TAG, "Triggering prayer alarm for $prayerName (mode: $alarmMode, sound: $soundChoice)")

        // Cancel previous pre-alert notification and old persistent prayer notification if showing
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(PRE_ALERT_NOTIFICATION_ID)
        notificationManager.cancel(AthanAudioService.PRAYER_PERSISTENT_NOTIFICATION_ID)

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
        } else {
            AppLogger.i(LogCategory.ALARM, TAG, "Prayer alarm for $prayerName is MUTED/Disabled")
        }

        // Immediately reschedule next prayer alarm
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = AppSettingsRepository(context)
                val settings = repository.settingsFlow.first()
                val nextInfo = AthanAlarmScheduler.scheduleNextAlarm(context, settings)
                AppLogger.i(LogCategory.ALARM, TAG, "Rescheduled next alarm for: ${nextInfo?.prayerType} at ${nextInfo?.formattedTime}")
            } catch (e: Exception) {
                AppLogger.e(LogCategory.ALARM, TAG, "Error rescheduling next alarm: ${e.message}", e)
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
                description = "Countdown alerts 10-30 minutes prior to prayer"
                enableVibration(true)
                setSound(null, null) // Custom/dynamic sound played via playPreAlertSound
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun playPreAlertSound(context: Context, soundChoice: String) {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val filePath = when {
                soundChoice.startsWith("custom_file:") -> soundChoice.removePrefix("custom_file:")
                soundChoice.startsWith("file://") -> Uri.parse(soundChoice).path
                else -> null
            }

            if (filePath != null) {
                val file = File(filePath)
                if (file.exists()) {
                    FileInputStream(file).use { fis ->
                        MediaPlayer().apply {
                            setAudioAttributes(audioAttributes)
                            setDataSource(fis.fd)
                            setOnCompletionListener { it.release() }
                            setOnErrorListener { mp, _, _ ->
                                mp.release()
                                true
                            }
                            prepare()
                            start()
                        }
                    }
                    return
                }
            }

            val audioUri: Uri = when {
                soundChoice.startsWith("content://") || soundChoice.startsWith("file://") -> {
                    Uri.parse(soundChoice)
                }
                soundChoice == "system_notification" -> {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
                soundChoice == "system_alarm" -> {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                soundChoice == "chime" -> {
                    Uri.parse("android.resource://${context.packageName}/${R.raw.athan_chime}")
                }
                else -> {
                    Uri.parse("android.resource://${context.packageName}/${R.raw.athan_chime}")
                }
            }

            MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(context, audioUri)
                setOnCompletionListener { it.release() }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            AppLogger.e(LogCategory.ALARM, TAG, "Failed to play pre-prayer alert sound ($soundChoice): ${e.message}", e)
        }
    }
}
