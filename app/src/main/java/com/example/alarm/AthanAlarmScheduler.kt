package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AppSettings
import com.example.engine.PrayerCalculationEngine
import com.example.model.AlarmMode
import com.example.model.PrayerType
import com.example.widget.NextPrayerWidgetProvider
import com.example.widget.PrayerScheduleWidgetProvider
import java.util.Calendar

object AthanAlarmScheduler {
    private const val TAG = "AthanAlarmScheduler"
    const val ACTION_PRAYER_ALARM = "com.aistudio.athanalarm.ACTION_PRAYER_ALARM"
    const val ACTION_PRE_PRAYER_ALERT = "com.aistudio.athanalarm.ACTION_PRE_PRAYER_ALERT"
    const val ACTION_STOP_ATHAN = "com.aistudio.athanalarm.ACTION_STOP_ATHAN"
    const val ACTION_SNOOZE = "com.aistudio.athanalarm.ACTION_SNOOZE"

    const val EXTRA_PRAYER_NAME = "extra_prayer_name"
    const val EXTRA_ALARM_MODE = "extra_alarm_mode"
    const val EXTRA_ATHAN_SOUND = "extra_athan_sound"
    const val EXTRA_PRAYER_TIME_MILLIS = "extra_prayer_time_millis"
    const val EXTRA_PRE_MINUTES = "extra_pre_minutes"

    private const val ALARM_REQUEST_CODE = 1001
    private const val PRE_ALARM_REQUEST_CODE = 1003

    data class ScheduledAlarmInfo(
        val prayerType: PrayerType,
        val triggerTimeMillis: Long,
        val formattedTime: String
    )

    fun scheduleNextAlarm(context: Context, settings: AppSettings): ScheduledAlarmInfo? {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        // Calculate for today
        val todayResult = PrayerCalculationEngine.calculate(
            date = now,
            city = settings.city,
            method = settings.calculationMethod,
            juristic = settings.juristicMethod,
            highLatRule = settings.highLatitudeRule,
            offsets = settings.offsets
        )

        // Calculate for tomorrow
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrowResult = PrayerCalculationEngine.calculate(
            date = tomorrow,
            city = settings.city,
            method = settings.calculationMethod,
            juristic = settings.juristicMethod,
            highLatRule = settings.highLatitudeRule,
            offsets = settings.offsets
        )

        val prayerSchedule = mutableListOf<Pair<PrayerType, Calendar>>()

        fun addPrayer(type: PrayerType, time: Pair<Int, Int>, isTomorrow: Boolean) {
            val cal = (if (isTomorrow) tomorrow.clone() as Calendar else now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, time.first)
                set(Calendar.MINUTE, time.second)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            prayerSchedule.add(type to cal)
        }

        // Add today's prayers
        addPrayer(PrayerType.FAJR, todayResult.fajr, false)
        addPrayer(PrayerType.SUNRISE, todayResult.sunrise, false)
        addPrayer(PrayerType.DHUHR, todayResult.dhuhr, false)
        addPrayer(PrayerType.ASR, todayResult.asr, false)
        addPrayer(PrayerType.MAGHRIB, todayResult.maghrib, false)
        addPrayer(PrayerType.ISHA, todayResult.isha, false)

        // Add tomorrow's Fajr
        addPrayer(PrayerType.FAJR, tomorrowResult.fajr, true)
        addPrayer(PrayerType.SUNRISE, tomorrowResult.sunrise, true)

        val currentTimeMillis = System.currentTimeMillis()

        // Find the earliest upcoming prayer that has alarm enabled (!= MUTED)
        var nextPrayerEntry: Pair<PrayerType, Calendar>? = null
        for (entry in prayerSchedule) {
            val prayerType = entry.first
            val cal = entry.second
            val alarmMode = settings.alarmModes[prayerType] ?: AlarmMode.ATHAN

            if (cal.timeInMillis > currentTimeMillis && alarmMode != AlarmMode.MUTED) {
                nextPrayerEntry = entry
                break
            }
        }

        if (nextPrayerEntry == null) {
            Log.d(TAG, "No upcoming enabled prayer alarm found.")
            return null
        }

        val (nextType, nextCal) = nextPrayerEntry
        val nextMode = settings.alarmModes[nextType] ?: AlarmMode.ATHAN

        // 1. Schedule exact prayer alarm
        val intent = Intent(context, AthanAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
            putExtra(EXTRA_PRAYER_NAME, nextType.name)
            putExtra(EXTRA_ALARM_MODE, nextMode.name)
            putExtra(EXTRA_ATHAN_SOUND, settings.athanSound)
            putExtra(EXTRA_PRAYER_TIME_MILLIS, nextCal.timeInMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarmExact(alarmManager, nextCal.timeInMillis, pendingIntent)

        // 2. Schedule Pre-alert reminder if enabled
        if (settings.prePrayerReminderEnabled && nextType.isSalat) {
            val preAlertTime = nextCal.timeInMillis - (settings.prePrayerReminderMinutes * 60 * 1000L)
            if (preAlertTime > currentTimeMillis) {
                val preIntent = Intent(context, AthanAlarmReceiver::class.java).apply {
                    action = ACTION_PRE_PRAYER_ALERT
                    putExtra(EXTRA_PRAYER_NAME, nextType.name)
                    putExtra(EXTRA_PRE_MINUTES, settings.prePrayerReminderMinutes)
                    putExtra(EXTRA_PRAYER_TIME_MILLIS, nextCal.timeInMillis)
                }
                val prePendingIntent = PendingIntent.getBroadcast(
                    context,
                    PRE_ALARM_REQUEST_CODE,
                    preIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                scheduleAlarmExact(alarmManager, preAlertTime, prePendingIntent)
                Log.d(TAG, "Scheduled pre-alert for ${nextType.englishName} at ${preAlertTime}")
            }
        }

        // 3. Update Widgets
        try {
            NextPrayerWidgetProvider.updateAllWidgets(context)
            PrayerScheduleWidgetProvider.updateAllWidgets(context)
        } catch (_: Exception) {}

        val formattedTime = "%02d:%02d".format(
            nextCal.get(Calendar.HOUR_OF_DAY),
            nextCal.get(Calendar.MINUTE)
        )

        return ScheduledAlarmInfo(
            prayerType = nextType,
            triggerTimeMillis = nextCal.timeInMillis,
            formattedTime = formattedTime
        )
    }

    private fun scheduleAlarmExact(alarmManager: AlarmManager, triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AthanAlarmReceiver::class.java).apply {
            action = ACTION_PRAYER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
