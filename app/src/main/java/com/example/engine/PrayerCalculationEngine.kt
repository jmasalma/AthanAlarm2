package com.example.engine

import com.example.model.CalculationMethod
import com.example.model.City
import com.example.model.HighLatitudeRule
import com.example.model.JuristicMethod
import com.example.model.PrayerType
import net.sourceforge.jitl.DayPrayers
import net.sourceforge.jitl.ExtremeLatitude
import net.sourceforge.jitl.Jitl
import net.sourceforge.jitl.Mathhab
import net.sourceforge.jitl.Method
import net.sourceforge.jitl.Rounding
import net.sourceforge.jitl.astro.Location
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Astronomical Prayer Times Calculation Engine based on JITL (Islamic Tools and Libraries)
 * matching the exact calculation logic from jmasalma/AthanAlarm, with an astronomical Jean Meeus fallback.
 */
object PrayerCalculationEngine {

    data class PrayerCalculationResult(
        val fajr: Pair<Int, Int>,
        val sunrise: Pair<Int, Int>,
        val dhuhr: Pair<Int, Int>,
        val asr: Pair<Int, Int>,
        val maghrib: Pair<Int, Int>,
        val isha: Pair<Int, Int>,
        val midnight: Pair<Int, Int>,
        val lastThird: Pair<Int, Int>
    )

    /**
     * Primary calculation method taking full date, coordinate, and method parameters.
     */
    fun calculate(
        year: Int,
        month: Int, // 1-12
        day: Int,
        latitude: Double,
        longitude: Double,
        timezone: Double,
        elevation: Double = 0.0,
        method: CalculationMethod = CalculationMethod.ISNA,
        juristic: JuristicMethod = JuristicMethod.STANDARD,
        highLatRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
        offsets: Map<PrayerType, Int> = emptyMap(),
        dhuhrPrecautionMin: Int = 1
    ): PrayerCalculationResult {
        return try {
            calculateWithJitl(
                year = year,
                month = month,
                day = day,
                latitude = latitude,
                longitude = longitude,
                timezone = timezone,
                elevation = elevation,
                methodEnum = method,
                juristic = juristic,
                highLatRule = highLatRule,
                offsets = offsets,
                dhuhrPrecautionMin = dhuhrPrecautionMin
            )
        } catch (_: Exception) {
            calculateWithMeeus(
                year = year,
                month = month,
                day = day,
                latitude = latitude,
                longitude = longitude,
                timezone = timezone,
                elevation = elevation,
                method = method,
                juristic = juristic,
                highLatRule = highLatRule,
                offsets = offsets,
                dhuhrPrecautionMin = dhuhrPrecautionMin
            )
        }
    }

    /**
     * Convenient overload taking a [Calendar] and [com.example.model.City], automatically resolving
     * the correct Daylight Saving Time offset (e.g. EDT vs EST) for the date.
     */
    fun calculate(
        date: Calendar,
        city: City,
        method: CalculationMethod = CalculationMethod.ISNA,
        juristic: JuristicMethod = JuristicMethod.STANDARD,
        highLatRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
        offsets: Map<PrayerType, Int> = emptyMap(),
        dhuhrPrecautionMin: Int = 1
    ): PrayerCalculationResult {
        val effectiveTz = city.getEffectiveTimezone(date)
        return calculate(
            year = date.get(Calendar.YEAR),
            month = date.get(Calendar.MONTH) + 1,
            day = date.get(Calendar.DAY_OF_MONTH),
            latitude = city.latitude,
            longitude = city.longitude,
            timezone = effectiveTz,
            elevation = city.elevation,
            method = method,
            juristic = juristic,
            highLatRule = highLatRule,
            offsets = offsets,
            dhuhrPrecautionMin = dhuhrPrecautionMin
        )
    }

