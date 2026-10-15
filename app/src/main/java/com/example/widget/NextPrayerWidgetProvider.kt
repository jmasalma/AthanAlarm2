package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettingsRepository
import com.example.engine.PrayerCalculationEngine
import com.example.model.PrayerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class NextPrayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, NextPrayerWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                updateWidgets(context, appWidgetManager, allWidgetIds)
            }
        }

        private fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val repo = AppSettingsRepository(context)
                    val settings = repo.settingsFlow.first()

                    val now = Calendar.getInstance()
                    val result = PrayerCalculationEngine.calculate(
                        date = now,
                        city = settings.city,
                        method = settings.calculationMethod,
                        juristic = settings.juristicMethod,
                        highLatRule = settings.highLatitudeRule,
                        offsets = settings.offsets
                    )

                    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                    val tomorrowResult = PrayerCalculationEngine.calculate(
                        date = tomorrow,
                        city = settings.city,
                        method = settings.calculationMethod,
                        juristic = settings.juristicMethod,
                        highLatRule = settings.highLatitudeRule,
                        offsets = settings.offsets
                    )

                    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

                    val list = listOf(
                        PrayerType.FAJR to result.fajr,
                        PrayerType.SUNRISE to result.sunrise,
                        PrayerType.DHUHR to result.dhuhr,
                        PrayerType.ASR to result.asr,
                        PrayerType.MAGHRIB to result.maghrib,
                        PrayerType.ISHA to result.isha
                    )

                    var nextPair: Pair<PrayerType, Pair<Int, Int>>? = null
                    for (pair in list) {
                        val m = pair.second.first * 60 + pair.second.second
                        if (m > currentMinutes) {
                            nextPair = pair
                            break
                        }
                    }

                    val isTomorrow = (nextPair == null)
                    if (nextPair == null) {
                        nextPair = PrayerType.FAJR to tomorrowResult.fajr
                    }

                    val (type, time) = nextPair
                    val englishName = if (type == PrayerType.NEXT_FAJR) "Fajr" else type.englishName

                    val formattedTime = if (settings.is24HourFormat) {
                        "%02d:%02d".format(time.first, time.second)
                    } else {
                        val h = when {
                            time.first == 0 -> 12
                            time.first > 12 -> time.first - 12
                            else -> time.first
                        }
                        val ampm = if (time.first < 12) "AM" else "PM"
                        "%d:%02d %s".format(h, time.second, ampm)
                    }

                    val targetCal = Calendar.getInstance().apply {
                        if (isTomorrow) {
                            add(Calendar.DAY_OF_MONTH, 1)
                        }
                        set(Calendar.HOUR_OF_DAY, time.first)
                        set(Calendar.MINUTE, time.second)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        if (before(now)) {
                            add(Calendar.DAY_OF_MONTH, 1)
                        }
                    }

                    val diffMillis = targetCal.timeInMillis - now.timeInMillis
                    val baseElapsed = SystemClock.elapsedRealtime() + diffMillis.coerceAtLeast(0)

                    for (widgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_next_prayer).apply {
                            setTextViewText(R.id.widget_prayer_name, englishName)
                            setTextViewText(R.id.widget_prayer_time, formattedTime)

                            // Live countdown chronometer
                            setChronometerCountDown(R.id.widget_countdown_chronometer, true)
                            setChronometer(R.id.widget_countdown_chronometer, baseElapsed, null, true)

                            val openAppIntent = Intent(context, MainActivity::class.java)
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                widgetId,
                                openAppIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            setOnClickPendingIntent(R.id.widget_next_prayer_root, pendingIntent)
                        }
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
