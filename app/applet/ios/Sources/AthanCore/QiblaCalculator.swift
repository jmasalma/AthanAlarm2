import Foundation

public enum QiblaCalculator {
    public static let kaabaLatitude: Double = 21.422487
    public static let kaabaLongitude: Double = 39.826206

    /// Calculates Qibla direction in degrees clockwise from True North (0-360)
    public static func calculateBearing(fromLatitude lat: Double, fromLongitude lon: Double) -> Double {
        let lat1 = lat * .pi / 180.0
        let lon1 = lon * .pi / 180.0
        let lat2 = kaabaLatitude * .pi / 180.0
        let lon2 = kaabaLongitude * .pi / 180.0

        let dLon = lon2 - lon1
        let y = sin(dLon) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)

        var bearing = atan2(y, x) * 180.0 / .pi
        bearing = (bearing.truncatingRemainder(dividingBy: 360.0) + 360.0).truncatingRemainder(dividingBy: 360.0)
        return bearing
    }

    /// Calculates geodesic distance to the Kaaba in kilometers (Haversine formula)
    public static func distanceToKaabaKm(fromLatitude lat: Double, fromLongitude lon: Double) -> Double {
        let r = 6371.0 // Earth radius in km
        let dLat = (kaabaLatitude - lat) * .pi / 180.0
        let dLon = (kaabaLongitude - lon) * .pi / 180.0

        let lat1 = lat * .pi / 180.0
        let lat2 = kaabaLatitude * .pi / 180.0

        let a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(lat1) * cos(lat2) * sin(dLon / 2.0) * sin(dLon / 2.0)
        let c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
