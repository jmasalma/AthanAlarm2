package com.example.engine

import java.util.Calendar
import kotlin.math.floor

data class HijriDate(
    val day: Int,
    val month: Int, // 1 to 12
    val year: Int,
    val monthEnglish: String,
    val monthArabic: String
) {
    val formattedEnglish: String
        get() = "$day $monthEnglish $year AH"

    val formattedArabic: String
        get() = "$day $monthArabic $year هـ"
}

object HijriCalendarHelper {

    private val MONTHS_ENGLISH = listOf(
        "Muharram",
        "Safar",
        "Rabi' al-Awwal",
        "Rabi' al-Thani",
        "Jumada al-Awwal",
        "Jumada al-Thani",
        "Rajab",
        "Sha'ban",
        "Ramadan",
        "Shawwal",
        "Dhu al-Qi'dah",
        "Dhu al-Hijjah"
    )

    private val MONTHS_ARABIC = listOf(
        "محرم",
        "صفر",
        "ربيع الأول",
        "ربيع الثاني",
        "جمادى الأولى",
        "جمادى الآخرة",
        "رجب",
        "شعبان",
        "رمضان",
        "شوال",
        "ذو القعدة",
        "ذو الحجة"
    )

    /**
     * Converts Gregorian date to Hijri date using Kuwaiti/Astronomical algorithm
     * with dayOffset support.
     */
    fun getHijriDate(
        year: Int,
        month: Int, // 1-12
        day: Int,
        dayOffset: Int = 0
    ): HijriDate {
        var d = day + dayOffset
        var m = month
        var y = year

        if (m < 3) {
            y -= 1
            m += 12
        }

        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)

        val jd = floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5

        val epoch = 1948439.5
        val daysSinceEpoch = jd - epoch

        val cycles = floor(daysSinceEpoch / 10631.0)
        val remainingDays = daysSinceEpoch - cycles * 10631.0

        val yearInCycle = floor((remainingDays - 0.5) / 354.366)
        val hijriYear = (cycles * 30 + yearInCycle + 1).toInt()

        val dayOfYear = remainingDays - floor(yearInCycle * 354.366) - 0.5
        val hijriMonth = (floor((dayOfYear) / 29.5) + 1).toInt().coerceIn(1, 12)
        val hijriDay = (dayOfYear - floor((hijriMonth - 1) * 29.5) + 1).toInt().coerceIn(1, 30)

        return HijriDate(
            day = hijriDay,
            month = hijriMonth,
            year = hijriYear,
            monthEnglish = MONTHS_ENGLISH[hijriMonth - 1],
            monthArabic = MONTHS_ARABIC[hijriMonth - 1]
        )
    }

    fun getTodayHijri(dayOffset: Int = 0): HijriDate {
        val cal = Calendar.getInstance()
        return getHijriDate(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            dayOffset
        )
    }
}
