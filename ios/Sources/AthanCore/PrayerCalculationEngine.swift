import Foundation

public struct PrayerCalculationResult {
    public let fajr: (hour: Int, minute: Int)
    public let sunrise: (hour: Int, minute: Int)
    public let dhuhr: (hour: Int, minute: Int)
    public let asr: (hour: Int, minute: Int)
    public let maghrib: (hour: Int, minute: Int)
    public let isha: (hour: Int, minute: Int)
    public let midnight: (hour: Int, minute: Int)
    public let lastThird: (hour: Int, minute: Int)

    public init(
        fajr: (hour: Int, minute: Int),
        sunrise: (hour: Int, minute: Int),
        dhuhr: (hour: Int, minute: Int),
        asr: (hour: Int, minute: Int),
        maghrib: (hour: Int, minute: Int),
        isha: (hour: Int, minute: Int),
        midnight: (hour: Int, minute: Int),
        lastThird: (hour: Int, minute: Int)
    ) {
        self.fajr = fajr
        self.sunrise = sunrise
        self.dhuhr = dhuhr
        self.asr = asr
        self.maghrib = maghrib
        self.isha = isha
        self.midnight = midnight
        self.lastThird = lastThird
    }
}

public enum PrayerCalculationEngine {

    public static func calculate(
        date: Date,
        city: City,
        method: CalculationMethod = .isna,
        juristic: JuristicMethod = .standard,
        highLatRule: HighLatitudeRule = .middleOfNight,
        offsets: [PrayerType: Int] = [:],
        dhuhrPrecautionMin: Int = 1
    ) -> PrayerCalculationResult {
        let calendar = Calendar(identifier: .gregorian)
        var cal = calendar
        if let timeZone = TimeZone(identifier: city.timeZoneId) {
            cal.timeZone = timeZone
        }

        let comps = cal.dateComponents([.year, .month, .day], from: date)
        let year = comps.year ?? 2026
        let month = comps.month ?? 10
        let day = comps.day ?? 1

        let tzSeconds = cal.timeZone.secondsFromGMT(for: date)
        let effectiveTimezone = Double(tzSeconds) / 3600.0

        return calculateWithMeeus(
            year: year,
            month: month,
            day: day,
            latitude: city.latitude,
            longitude: city.longitude,
            timezone: effectiveTimezone,
            elevation: city.elevation,
            method: method,
            juristic: juristic,
            highLatRule: highLatRule,
            offsets: offsets,
            dhuhrPrecautionMin: dhuhrPrecautionMin
        )
    }

