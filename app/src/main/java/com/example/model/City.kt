package com.example.model

import java.util.Calendar
import java.util.TimeZone

data class City(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: Double,
    val elevation: Double = 0.0, // Elevation/Altitude in meters
    val timeZoneId: String = ""
) {
    val displayName: String
        get() = "$name, $country"

    /**
     * Determines the effective UTC offset in hours for this city on a specific date,
     * taking Daylight Saving Time (DST) into account.
     */
    fun getEffectiveTimezone(date: Calendar = Calendar.getInstance()): Double {
        if (timeZoneId.isNotBlank()) {
            try {
                val tz = TimeZone.getTimeZone(timeZoneId)
                return tz.getOffset(date.timeInMillis) / 3600000.0
            } catch (_: Exception) {}
        }

        // If timeZoneId is blank or not found, try to look up matching preset city
        val matchedCity = CITIES.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (matchedCity != null && matchedCity.timeZoneId.isNotBlank()) {
            try {
                val tz = TimeZone.getTimeZone(matchedCity.timeZoneId)
                return tz.getOffset(date.timeInMillis) / 3600000.0
            } catch (_: Exception) {}
        }

        // Check if device default timezone corresponds to this base timezone
        val defaultTz = TimeZone.getDefault()
        val defaultRawHours = defaultTz.rawOffset / 3600000.0
        if (Math.abs(defaultRawHours - timezone) < 0.25) {
            return defaultTz.getOffset(date.timeInMillis) / 3600000.0
        }

        // Approximate from longitude if close to device timezone (within 1.5 hr)
        val approxHoursFromLon = longitude / 15.0
        val deviceOffsetHours = defaultTz.getOffset(date.timeInMillis) / 3600000.0
        if (Math.abs(deviceOffsetHours - approxHoursFromLon) < 1.5) {
            return deviceOffsetHours
        }

        return timezone
    }

    companion object {
        val DEFAULT = City(
            name = "Waterloo",
            country = "Ontario, Canada",
            latitude = 43.4643,
            longitude = -80.5204,
            timezone = -5.0,
            elevation = 329.0,
            timeZoneId = "America/Toronto"
        )

        val CITIES = listOf(
            // Canada (Waterloo & Regional Cities first)
            City("Waterloo", "Ontario, Canada", 43.4643, -80.5204, -5.0, 329.0, "America/Toronto"),
            City("Kitchener", "Ontario, Canada", 43.4516, -80.4925, -5.0, 320.0, "America/Toronto"),
            City("Cambridge", "Ontario, Canada", 43.3616, -80.3144, -5.0, 310.0, "America/Toronto"),
            City("Guelph", "Ontario, Canada", 43.5448, -80.2482, -5.0, 334.0, "America/Toronto"),
            City("Toronto", "Ontario, Canada", 43.6532, -79.3832, -5.0, 76.0, "America/Toronto"),
            City("Mississauga", "Ontario, Canada", 43.5890, -79.6441, -5.0, 156.0, "America/Toronto"),
            City("Hamilton", "Ontario, Canada", 43.2557, -79.8711, -5.0, 102.0, "America/Toronto"),
            City("London", "Ontario, Canada", 42.9849, -81.2453, -5.0, 251.0, "America/Toronto"),
            City("Windsor", "Ontario, Canada", 42.3149, -83.0364, -5.0, 190.0, "America/Toronto"),
            City("Ottawa", "Ontario, Canada", 45.4215, -75.6972, -5.0, 70.0, "America/Toronto"),
            City("Montreal", "Quebec, Canada", 45.5017, -73.5673, -5.0, 36.0, "America/Toronto"),
            City("Quebec City", "Quebec, Canada", 46.8139, -71.2080, -5.0, 98.0, "America/Toronto"),
            City("Calgary", "Alberta, Canada", 51.0447, -114.0719, -7.0, 1045.0, "America/Edmonton"),
            City("Edmonton", "Alberta, Canada", 53.5461, -113.4938, -7.0, 671.0, "America/Edmonton"),
            City("Vancouver", "BC, Canada", 49.2827, -123.1207, -8.0, 70.0, "America/Vancouver"),
            City("Victoria", "BC, Canada", 48.4284, -123.3656, -8.0, 23.0, "America/Vancouver"),
            City("Winnipeg", "Manitoba, Canada", 49.8951, -97.1384, -6.0, 238.0, "America/Winnipeg"),
            City("Halifax", "Nova Scotia, Canada", 44.6488, -63.5752, -4.0, 45.0, "America/Halifax"),

            // United States
            City("New York", "United States", 40.7128, -74.0060, -5.0, 10.0, "America/New_York"),
            City("Chicago", "United States", 41.8781, -87.6298, -6.0, 181.0, "America/Chicago"),
            City("Detroit", "United States", 42.3314, -83.0458, -5.0, 200.0, "America/Detroit"),
            City("Dearborn", "United States", 42.3223, -83.1763, -5.0, 180.0, "America/Detroit"),
            City("Buffalo", "United States", 42.8864, -78.8784, -5.0, 183.0, "America/New_York"),
            City("Los Angeles", "United States", 34.0522, -118.2437, -8.0, 89.0, "America/Los_Angeles"),
            City("San Francisco", "United States", 37.7749, -122.4194, -8.0, 16.0, "America/Los_Angeles"),
            City("Seattle", "United States", 47.6062, -122.3321, -8.0, 53.0, "America/Los_Angeles"),
            City("Houston", "United States", 29.7604, -95.3698, -6.0, 15.0, "America/Chicago"),
            City("Dallas", "United States", 32.7767, -96.7970, -6.0, 131.0, "America/Chicago"),
            City("Washington D.C.", "United States", 38.9072, -77.0369, -5.0, 20.0, "America/New_York"),
            City("Philadelphia", "United States", 39.9526, -75.1652, -5.0, 12.0, "America/New_York"),
            City("Boston", "United States", 42.3601, -71.0589, -5.0, 43.0, "America/New_York"),
            City("Minneapolis", "United States", 44.9778, -93.2650, -6.0, 264.0, "America/Chicago"),
            City("Atlanta", "United States", 33.7490, -84.3880, -5.0, 320.0, "America/New_York"),

            // Islamic World & Middle East
            City("Makkah", "Saudi Arabia", 21.4225, 39.8262, 3.0, 277.0, "Asia/Riyadh"),
            City("Madinah", "Saudi Arabia", 24.4672, 39.6111, 3.0, 608.0, "Asia/Riyadh"),
            City("Jerusalem", "Palestine", 31.7683, 35.2137, 3.0, 754.0, "Asia/Jerusalem"),
            City("Riyadh", "Saudi Arabia", 24.7136, 46.6753, 3.0, 612.0, "Asia/Riyadh"),
            City("Jeddah", "Saudi Arabia", 21.5433, 39.1728, 3.0, 12.0, "Asia/Riyadh"),
            City("Dubai", "United Arab Emirates", 25.2048, 55.2708, 4.0, 5.0, "Asia/Dubai"),
            City("Abu Dhabi", "United Arab Emirates", 24.4539, 54.3773, 4.0, 10.0, "Asia/Dubai"),
            City("Doha", "Qatar", 25.2854, 51.5310, 3.0, 13.0, "Asia/Qatar"),
            City("Kuwait City", "Kuwait", 29.3759, 47.9774, 3.0, 10.0, "Asia/Kuwait"),
            City("Amman", "Jordan", 31.9454, 35.9284, 3.0, 773.0, "Asia/Amman"),
            City("Cairo", "Egypt", 30.0444, 31.2357, 2.0, 23.0, "Africa/Cairo"),
            City("Istanbul", "Turkey", 41.0082, 28.9784, 3.0, 39.0, "Europe/Istanbul"),
            City("Karachi", "Pakistan", 24.8607, 67.0011, 5.0, 8.0, "Asia/Karachi"),
            City("Lahore", "Pakistan", 31.5497, 74.3436, 5.0, 217.0, "Asia/Karachi"),
            City("Islamabad", "Pakistan", 33.6844, 73.0479, 5.0, 507.0, "Asia/Karachi"),
            City("Dhaka", "Bangladesh", 23.8103, 90.4125, 6.0, 4.0, "Asia/Dhaka"),
            City("Jakarta", "Indonesia", -6.2088, 106.8456, 7.0, 8.0, "Asia/Jakarta"),
            City("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, 8.0, 52.0, "Asia/Kuala_Lumpur"),

            // Europe & Worldwide
            City("London", "United Kingdom", 51.5074, -0.1278, 0.0, 11.0, "Europe/London"),
            City("Birmingham", "United Kingdom", 52.4862, -1.8904, 0.0, 140.0, "Europe/London"),
            City("Paris", "France", 48.8566, 2.3522, 1.0, 35.0, "Europe/Paris"),
            City("Berlin", "Germany", 52.5200, 13.4050, 1.0, 34.0, "Europe/Berlin"),
            City("Sydney", "Australia", -33.8688, 151.2093, 10.0, 19.0, "Australia/Sydney"),
            City("Melbourne", "Australia", -37.8136, 144.9631, 10.0, 31.0, "Australia/Sydney")
        )
    }
}
