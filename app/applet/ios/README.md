# AthanAlarm2-iOS Setup & Release Guide

Companion guide for launching the native iOS companion app at **`https://github.com/jmasalma/AthanAlarm2-iOS`**.

---

## 1. Create the GitHub Repository

1. On GitHub, create a new public or private repository named:
   **`AthanAlarm2-iOS`**
2. Clone it to your computer:
   ```bash
   git clone https://github.com/jmasalma/AthanAlarm2-iOS.git
   cd AthanAlarm2-iOS
   ```

---

## 2. Copy the Provided Swift Files

The core calculation, data models, and workflow files are prepared in the `ios/` folder:

| File | Purpose |
|---|---|
| `Sources/AthanCore/PrayerCalculationEngine.swift` | 100% offline astronomical prayer calculation matching the Android engine |
| `Sources/AthanCore/PrayerModels.swift` | `PrayerType`, `AlarmMode`, `CalculationMethod`, `City`, and settings models |
| `Sources/AthanCore/QiblaCalculator.swift` | Spherical great-circle bearing and distance to the Kaaba |
| `.github/workflows/ios.yml` | GitHub Actions workflow to build and release `AthanAlarm2.ipa` |

---

## 3. Xcode Project Setup

1. Open Xcode on macOS: **File > New > Project...**
   - Template: **App** (iOS)
   - Product Name: **`AthanAlarm2`**
   - Interface: **SwiftUI**
   - Language: **Swift**
2. Add a Widget Extension:
   - **File > New > Target... > Widget Extension**
   - Product Name: **`AthanAlarmWidgets`**
3. Drag the `AthanCore/` Swift files into your Xcode project and ensure their Target Membership includes both **`AthanAlarm2`** and **`AthanAlarmWidgets`**.
4. In Project Settings > **Signing & Capabilities**:
   - For AltStore/TrollStore sideloading, standard development signing or automated signing with your free Apple ID works seamlessly.

---

## 4. Automated Builds with GitHub Actions

Place `.github/workflows/ios.yml` at the root of `AthanAlarm2-iOS`:

1. Commit and push:
   ```bash
   git add .
   git commit -m "feat: initial iOS app and CI setup"
   git push origin main
   ```
2. To trigger a release:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
3. GitHub Actions will:
   - Spin up a macOS runner.
   - Archive the app.
   - Package it into **`AthanAlarm2.ipa`**.
   - Create a GitHub Release with download links and automatic release notes!

---

## 5. Installing the IPA on iPhone / iPad

Download `AthanAlarm2.ipa` from the GitHub Release onto your device and install with:
- **AltStore / SideStore**: Open AltStore -> My Apps -> "+" -> select `AthanAlarm2.ipa`.
- **TrollStore** (if jailbroken or iOS 14.0–17.0 on supported chips): Tap the IPA -> Open in TrollStore.
- **Sideloadly** (macOS or Windows via USB/Wi-Fi).