    public static func calculateWithMeeus(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        timezone: Double,
        elevation: Double = 0.0,
        method: CalculationMethod = .isna,
        juristic: JuristicMethod = .standard,
        highLatRule: HighLatitudeRule = .middleOfNight,
        offsets: [PrayerType: Int] = [:],
        dhuhrPrecautionMin: Int = 1
    ) -> PrayerCalculationResult {
        let julianDate = getJulianDate(year: year, month: month, day: day)
        let d = julianDate - 2451545.0

        let g = fixAngle(357.529 + 0.98560028 * d)
        let q = fixAngle(280.459 + 0.98564736 * d)
        let l = fixAngle(q + 1.915 * sin(g * .pi / 180.0) + 0.020 * sin(2.0 * g * .pi / 180.0))

        let e = 23.439 - 0.00000036 * d
        let sinD = sin(e * .pi / 180.0) * sin(l * .pi / 180.0)
        let declination = asin(sinD) * 180.0 / .pi

        let cosD = cos(e * .pi / 180.0) * sin(l * .pi / 180.0)
        let ra = fixAngle(atan2(cosD, cos(l * .pi / 180.0)) * 180.0 / .pi) / 15.0

        let eqt = q / 15.0 - ra
        let transit = 12.0 + timezone - (longitude / 15.0) - eqt

        let sunAngle = -0.833 - (0.0347 * sqrt(max(0.0, elevation)))
        let sunriseHourAngle = calculateHourAngle(latitude: latitude, declination: declination, angle: sunAngle)
        let sunriseRaw = transit - (sunriseHourAngle / 15.0)
        let sunsetRaw = transit + (sunriseHourAngle / 15.0)

        let fajrHourAngle = calculateHourAngle(latitude: latitude, declination: declination, angle: -method.fajrAngle)
        var fajrRaw = fajrHourAngle.isNaN ? Double.nan : transit - (fajrHourAngle / 15.0)

        let asrAngle = calculateAsrAngle(latitude: latitude, declination: declination, shadowFactor: juristic.shadowFactor)
        let asrHourAngle = calculateHourAngle(latitude: latitude, declination: declination, angle: asrAngle)
        let asrRaw = transit + (asrHourAngle / 15.0)

        var maghribRaw = sunsetRaw
        if method.maghribAngle > 0.0 {
            let maghribHA = calculateHourAngle(latitude: latitude, declination: declination, angle: -method.maghribAngle)
            if !maghribHA.isNaN {
                maghribRaw = transit + (maghribHA / 15.0)
            }
        }

        var ishaRaw = sunsetRaw + 1.5
        if method.ishaIntervalMin > 0 {
            ishaRaw = maghribRaw + (Double(method.ishaIntervalMin) / 60.0)
        } else {
            let ishaHA = calculateHourAngle(latitude: latitude, declination: declination, angle: -method.ishaAngle)
            if !ishaHA.isNaN {
                ishaRaw = transit + (ishaHA / 15.0)
            }
        }

        // High Latitude Adjustment
        let nightPortion = 24.0 - sunsetRaw + sunriseRaw
        if fajrRaw.isNaN || (sunriseRaw - fajrRaw) > (nightPortion / 2.0) {
            switch highLatRule {
            case .middleOfNight:
                fajrRaw = sunriseRaw - (nightPortion / 2.0)
            case .oneSeventh:
                fajrRaw = sunriseRaw - (nightPortion / 7.0)
            case .angleBased:
                fajrRaw = sunriseRaw - (nightPortion * (method.fajrAngle / 60.0))
            case .none:
                break
            }
        }

        if ishaRaw.isNaN || (ishaRaw - sunsetRaw) > (nightPortion / 2.0) {
            if method.ishaIntervalMin > 0 {
                ishaRaw = maghribRaw + (Double(method.ishaIntervalMin) / 60.0)
            } else {
                switch highLatRule {
                case .middleOfNight:
                    ishaRaw = sunsetRaw + (nightPortion / 2.0)
                case .oneSeventh:
                    ishaRaw = sunsetRaw + (nightPortion / 7.0)
                case .angleBased:
                    ishaRaw = sunsetRaw + (nightPortion * (method.ishaAngle / 60.0))
                case .none:
                    break
                }
            }
        }

        func toTimeTuple(rawHours: Double, prayer: PrayerType, extraMinutes: Int = 0) -> (hour: Int, minute: Int) {
            guard !rawHours.isNaN else { return (0, 0) }
            let totalMin = Int(round(rawHours * 60.0)) + (offsets[prayer] ?? 0) + extraMinutes
            let normalized = ((totalMin % 1440) + 1440) % 1440
            return (normalized / 60, normalized % 60)
        }

        let fajrTime = toTimeTuple(rawHours: fajrRaw, prayer: .fajr)
        let sunriseTime = toTimeTuple(rawHours: sunriseRaw, prayer: .sunrise)
        let dhuhrTime = toTimeTuple(rawHours: transit, prayer: .dhuhr, extraMinutes: dhuhrPrecautionMin)
        let asrTime = toTimeTuple(rawHours: asrRaw, prayer: .asr)
        let maghribTime = toTimeTuple(rawHours: maghribRaw, prayer: .maghrib)
        let ishaTime = toTimeTuple(rawHours: ishaRaw, prayer: .isha)

        let sunsetMin = (maghribTime.hour * 60) + maghribTime.minute
        let nextFajrMin = ((fajrTime.hour + 24) * 60) + fajrTime.minute
        let nightDuration = nextFajrMin - sunsetMin
        let midnightMin = ((sunsetMin + (nightDuration / 2)) % 1440 + 1440) % 1440
        let lastThirdMin = ((sunsetMin + ((nightDuration * 2) / 3)) % 1440 + 1440) % 1440

        return PrayerCalculationResult(
            fajr: fajrTime,
            sunrise: sunriseTime,
            dhuhr: dhuhrTime,
            asr: asrTime,
            maghrib: maghribTime,
            isha: ishaTime,
            midnight: (midnightMin / 60, midnightMin % 60),
            lastThird: (lastThirdMin / 60, lastThirdMin % 60)
        )
    }

    private static func getJulianDate(year: Int, month: Int, day: Int) -> Double {
        var y = year
        var m = month
        if m <= 2 {
            y -= 1
            m += 12
        }
        let a = floor(Double(y) / 100.0)
        let b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * Double(y + 4716)) + floor(30.6001 * Double(m + 1)) + Double(day) + b - 1524.5
    }

    private static func fixAngle(_ angle: Double) -> Double {
        var a = angle.truncatingRemainder(dividingBy: 360.0)
        if a < 0 { a += 360.0 }
        return a
    }

    private static func calculateHourAngle(latitude: Double, declination: Double, angle: Double) -> Double {
        let latRad = latitude * .pi / 180.0
        let decRad = declination * .pi / 180.0
        let angleRad = angle * .pi / 180.0

        let val = (sin(angleRad) - sin(latRad) * sin(decRad)) / (cos(latRad) * cos(decRad))
        guard val >= -1.0 && val <= 1.0 else { return Double.nan }
        return acos(val) * 180.0 / .pi
    }

    private static func calculateAsrAngle(latitude: Double, declination: Double, shadowFactor: Double) -> Double {
        let diffRad = abs(latitude - declination) * .pi / 180.0
        let cotAngle = shadowFactor + tan(diffRad)
        return atan(1.0 / cotAngle) * 180.0 / .pi
    }
}
