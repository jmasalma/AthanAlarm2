package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettingsRepository
import com.example.engine.PrayerCalculationEngine
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
                            "%d:%02d".format(h, time.second)
                        }
                    }

                    val dateStr = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date())

                    for (widgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_prayer_schedule).apply {
                            setTextViewText(R.id.widget_schedule_title, "Athan • ${settings.city.name}")
                            setTextViewText(R.id.widget_schedule_date, dateStr)

                            setTextViewText(R.id.time_fajr, format(result.fajr))
                            setTextViewText(R.id.time_sunrise, format(result.sunrise))
                            setTextViewText(R.id.time_dhuhr, format(result.dhuhr))
                            setTextViewText(R.id.time_asr, format(result.asr))
                            setTextViewText(R.id.time_maghrib, format(result.maghrib))
                            setTextViewText(R.id.time_isha, format(result.isha))
                            setTextViewText(R.id.time_next_fajr, format(tomorrowResult.fajr))

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
