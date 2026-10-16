# Standalone Native iOS App (AthanAlarm2-iOS) Plan

Architecture, repository setup, and GitHub Actions build blueprint for building the native iOS companion app in a dedicated GitHub repository named **`AthanAlarm2-iOS`** using Swift, SwiftUI, and WidgetKit, producing sideloadable IPAs for AltStore / TrollStore.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions have been confirmed:
> - **Repository Name**: **`AthanAlarm2-iOS`** (e.g. `https://github.com/jmasalma/AthanAlarm2-iOS`), aligning directly with the Android `AthanAlarm2` branding.
> - **Repository Strategy**: Separate standalone repository rather than a monorepo, keeping the Android Gradle codebase clean and preventing CI minute consumption.
> - **Tech Stack**: Native Swift & SwiftUI with WidgetKit, UserNotifications, CoreLocation, and AVFoundation.
> - **Distribution Format**: Automated GitHub Actions on `macos-14`/`macos-15` creating an ad-hoc signed or development `.ipa` package for sideloading via AltStore, SideStore, Sideloadly, or TrollStore.

- **Confirmed Decision 1**: Repository named `AthanAlarm2-iOS`.
- **Confirmed Decision 2**: Separate dedicated repo (`jmasalma/AthanAlarm2-iOS`).
- **Confirmed Decision 3**: Pure native SwiftUI + WidgetKit + AVFoundation.
- **Confirmed Decision 4**: GitHub Actions workflow generating `AthanAlarm2.ipa` on release tags.

---

## 1. Overview & Core Concept

- **What It Delivers**:
  A complete native iOS companion to AthanAlarm2, featuring accurate offline Islamic prayer time calculations, custom calculation methods, configurable sound alerts (gentle chime, full adhan, system tones), Home Screen and Lock Screen widgets (WidgetKit), Qibla compass (CoreLocation), and automated GitHub Actions build pipelines.
- **Target Audience**:
  iPhone and iPad users who want a private, privacy-respecting, zero-ad prayer alarm app with reliable local notifications and live widgets.
- **Key Value**:
  Matching complete feature parity with the Android app while adhering strictly to iOS Human Interface Guidelines and Apple background execution patterns.

---

## 2. User Experience & Visual Design

### Key User Flows
1. **Prayer Times Dashboard**:
   - Clean golden/emerald Islamic aesthetic mirroring the Android design.
   - Header with current Islamic Hijri date, Gregorian date, selected city, and live countdown timer to the next prayer.
   - Vertical prayer schedule listing Fajr, Sunrise, Dhuhr, Asr, Maghrib, and Isha with the active/upcoming prayer highlighted.
   - Tap any prayer alert icon to toggle notification mode (Full Athan, Gentle Chime, Notification Only, Disabled) with an instant floating toast/banner.
2. **Widgets (WidgetKit)**:
   - **Small Widget (`systemSmall` - 2x2 on iOS)**: Displays next prayer name in English, prayer time, and countdown.
   - **Medium Widget (`systemMedium`) / Lock Screen (`accessoryRectangular`)**: Displays the upcoming prayers and daily schedule.
3. **Qibla Compass**:
   - Smooth 60fps compass dial utilizing `CoreLocation` and `CoreMotion` heading sensors pointing directly toward the Kaaba in Mecca.
4. **Settings & Audio Options**:
   - City search / GPS auto-detect.
   - Fiqh calculation methods (ISNA, MWL, Makkah, Egypt, Karachi, etc.).
   - Sound selection: Gentle Chime (default), Built-in Adhan, or System Alert Tones.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Separate Repository (`AthanAlarm2-iOS`) vs. Monorepo**:
  - *Chosen Approach*: Separate dedicated repository (`AthanAlarm2-iOS`).
  - *Why*:
    1. Android Studio (Gradle) and Xcode have fundamentally different toolchains and build lifecycles.
    2. GitHub Actions billing: Linux runners are cheap/free for Android builds; macOS runners for iOS consume 10x workflow minutes. Separating repos prevents unnecessary cross-triggering.
    3. Independence: Version tags and release assets (APK vs. IPA) remain clean and unentangled.
- **Decision 2: Native Swift/SwiftUI vs. Cross-Platform Framework**:
  - *Chosen Approach*: Pure Swift and SwiftUI.
  - *Why*: iOS background audio (`AVAudioSession`), exact scheduled notifications (`UNCalendarNotificationTrigger`), and interactive Lock Screen widgets (`WidgetKit`) are significantly more reliable and idiomatic in native Swift.
- **Decision 3: Sideloading IPA Distribution**:
  - *Chosen Approach*: GitHub Actions creates an ad-hoc or development-signed `.ipa` that can be imported directly into AltStore, SideStore, Sideloadly, or TrollStore without requiring an expensive paid Apple Developer Program account.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                 AthanAlarm2-iOS App Architecture            │
├────────────────────────────────┬────────────────────────────┤
│           UI Layer             │       Widget Extension     │
│   ┌────────────────────────┐   │   ┌────────────────────┐   │
│   │ SwiftUI Views          │   │   │ WidgetKit Timeline │   │
│   │ • PrayerScheduleView   │   │   │ • NextPrayerWidget │   │
│   │ • QiblaCompassView     │   │   │ • DailySchedule    │   │
│   │ • SettingsView         │   │   └─────────┬──────────┘   │
│   └───────────┬────────────┘   │             │              │
├───────────────┼────────────────┴─────────────┼──────────────┤
│               ▼                              ▼              │
│       Shared App Group / UserDefaults (AppStorage)          │
│               │                              │              │
├───────────────┴──────────────────────────────┴──────────────┤
│                     Core Business Logic                     │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ • PrayerCalculationEngine (Astronomical Solar Math) │   │
│   │ • NotificationScheduler (UNUserNotificationCenter)  │   │
│   │ • AudioPlaybackManager (AVAudioPlayer & Sessions)   │   │
│   │ • QiblaEngine (Spherical Great-Circle Math)         │   │
│   └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                               ▲
                               │
               ┌───────────────┴───────────────┐
               │    GitHub Actions (macOS)     │
               │ • xcodebuild archive          │
               │ • Package Payload -> .ipa     │
               │ • Release Upload to GitHub    │
               └───────────────────────────────┘
```

### Data Storage & App Groups
- **UserDefaults with App Group (`group.com.athanalarm2`)**:
  Allows the main SwiftUI app and the WidgetKit extension to read the same saved city, calculation preferences, and prayer offsets.
- **Local Calculation**:
  Astronomical algorithms (declination, equation of time, transit) ported directly from the existing Kotlin engine into Swift, running 100% offline.

### GitHub Actions Workflow (`.github/workflows/ios.yml`)
- Runs on `macos-14` / `macos-15`.
- Steps:
  1. Checkout repository `AthanAlarm2-iOS`.
  2. Select Xcode version.
  3. Build & archive via `xcodebuild -scheme AthanAlarm2 -configuration Release -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO`.
  4. Package `.app` into `Payload/` folder and zip into `AthanAlarm2.ipa`.
  5. Upload artifact and attach to GitHub Releases on tag push (`v*`).

---

## 5. Next Steps & Repository Setup

1. Create the repository on GitHub: `https://github.com/jmasalma/AthanAlarm2-iOS`.
2. Initialize with Xcode project template, WidgetKit extension, astronomical calculations, and audio assets.
3. Configure the GitHub Actions workflow for automated IPA releases.
4. Keep the Android repository (`AthanAlarm2`) dedicated to Android releases.
