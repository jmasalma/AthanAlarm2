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
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.cast.CastMediaManager
import com.example.data.AppSettingsRepository
import com.example.model.AlarmMode
import com.example.model.PrayerType
import com.example.util.AppLogger
import com.example.util.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

class AthanAudioService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private var isLocalAudioPlaying = false
    private var castingObserverJob: Job? = null

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

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager?.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "AthanAlarm::CastWifiLock"
        )?.apply {
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

                if (alarmMode != AlarmMode.MUTED) {
                    playAudio(soundChoice, alarmMode, prayerType)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun playAudio(soundChoice: String, alarmMode: AlarmMode, prayerType: PrayerType) {
        wakeLock?.acquire(5 * 60 * 1000L) // 5 minutes safety timeout
        wifiLock?.acquire()

        requestAudioFocus()

        // 1. If Cast is enabled, initiate casting to Google Home / Chromecast device
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = AppSettingsRepository(applicationContext).settingsFlow.first()
                if (settings.castEnabled) {
                    val castSound = if (settings.castAlertSound.isNotBlank()) settings.castAlertSound else soundChoice
                    AppLogger.i(LogCategory.ALARM, TAG, "Triggering prayer cast: device=${settings.castDeviceName}, sound=$castSound")
                    CastMediaManager.playOnCast(
                        context = applicationContext,
                        soundChoice = castSound,
                        prayerType = prayerType,
                        targetDeviceId = settings.castDeviceId,
                        targetDeviceName = settings.castDeviceName
                    )
                }
            } catch (e: Exception) {
                AppLogger.w(LogCategory.ALARM, TAG, "Cast playback initiation error: ${e.message}")
            }
        }

        // 2. Monitor Cast status so service stays alive while cast playback is in progress
        castingObserverJob?.cancel()
        castingObserverJob = CoroutineScope(Dispatchers.Main).launch {
            CastMediaManager.isCastingFlow.collect { isCasting ->
                if (!isCasting && !isLocalAudioPlaying) {
                    AppLogger.i(LogCategory.ALARM, TAG, "Both phone audio and cast audio completed. Stopping foreground service.")
                    stopAlarm()
                    stopSelf()
                }
            }
        }

        // 3. Play local audio on phone (unless user selected SILENT / notification only)
        if (alarmMode == AlarmMode.ATHAN || alarmMode == AlarmMode.BEEP) {
            isLocalAudioPlaying = true
            try {
                mediaPlayer?.release()

                val filePath = when {
                    soundChoice.startsWith("custom_file:") -> soundChoice.removePrefix("custom_file:")
                    soundChoice.startsWith("file://") -> Uri.parse(soundChoice).path
                    else -> null
                }
                if (filePath != null) {
                    val file = File(filePath)
                    if (file.exists()) {
                        FileInputStream(file).use { fis ->
                            mediaPlayer = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_ALARM)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()
                                )
                                setDataSource(fis.fd)
                                isLooping = false
                                setOnCompletionListener {
                                    AppLogger.i(LogCategory.ALARM, TAG, "Local phone audio completed.")
                                    isLocalAudioPlaying = false
                                    checkCompletionAndStop()
                                }
                                setOnErrorListener { _, what, extra ->
                                    AppLogger.e(LogCategory.ALARM, TAG, "MediaPlayer error: what=$what extra=$extra")
                                    isLocalAudioPlaying = false
                                    checkCompletionAndStop()
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
                    soundChoice == "adhan" || soundChoice == "athan" -> {
                        if (prayerType == PrayerType.FAJR || prayerType == PrayerType.NEXT_FAJR) {
                            Uri.parse("android.resource://$packageName/${R.raw.athan_fajr}")
                        } else {
                            Uri.parse("android.resource://$packageName/${R.raw.athan_general}")
                        }
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
                        AppLogger.i(LogCategory.ALARM, TAG, "Local phone audio completed.")
                        isLocalAudioPlaying = false
                        checkCompletionAndStop()
                    }
                    setOnErrorListener { _, what, extra ->
                        AppLogger.e(LogCategory.ALARM, TAG, "MediaPlayer error: what=$what extra=$extra")
                        isLocalAudioPlaying = false
                        checkCompletionAndStop()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                AppLogger.e(LogCategory.ALARM, TAG, "Error playing local audio: ${e.message}", e)
                isLocalAudioPlaying = false
                checkCompletionAndStop()
            }
        } else {
            isLocalAudioPlaying = false
        }
    }

    private fun checkCompletionAndStop() {
        if (!isLocalAudioPlaying && !CastMediaManager.isCastingFlow.value) {
            AppLogger.i(LogCategory.ALARM, TAG, "Local audio finished and Cast is not active. Stopping service.")
            stopAlarm()
            stopSelf()
        } else {
            AppLogger.i(LogCategory.ALARM, TAG, "Local audio completed, but Cast is still streaming to smart speaker.")
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
        isLocalAudioPlaying = false
        castingObserverJob?.cancel()

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            AppLogger.e(LogCategory.ALARM, TAG, "Error stopping media player: ${e.message}")
        }

        abandonAudioFocus()

        CastMediaManager.stopCast(applicationContext)

        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }

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
        val openAppPendingIntent = PendingIntent.getActivity(
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

        val prayerDisplayName = prayerType.englishName
        val title = "Time for $prayerDisplayName"
        val text = "Prayer time has arrived. Hayya 'ala-s-Salah."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_athan)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Stop Athan", stopPendingIntent)
            .addAction(0, "Snooze (10m)", snoozePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Athan Prayer Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Plays the Athan when it is time to pray"
                setSound(null, null) // Audio handled by MediaPlayer / Cast
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }
}
