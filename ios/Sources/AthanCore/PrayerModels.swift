import Foundation

public enum PrayerType: String, CaseIterable, Codable, Identifiable {
    case fajr = "FAJR"
    case sunrise = "SUNRISE"
    case dhuhr = "DHUHR"
    case asr = "ASR"
    case maghrib = "MAGHRIB"
    case isha = "ISHA"
    case nextFajr = "NEXT_FAJR"

    public var id: String { rawValue }

    public var englishName: String {
        switch self {
        case .fajr: return "Fajr"
        case .sunrise: return "Sunrise"
        case .dhuhr: return "Dhuhr"
        case .asr: return "Asr"
        case .maghrib: return "Maghrib"
        case .isha: return "Isha"
        case .nextFajr: return "Next Fajr"
        }
    }

    public var arabicName: String {
        switch self {
        case .fajr: return "الفجر"
        case .sunrise: return "الشروق"
        case .dhuhr: return "الظهر"
        case .asr: return "العصر"
        case .maghrib: return "المغرب"
        case .isha: return "العشاء"
        case .nextFajr: return "الفجر القادم"
        }
    }

    public var isSalat: BooleanLiteralType {
        return self != .sunrise
    }
}

public enum AlarmMode: String, CaseIterable, Codable {
    case athan = "ATHAN"
    case chime = "CHIME"
    case silent = "SILENT"
    case muted = "MUTED"

    public var title: String {
        switch self {
        case .athan: return "Full Athan"
        case .chime: return "Gentle Chime"
        case .silent: return "Notification Only"
        case .muted: return "Disabled"
        }
    }

    public func next() -> AlarmMode {
        switch self {
        case .athan: return .chime
        case .chime: return .silent
        case .silent: return .muted
        case .muted: return .athan
        }
    }
}

public enum CalculationMethod: String, CaseIterable, Codable, Identifiable {
    case mwl = "MWL"
    case isna = "ISNA"
    case egypt = "EGYPT"
    case makkah = "MAKKAH"
    case karachi = "KARACHI"
    case tehran = "TEHRAN"
    case gulf = "GULF"
    case kuwait = "KUWAIT"
    case qatar = "QATAR"
    case diyanet = "DIYANET"
    case france = "FRANCE"

    public var id: String { rawValue }

    public var title: String {
        switch self {
        case .mwl: return "Muslim World League"
        case .isna: return "Islamic Society of North America (ISNA)"
        case .egypt: return "Egyptian General Authority of Survey"
        case .makkah: return "Umm Al-Qura University, Makkah"
        case .karachi: return "Univ. of Islamic Sciences, Karachi"
        case .tehran: return "Institute of Geophysics, Tehran"
        case .gulf: return "Gulf Region / UAE"
        case .kuwait: return "Kuwait"
        case .qatar: return "Qatar"
        case .diyanet: return "Diyanet İşleri Başkanlığı (Turkey)"
        case .france: return "Union of Islamic Organisations of France"
        }
    }

    public var fajrAngle: Double {
        switch self {
        case .mwl, .karachi, .kuwait, .qatar, .diyanet: return 18.0
        case .isna: return 15.0
        case .egypt, .gulf: return 19.5
        case .makkah: return 18.5
        case .tehran: return 17.7
        case .france: return 12.0
        }
    }

    public var ishaAngle: Double {
        switch self {
        case .mwl, .diyanet: return 17.0
        case .isna: return 15.0
        case .egypt, .kuwait: return 17.5
        case .makkah, .gulf, .qatar: return 0.0 // Uses interval
        case .karachi: return 18.0
        case .tehran: return 14.0
        case .france: return 12.0
        }
    }

    public var ishaIntervalMin: Int {
        switch self {
        case .makkah, .gulf, .qatar: return 90
        default: return 0
        }
    }

    public var maghribAngle: Double {
        switch self {
        case .tehran: return 4.5
        default: return 0.0
        }
    }
}

public enum JuristicMethod: String, CaseIterable, Codable {
    case standard = "STANDARD"
    case hanafi = "HANAFI"

    public var title: String {
        switch self {
        case .standard: return "Standard (Shafi'i, Maliki, Hanbali)"
        case .hanafi: return "Hanafi"
        }
    }

    public var shadowFactor: Double {
        return self == .hanafi ? 2.0 : 1.0
    }
}

public enum HighLatitudeRule: String, CaseIterable, Codable {
    case none = "NONE"
    case middleOfNight = "MIDDLE_OF_NIGHT"
    case oneSeventh = "ONE_SEVENTH"
    case angleBased = "ANGLE_BASED"

    public var title: String {
        switch self {
        case .none: return "None"
        case .middleOfNight: return "Middle of the Night"
        case .oneSeventh: return "One-Seventh of the Night"
        case .angleBased: return "Angle-Based"
        }
    }
}

public struct City: Codable, Identifiable, Equatable {
    public var id: String { "\(name)_\(country)" }
    public let name: String
    public let country: String
    public let latitude: Double
    public let longitude: Double
    public let timezone: Double
    public let elevation: Double
    public let timeZoneId: String

    public init(name: String, country: String, latitude: Double, longitude: Double, timezone: Double, elevation: Double = 0.0, timeZoneId: String = "America/Toronto") {
        self.name = name
        self.country = country
        self.latitude = latitude
        self.longitude = longitude
        self.timezone = timezone
        self.elevation = elevation
        self.timeZoneId = timeZoneId
    }

    public static let `default` = City(
        name: "Waterloo",
        country: "Canada",
        latitude: 43.4643,
        longitude: -80.5204,
        timezone: -5.0,
        elevation: 329.0,
        timeZoneId: "America/Toronto"
    )
}
