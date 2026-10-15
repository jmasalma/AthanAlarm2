package com.example.model

enum class PrayerType(
    val englishName: String,
    val arabicName: String,
    val isSalat: Boolean
) {
    FAJR("Fajr", "الفجر", true),
    SUNRISE("Sunrise", "الشروق", false),
    DHUHR("Dhuhr", "الظهر", true),
    ASR("Asr", "العصر", true),
    MAGHRIB("Maghrib", "المغرب", true),
    ISHA("Isha", "العشاء", true),
    NEXT_FAJR("Next Fajr", "الفجر القادم", true);

    companion object {
        fun fromName(name: String): PrayerType {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FAJR
        }
    }
}

enum class AlarmMode(val title: String) {
    ATHAN("Full Athan"),
    BEEP("Gentle Chime"),
    SILENT("Notification Only"),
    MUTED("Disabled");

    fun next(): AlarmMode {
        return when (this) {
            ATHAN -> BEEP
            BEEP -> SILENT
            SILENT -> MUTED
            MUTED -> ATHAN
        }
    }
}

enum class CalculationMethod(
    val title: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMin: Int = 0,
    val maghribAngle: Double = 0.0
) {
    MWL("Muslim World League", 18.0, 17.0),
    ISNA("Islamic Society of North America (ISNA)", 15.0, 15.0),
    EGYPT("Egyptian General Authority of Survey", 19.5, 17.5),
    MAKKAH("Umm Al-Qura University, Makkah", 18.5, 0.0, ishaIntervalMin = 90),
    KARACHI("Univ. of Islamic Sciences, Karachi", 18.0, 18.0),
    TEHRAN("Institute of Geophysics, Tehran", 17.7, 14.0, maghribAngle = 4.5),
    GULF("Gulf Region / UAE", 19.5, 0.0, ishaIntervalMin = 90),
    KUWAIT("Kuwait", 18.0, 17.5),
    QATAR("Qatar", 18.0, 0.0, ishaIntervalMin = 90),
    DIYANET("Diyanet İşleri Başkanlığı (Turkey)", 18.0, 17.0),
    FRANCE("Union of Islamic Organisations of France", 12.0, 12.0)
}

enum class JuristicMethod(val title: String, val shadowFactor: Double) {
    STANDARD("Standard (Shafi'i, Maliki, Hanbali)", 1.0),
    HANAFI("Hanafi", 2.0)
}

enum class HighLatitudeRule(val title: String) {
    NONE("None"),
    MIDDLE_OF_NIGHT("Middle of the Night"),
    ONE_SEVENTH("One-Seventh of the Night"),
    ANGLE_BASED("Angle-Based")
}

data class PrayerTimeItem(
    val type: PrayerType,
    val hour: Int,
    val minute: Int,
    val alarmMode: AlarmMode = AlarmMode.ATHAN,
    val isNext: Boolean = false,
    val isPassed: Boolean = false
) {
    val formattedTime12h: String
        get() {
            val period = if (hour < 12) "AM" else "PM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return "%d:%02d %s".format(displayHour, minute, period)
        }

    val formattedTime24h: String
        get() = "%02d:%02d".format(hour, minute)

    val totalMinutes: Int
        get() = hour * 60 + minute
}