    private fun calculateWithJitl(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timezone: Double,
        elevation: Double,
        methodEnum: CalculationMethod,
        juristic: JuristicMethod,
        highLatRule: HighLatitudeRule,
        offsets: Map<PrayerType, Int>,
        dhuhrPrecautionMin: Int
    ): PrayerCalculationResult {
        val location = Location(latitude, longitude, timezone, 0).apply {
            seaLevel = elevation.coerceAtLeast(0.0)
            pressure = 1010.0
            temperature = 10.0
        }

        val jitlMethod = when (methodEnum) {
            CalculationMethod.ISNA -> Method.ISNA.copy()
            CalculationMethod.MWL -> Method.MUSLIM_LEAGUE.copy()
            CalculationMethod.EGYPT -> Method.EGYPT_SURVEY.copy()
            CalculationMethod.MAKKAH -> Method.UMM_ALQURRA.copy()
            CalculationMethod.KARACHI -> if (juristic == JuristicMethod.HANAFI) Method.KARACHI_HANAF.copy() else Method.KARACHI_SHAF.copy()
            else -> {
                val m = Method.ISNA.copy()
                m.fajrAng = methodEnum.fajrAngle
                if (methodEnum.ishaIntervalMin > 0) {
                    m.ishaaInv = methodEnum.ishaIntervalMin
                    m.ishaaAng = 0.0
                } else {
                    m.ishaaAng = methodEnum.ishaAngle
                }
                m
            }
        }

        jitlMethod.round = Rounding.SPECIAL
        jitlMethod.mathhab = if (juristic == JuristicMethod.HANAFI) Mathhab.HANAFI else Mathhab.SHAAFI
        jitlMethod.extremeLatitude = when (highLatRule) {
            HighLatitudeRule.NONE -> ExtremeLatitude.NONE_EX
            HighLatitudeRule.MIDDLE_OF_NIGHT -> ExtremeLatitude.HALF_INVALID
            HighLatitudeRule.ONE_SEVENTH -> ExtremeLatitude.SEVEN_NIGHT_INVALID
            HighLatitudeRule.ANGLE_BASED -> ExtremeLatitude.GOOD_INVALID
        }

        val gregorianDay = GregorianCalendar(year, month - 1, day)
        val itl = Jitl(location, jitlMethod)
        val dayPrayers: DayPrayers = itl.getPrayerTimes(gregorianDay)

        fun toPair(hour: Int, minute: Int, prayer: PrayerType, extraMin: Int = 0): Pair<Int, Int> {
            val offset = (offsets[prayer] ?: 0) + extraMin
            val totalMinutes = hour * 60 + minute + offset
            val normalized = ((totalMinutes % 1440) + 1440) % 1440
            return Pair(normalized / 60, normalized % 60)
        }

        val fajr = toPair(dayPrayers.fajr().hour, dayPrayers.fajr().minute, PrayerType.FAJR)
        val sunrise = toPair(dayPrayers.shuruq().hour, dayPrayers.shuruq().minute, PrayerType.SUNRISE)
        val dhuhr = toPair(dayPrayers.thuhr().hour, dayPrayers.thuhr().minute, PrayerType.DHUHR, dhuhrPrecautionMin)
        val asr = toPair(dayPrayers.assr().hour, dayPrayers.assr().minute, PrayerType.ASR)
        val maghrib = toPair(dayPrayers.maghrib().hour, dayPrayers.maghrib().minute, PrayerType.MAGHRIB)
        val isha = toPair(dayPrayers.ishaa().hour, dayPrayers.ishaa().minute, PrayerType.ISHA)

        // Midnight & Last third
        val sunsetMin = dayPrayers.maghrib().hour * 60 + dayPrayers.maghrib().minute
        val nextFajrPrayer = itl.getNextDayFajr(gregorianDay)
        val nextFajrMin = (nextFajrPrayer.hour + 24) * 60 + nextFajrPrayer.minute
        val nightDuration = nextFajrMin - sunsetMin
        val midnightMin = sunsetMin + nightDuration / 2
        val lastThirdMin = sunsetMin + (nightDuration * 2) / 3

        val midnightNorm = ((midnightMin % 1440) + 1440) % 1440
        val lastThirdNorm = ((lastThirdMin % 1440) + 1440) % 1440

        return PrayerCalculationResult(
            fajr = fajr,
            sunrise = sunrise,
            dhuhr = dhuhr,
            asr = asr,
            maghrib = maghrib,
            isha = isha,
            midnight = Pair(midnightNorm / 60, midnightNorm % 60),
            lastThird = Pair(lastThirdNorm / 60, lastThirdNorm % 60)
        )
    }

    private fun calculateWithMeeus(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timezone: Double,
        elevation: Double,
        method: CalculationMethod,
        juristic: JuristicMethod,
        highLatRule: HighLatitudeRule,
        offsets: Map<PrayerType, Int>,
        dhuhrPrecautionMin: Int
    ): PrayerCalculationResult {
        val julianDate = getJulianDate(year, month, day)
        val d = julianDate - 2451545.0

        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sin(Math.toRadians(g)) + 0.020 * sin(Math.toRadians(2 * g)))

        val e = 23.439 - 0.00000036 * d
        val sinD = sin(Math.toRadians(e)) * sin(Math.toRadians(l))
        val declination = Math.toDegrees(asin(sinD))

        val cosD = cos(Math.toRadians(e)) * sin(Math.toRadians(l))
        val ra = fixAngle(Math.toDegrees(atan2(cosD, cos(Math.toRadians(l))))) / 15.0

        // Equation of time in hours
        val eqt = q / 15.0 - ra

        // Midday transit time in local clock hours
        val transit = 12.0 + timezone - (longitude / 15.0) - eqt

        val sunAngle = -0.833 - (0.0347 * kotlin.math.sqrt(elevation.coerceAtLeast(0.0)))
        val sunriseHourAngle = calculateHourAngle(latitude, declination, sunAngle)
        val sunriseRaw = transit - (sunriseHourAngle / 15.0)
        val sunsetRaw = transit + (sunriseHourAngle / 15.0)

