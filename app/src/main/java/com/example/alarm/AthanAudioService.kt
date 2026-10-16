package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.AlarmMode
import com.example.model.PrayerType

class AthanAudioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    companion object {
        private const val TAG = "AthanAudioService"
        const val CHANNEL_ID = "athan_alarm_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_ALARM = "com.aistudio.athanalarm.START_ALARM"
        const val ACTION_STOP_SERVICE = "com.aistudio.athanalarm.STOP_SERVICE"
        const val ACTION_SNOOZE_ALARM = "com.aistudio.athanalarm.SNOOZE_ALARM"

        const val EXTRA_PRAYER = "extra_prayer"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_SOUND = "extra_sound"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AthanAlarm::AudioPlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_ALARM

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopAlarm()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE_ALARM -> {
                snoozeAlarm(intent)
                stopAlarm()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_ALARM -> {
                val prayerName = intent?.getStringExtra(EXTRA_PRAYER) ?: PrayerType.FAJR.name
                val modeName = intent?.getStringExtra(EXTRA_MODE) ?: AlarmMode.ATHAN.name
                val soundChoice = intent?.getStringExtra(EXTRA_SOUND) ?: "adhan"

                val prayerType = PrayerType.fromName(prayerName)
                val alarmMode = AlarmMode.entries.firstOrNull { it.name == modeName } ?: AlarmMode.ATHAN

                startForeground(NOTIFICATION_ID, buildNotification(prayerType, alarmMode))

                if (alarmMode == AlarmMode.ATHAN || alarmMode == AlarmMode.BEEP) {
                    playAudio(soundChoice, alarmMode)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun playAudio(soundChoice: String, alarmMode: AlarmMode) {
        wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes timeout

        requestAudioFocus()

        try {
            mediaPlayer?.release()

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
                soundChoice == "adhan" -> {
                    Uri.parse("android.resource://$packageName/${R.raw.athan_adhan}")
                }
                soundChoice == "chime" || alarmMode == AlarmMode.BEEP -> {
                    Uri.parse("android.resource://$packageName/${R.raw.athan_chime}")
                }
                else -> {
                    Uri.parse("android.resource://$packageName/${R.raw.athan_chime}")
                }
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, audioUri)
                isLooping = false
                setOnCompletionListener {
                    Log.d(TAG, "Audio completed.")
                    stopAlarm()
                    stopSelf()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    stopAlarm()
                    stopSelf()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio: ${e.message}", e)
            stopAlarm()
            stopSelf()
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(playbackAttributes)
                .build()
            am.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media player: ${e.message}")
        }

        abandonAudioFocus()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun snoozeAlarm(intent: Intent?) {
        val prayerName = intent?.getStringExtra(EXTRA_PRAYER) ?: PrayerType.FAJR.name
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

        val snoozeTime = System.currentTimeMillis() + 10 * 60 * 1000L // 10 minutes
        val snoozeIntent = Intent(this, AthanAlarmReceiver::class.java).apply {
            action = AthanAlarmScheduler.ACTION_PRAYER_ALARM
            putExtra(AthanAlarmScheduler.EXTRA_PRAYER_NAME, prayerName)
            putExtra(AthanAlarmScheduler.EXTRA_ALARM_MODE, AlarmMode.ATHAN.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            1002,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            snoozeTime,
            pendingIntent
        )
    }

    private fun buildNotification(prayerType: PrayerType, alarmMode: AlarmMode): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AthanAudioService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AthanAudioService::class.java).apply {
            action = ACTION_SNOOZE_ALARM
            putExtra(EXTRA_PRAYER, prayerType.name)
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Hayya 'ala-Salah - ${prayerType.englishName} (${prayerType.arabicName})"
        val content = if (alarmMode == AlarmMode.SILENT) {
            "It is now time for ${prayerType.englishName} prayer."
        } else {
            "Athan is calling. Time for ${prayerType.englishName} prayer."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_athan)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .setOngoing(alarmMode != AlarmMode.SILENT)

        if (alarmMode != AlarmMode.SILENT) {
            builder.addAction(android.R.drawable.ic_media_pause, "Stop Athan", stopPendingIntent)
            builder.addAction(android.R.drawable.ic_popup_reminder, "Snooze 10m", snoozePendingIntent)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Athan Prayer Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications and sound for Islamic prayer times"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
