package com.example.engine

import com.example.data.AppSettings
import com.example.data.RamadanMode
import java.util.Calendar

data class FastingStatus(
    val isActive: Boolean,
    val isIftar: Boolean, // true = next milestone is Iftar (Maghrib), false = next is Imsak (Fajr)
    val milestoneName: String, // "Iftar" or "Imsak"
    val targetHour: Int,
    val targetMinute: Int,
    val formattedTargetTime: String,
    val secondsRemaining: Long,
    val countdownFormatted: String,
    val shortCountdownFormatted: String
)

object FastingHelper {

    /**
     * Determines whether Fasting Mode is currently active based on user settings
     * and auto-detection of Ramadan (Hijri month 9).
     */
    fun isFastingActive(settings: AppSettings, now: Calendar = Calendar.getInstance()): Boolean {
        return when (settings.ramadanMode) {
            RamadanMode.OFF -> false
            RamadanMode.FASTING_DAYS -> true
            RamadanMode.AUTO -> {
                val hijri = HijriCalendarHelper.getTodayHijri(settings.hijriDayOffset)
                hijri.month == 9 // Ramadan is month 9 in Islamic calendar
            }
        }
    }

    /**
     * Computes the current fasting status, milestone (Imsak vs Iftar), and countdown.
     * Exact Fajr is used for Imsak; exact Maghrib is used for Iftar.
     */
    fun calculateFastingStatus(
        settings: AppSettings,
        todayFajr: Pair<Int, Int>,
        todayMaghrib: Pair<Int, Int>,
        tomorrowFajr: Pair<Int, Int>,
        now: Calendar = Calendar.getInstance()
    ): FastingStatus? {
        if (!isFastingActive(settings, now)) return null

        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val nowSec = now.get(Calendar.HOUR_OF_DAY) * 3600 +
                now.get(Calendar.MINUTE) * 60 +
                now.get(Calendar.SECOND)

        val fajrMinutes = todayFajr.first * 60 + todayFajr.second
        val maghribMinutes = todayMaghrib.first * 60 + todayMaghrib.second

        val isIftar: Boolean
        val milestoneName: String
        val targetPair: Pair<Int, Int>
        val isTomorrow: Boolean

        when {
            currentMinutes < fajrMinutes -> {
                // Before Fajr today: fasting begins at Fajr (Imsak)
                isIftar = false
                milestoneName = "Imsak"
                targetPair = todayFajr
                isTomorrow = false
            }
            currentMinutes < maghribMinutes -> {
                // Between Fajr and Maghrib: fasting ends at Maghrib (Iftar)
                isIftar = true
                milestoneName = "Iftar"
                targetPair = todayMaghrib
                isTomorrow = false
            }
            else -> {
                // After Maghrib: Iftar has passed for today, next milestone is tomorrow's Fajr (Imsak)
                isIftar = false
                milestoneName = "Imsak"
                targetPair = tomorrowFajr
                isTomorrow = true
            }
        }

        val targetSec = targetPair.first * 3600 + targetPair.second * 60
        val diffSec = if (isTomorrow || targetSec < nowSec) {
            (86400 - nowSec) + targetSec
        } else {
            (targetSec - nowSec).coerceAtLeast(0)
        }

        val hours = diffSec / 3600
        val mins = (diffSec % 3600) / 60
        val secs = diffSec % 60

        val countdownFormatted = "%02d:%02d:%02d".format(hours, mins, secs)
        val shortCountdownFormatted = if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m ${secs}s"
        }

        val formattedTargetTime = if (settings.is24HourFormat) {
            "%02d:%02d".format(targetPair.first, targetPair.second)
        } else {
            val h = when {
                targetPair.first == 0 -> 12
                targetPair.first > 12 -> targetPair.first - 12
                else -> targetPair.first
            }
            val ampm = if (targetPair.first < 12) "AM" else "PM"
            "%d:%02d %s".format(h, targetPair.second, ampm)
        }

        return FastingStatus(
            isActive = true,
            isIftar = isIftar,
            milestoneName = milestoneName,
            targetHour = targetPair.first,
            targetMinute = targetPair.second,
            formattedTargetTime = formattedTargetTime,
            secondsRemaining = diffSec.toLong(),
            countdownFormatted = countdownFormatted,
            shortCountdownFormatted = shortCountdownFormatted
        )
    }
}