        val fajrHourAngle = calculateHourAngle(latitude, declination, -method.fajrAngle)
        var fajrRaw = if (fajrHourAngle.isNaN()) Double.NaN else transit - (fajrHourAngle / 15.0)

        val asrAngle = calculateAsrAngle(latitude, declination, juristic.shadowFactor)
        val asrHourAngle = calculateHourAngle(latitude, declination, asrAngle)
        val asrRaw = transit + (asrHourAngle / 15.0)

        var maghribRaw = if (method.maghribAngle > 0.0) {
            val maghribHourAngle = calculateHourAngle(latitude, declination, -method.maghribAngle)
            transit + (maghribHourAngle / 15.0)
        } else {
            sunsetRaw
        }

        var ishaRaw = if (method.ishaIntervalMin > 0) {
            maghribRaw + (method.ishaIntervalMin / 60.0)
        } else {
            val ishaHourAngle = calculateHourAngle(latitude, declination, -method.ishaAngle)
            if (ishaHourAngle.isNaN()) Double.NaN else transit + (ishaHourAngle / 15.0)
        }

        val nightDuration = fixHour(24.0 + sunriseRaw - sunsetRaw)
        if (fajrRaw.isNaN() || highLatRule != HighLatitudeRule.NONE) {
            val maxFajrPortion = when (highLatRule) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> nightDuration / 2.0
                HighLatitudeRule.ONE_SEVENTH -> nightDuration / 7.0
                HighLatitudeRule.ANGLE_BASED -> (method.fajrAngle / 60.0) * nightDuration
                HighLatitudeRule.NONE -> nightDuration / 2.0
            }
            if (fajrRaw.isNaN() || (sunriseRaw - fajrRaw) > maxFajrPortion) {
                fajrRaw = sunriseRaw - maxFajrPortion
            }
        }

        if (ishaRaw.isNaN() || highLatRule != HighLatitudeRule.NONE) {
            val maxIshaPortion = when (highLatRule) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> nightDuration / 2.0
                HighLatitudeRule.ONE_SEVENTH -> nightDuration / 7.0
                HighLatitudeRule.ANGLE_BASED -> (method.ishaAngle / 60.0) * nightDuration
                HighLatitudeRule.NONE -> nightDuration / 2.0
            }
            if (ishaRaw.isNaN() || (ishaRaw - sunsetRaw) > maxIshaPortion) {
                ishaRaw = sunsetRaw + maxIshaPortion
            }
        }

        val midnightRaw = sunsetRaw + (nightDuration / 2.0)
        val lastThirdRaw = sunsetRaw + ((nightDuration * 2.0) / 3.0)
        val dhuhrRaw = transit + (dhuhrPrecautionMin / 60.0)

        fun applyOffset(rawHour: Double, prayer: PrayerType): Pair<Int, Int> {
            val offsetMin = offsets[prayer] ?: 0
            val totalMinutes = Math.round(fixHour(rawHour) * 60.0).toInt() + offsetMin
            val normalizedMin = ((totalMinutes % 1440) + 1440) % 1440
            return Pair(normalizedMin / 60, normalizedMin % 60)
        }

        return PrayerCalculationResult(
            fajr = applyOffset(fajrRaw, PrayerType.FAJR),
            sunrise = applyOffset(sunriseRaw, PrayerType.SUNRISE),
            dhuhr = applyOffset(dhuhrRaw, PrayerType.DHUHR),
            asr = applyOffset(asrRaw, PrayerType.ASR),
            maghrib = applyOffset(maghribRaw, PrayerType.MAGHRIB),
            isha = applyOffset(ishaRaw, PrayerType.ISHA),
            midnight = applyOffset(midnightRaw, PrayerType.FAJR),
            lastThird = applyOffset(lastThirdRaw, PrayerType.FAJR)
        )
    }

    private fun calculateHourAngle(latitude: Double, declination: Double, angle: Double): Double {
        val latRad = Math.toRadians(latitude)
        val decRad = Math.toRadians(declination)
        val angleRad = Math.toRadians(angle)

        val cosH = (sin(angleRad) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        if (cosH > 1.0 || cosH < -1.0) {
            return Double.NaN
        }
        return Math.toDegrees(acos(cosH))
    }

    private fun calculateAsrAngle(latitude: Double, declination: Double, shadowFactor: Double): Double {
        val d = Math.toRadians(declination)
        val phi = Math.toRadians(latitude)
        val cotAngle = shadowFactor + kotlin.math.tan(kotlin.math.abs(phi - d))
        val angleRad = kotlin.math.atan(1.0 / cotAngle)
        return Math.toDegrees(angleRad)
    }

    private fun getJulianDate(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun fixAngle(angle: Double): Double {
        var a = angle - 360.0 * floor(angle / 360.0)
        if (a < 0.0) a += 360.0
        return a
    }

    private fun fixHour(hour: Double): Double {
        var h = hour - 24.0 * floor(hour / 24.0)
        if (h < 0.0) h += 24.0
        return h
    }
}
