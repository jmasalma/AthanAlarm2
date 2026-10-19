# Privacy Policy for Athan Alarm

**Package:** `com.masalma.athanalarm`  
**Effective Date:** October 19, 2026  
**Last Updated:** October 19, 2026  

Athan Alarm ("we," "our," or "us") respects your privacy and is firmly committed to protecting your personal data. This Privacy Policy explains our practices regarding the collection, use, and disclosure of information when you install and use the **Athan Alarm** mobile application (the "App").

Please read this Privacy Policy carefully. By using Athan Alarm, you agree to the collection and use of information in accordance with this policy.

---

## 1. Information We Collect and Process

### A. Location Data
* **Precise and Approximate Location:** Athan Alarm requires access to your device's location (GPS coordinates, network location) to calculate accurate astronomical solar prayer times (Fajr, Sunrise, Dhuhr, Asr, Maghrib, and Isha) and determine the spherical great-circle Qibla direction relative to your physical location.
* **100% On-Device Processing:** All astronomical calculations and Qibla alignments are performed entirely on your local device. Your location coordinates are **never** uploaded, transmitted, logged, or stored on any external server or third-party service.
* **Manual Coordinates Alternative:** If you prefer not to grant location permissions, you may select any city from our built-in offline database or manually input latitude and longitude coordinates in the App settings.

### B. Device Preferences & Local Information
* **Local Storage:** The App stores your user preferences (such as selected athan sounds, notification alert modes, calculation methods, juristic rules, and minute offsets) locally on your device using standard Android SharedPreferences / DataStore. This data never leaves your device.

---

## 2. Information We DO NOT Collect or Share

* **No Personal Identity Data:** We do not collect personal identification information such as your name, email address, phone number, contact lists, photos, or physical mailing address.
* **No Account Registration:** No account, sign-in, or social login is required to access all features.
* **No Tracking or Third-Party Analytics:** We do not use third-party tracking SDKs, advertising identifiers (AAID), or analytical frameworks to monitor your behavior.
* **No Selling or Sharing of Data:** We do not sell, rent, trade, monetize, or transfer your location data or device information to third parties under any circumstances.

---

## 3. How We Use Your Information

The limited data processed by the App is strictly used for core religious functional purposes:
* Computing accurate local daily prayer schedules based on astronomical celestial equations.
* Triggering local notification alarms and athan audio precisely at scheduled prayer times.
* Displaying real-time directional compass alignment toward the Kaaba in Mecca.

---

## 4. App Permissions

To deliver its core features, Athan Alarm may request the following device permissions:
* **Location Permission (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`):** Required to retrieve local coordinates for prayer time and Qibla azimuth computation.
* **Notifications (`POST_NOTIFICATIONS`):** Required on Android 13+ to display prayer reminder notices and playback controls in the status bar and lock screen.
* **Exact Alarms (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`):** Required to trigger alarms precisely at the exact second prayer time arrives, even during system Doze mode.
* **Foreground Service (`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK`):** Required to play athan audio smoothly in the background when the screen is locked.

You may revoke any permission at any time through your device's Android System Settings.

---

## 5. Children's Privacy

Athan Alarm is suitable for users of all ages, including children and families. We do not collect or solicit personal information from children under the age of 13 (or under 16 in certain jurisdictions), in full compliance with COPPA and GDPR requirements.

---

## 6. Security

We prioritize the security of your data. Because all preference configurations and calculation data remain exclusively on your local device storage, your information is protected by your device's native operating system security measures (sandboxing and encryption).

---

## 7. Changes to This Privacy Policy

We may update our Privacy Policy periodically. Any updates will be posted to this document and our public webpage with a revised "Last Updated" date.

---

## 8. Contact Us

If you have any questions, feedback, or concerns regarding this Privacy Policy, please contact us at:

* **Developer:** J. Masalma
* **Email:** developer@masalma.com  
* **Website:** https://masalma.com  
* **Repository:** https://github.com/jmasalma/AthanAlarm2
