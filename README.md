# Mileage Tracker

Put mileage and vehicle expenses on autopilot. Mileage Tracker is an Android app for automatic and manual drive logging, multi-vehicle records, fuel and maintenance estimates, and PDF or CSV exports.

[Download the latest APK](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest) · [Project landing page](https://senterprisehub.github.io/Mileage-Tracker/) · [View the source](https://github.com/Senterprisehub/Mileage-Tracker)

## Features

- **Automatic and manual trips:** Detect drives in the background using location and motion, or add a trip manually. Automatic detection and background tracking require Android permissions and may be affected by device settings.
- **Trip history:** Review routes, distance, duration, and notes. Categorize drives as business, personal, medical, or charity, and edit trip details after a drive.
- **Multiple vehicles:** Maintain vehicle profiles with fuel type, efficiency, and maintenance rates; assign trips to vehicles and compare vehicle totals.
- **Fuel and cost estimates:** Estimate fuel use, fuel expense, maintenance, tolls, parking, and business mileage deductions using the app's settings. Estimates depend on the information and rates you enter or select; they are not tax or financial advice.
- **Fuel-price information:** The app can attempt to fetch live city fuel prices for India and uses built-in country/city reference rates as fallbacks. Live price lookup requires an internet connection and may not be available in every location or for every fuel type.
- **Reports and backups:** Export trip information as PDF or CSV, share reports, and back up or restore app data.
- **Tracking controls:** Configure automatic tracking, speed threshold, pause/resume behavior, and saved places.
- **Local-first records:** Trip and vehicle records are stored on your device. Location tracking requires permission; online fuel-price lookup uses network access.

## Get the app

1. Open the [latest GitHub release](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest).
2. Download the APK attached to that release.
3. Open the APK and follow Android's installation prompts. Android may ask you to allow installs from the browser or file manager; turn that permission off again afterward if you enabled it.
4. Review the app's location, activity, and notification permissions before enabling automatic background tracking.

The repository currently has a `v3.1` release and the app source declares version `3.1` (version code `12`). For the current APK and its release notes, use the [latest release page](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest). APK availability and contents are determined by the release, not by the source version alone.

## Build from source

This is an Android project written primarily in Kotlin with Jetpack Compose and Room.

### Requirements

- Android Studio with a compatible Android Gradle Plugin and Gradle installation
- JDK 11-compatible toolchain
- Android SDK for the project's compile SDK (API 36, extension level 1)

Open the project in Android Studio and build the `app` module. For a signed release build, configure the signing values used by `app/build.gradle.kts` (`KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`) and the expected upload key alias. Never commit keystore files or passwords.

## App details

- Application ID: `com.aistudio.mileagetracker.kxmpzq`
- Source version: `3.1` (version code `12`)
- Minimum Android version: Android 7.0 (API 24)
- Target SDK: 36
- Main technologies: Kotlin, Jetpack Compose, Room, Google Play Services Location

Automatic tracking uses foreground location services and may request precise/background location, activity recognition, notifications, and other system permissions. You can use manual trip entry if you do not want to enable automatic tracking. Fuel-price refreshes require network access; trip tracking and saved records are designed to be local-first.

## Project layout

```text
.
├── app/                 # Android application module and source
├── gradle/              # Gradle configuration
├── index.html           # GitHub Pages landing page
├── README.md
├── build.gradle.kts
└── settings.gradle.kts
```

## License

No license file is currently present. Until one is added, do not assume the source is available for reuse, modification, or redistribution under an open-source license.
