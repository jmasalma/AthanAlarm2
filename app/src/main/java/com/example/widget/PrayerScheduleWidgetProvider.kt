package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PrayerScheduleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, PrayerScheduleWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                updateWidgets(context, appWidgetManager, allWidgetIds)
            }
        }

        private data class PrayerRowBinding(
            val type: PrayerType,
            val time: Pair<Int, Int>,
            val indicatorId: Int,
            val nameId: Int,
            val timeId: Int
        )

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

                    fun format(time: Pair<Int, Int>): String {
                        return if (settings.is24HourFormat) {
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
                    }

                    val dateStr = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())
                    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

                    val rows = listOf(
                        PrayerRowBinding(PrayerType.FAJR, result.fajr, R.id.indicator_fajr, R.id.name_fajr, R.id.time_fajr),
                        PrayerRowBinding(PrayerType.SUNRISE, result.sunrise, R.id.indicator_sunrise, R.id.name_sunrise, R.id.time_sunrise),
                        PrayerRowBinding(PrayerType.DHUHR, result.dhuhr, R.id.indicator_dhuhr, R.id.name_dhuhr, R.id.time_dhuhr),
                        PrayerRowBinding(PrayerType.ASR, result.asr, R.id.indicator_asr, R.id.name_asr, R.id.time_asr),
                        PrayerRowBinding(PrayerType.MAGHRIB, result.maghrib, R.id.indicator_maghrib, R.id.name_maghrib, R.id.time_maghrib),
                        PrayerRowBinding(PrayerType.ISHA, result.isha, R.id.indicator_isha, R.id.name_isha, R.id.time_isha)
                    )

                    var nextType: PrayerType? = null
                    for (row in rows) {
                        val m = row.time.first * 60 + row.time.second
                        if (m > currentMinutes) {
                            nextType = row.type
                            break
                        }
                    }
                    val isTomorrowFajr = (nextType == null)

                    for (widgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_prayer_schedule).apply {
                            setTextViewText(R.id.widget_schedule_title, "Athan • ${settings.city.name}")
                            setTextViewText(R.id.widget_schedule_date, dateStr)

                            for (row in rows) {
                                val m = row.time.first * 60 + row.time.second
                                setTextViewText(row.timeId, format(row.time))
                                val isNext = (row.type == nextType && !isTomorrowFajr)
                                val isPassed = if (isTomorrowFajr) true else (m <= currentMinutes)

                                if (isNext) {
                                    setViewVisibility(row.indicatorId, View.VISIBLE)
                                    setTextColor(row.nameId, Color.parseColor("#D4AF37"))
                                    setTextColor(row.timeId, Color.parseColor("#D4AF37"))
                                } else if (isPassed) {
                                    setViewVisibility(row.indicatorId, View.INVISIBLE)
                                    setTextColor(row.nameId, Color.parseColor("#70A2C4BC"))
                                    setTextColor(row.timeId, Color.parseColor("#80FFFFFF"))
                                } else {
                                    setViewVisibility(row.indicatorId, View.INVISIBLE)
                                    setTextColor(row.nameId, Color.parseColor("#A2C4BC"))
                                    setTextColor(row.timeId, Color.parseColor("#FFFFFF"))
                                }
                            }

                            // Next Fajr (tomorrow)
                            setTextViewText(R.id.time_next_fajr, format(tomorrowResult.fajr))
                            if (isTomorrowFajr) {
                                setViewVisibility(R.id.indicator_next_fajr, View.VISIBLE)
                                setTextColor(R.id.name_next_fajr, Color.parseColor("#D4AF37"))
                                setTextColor(R.id.time_next_fajr, Color.parseColor("#D4AF37"))
                            } else {
                                setViewVisibility(R.id.indicator_next_fajr, View.INVISIBLE)
                                setTextColor(R.id.name_next_fajr, Color.parseColor("#88D4AF37"))
                                setTextColor(R.id.time_next_fajr, Color.parseColor("#CCFFFFFF"))
                            }

                            val openAppIntent = Intent(context, MainActivity::class.java)
                            val pendingIntent = PendingIntent.getActivity(
                                context,
                                widgetId,
                                openAppIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            setOnClickPendingIntent(R.id.widget_schedule_root, pendingIntent)
                        }
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
