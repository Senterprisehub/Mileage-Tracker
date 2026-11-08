# Mileage Tracker

Put mileage and vehicle expenses on autopilot. **Mileage Tracker** is an Android app for logging drives, keeping vehicle costs organized, and exporting trip records as PDF or CSV.

[**Download the latest APK**](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest) · [Project landing page](https://senterprisehub.github.io/Mileage-Tracker/) · [Browse the source](https://github.com/Senterprisehub/Mileage-Tracker)

## What you can do

- **Log drives automatically or manually.** Background location and motion-based detection can record drives; you can also enter trips yourself. Automatic tracking depends on Android permissions and device settings.
- **Organize trip history.** Review distance and duration, add notes, and categorize drives as business, personal, medical, or charity.
- **Manage multiple vehicles.** Keep vehicle profiles and assign trips to the right vehicle.
- **Understand estimated costs.** Use vehicle efficiency and configured rates to estimate fuel use, fuel expense, maintenance, tolls, parking, and mileage deductions. Estimates depend on the trip and vehicle information you provide.
- **Check fuel-price information.** The app can look up city fuel prices in India when online and use built-in reference rates as a fallback. Prices and estimates may not reflect the final amount paid.
- **Export and back up records.** Create PDF or CSV reports, share them, and back up or restore app data.
- **Keep records on your device.** Trip and vehicle records are stored locally. Network access is used for online fuel-price lookup; location access is needed for tracking.

## Install

1. Visit [GitHub Releases](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest) and download the APK attached to the latest release.
2. Open the APK on your Android device and follow the installation prompts. If Android asks you to allow installs from your browser or file manager, you can turn that permission off again after installation.
3. Review the app's permissions. Grant the location, activity-recognition, and notification permissions you need before enabling automatic background tracking.

The current source version is **3.2** (version code **13**). Use the [latest release page](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest) for the current APK and release notes.

## Build from source

This is a native Android application written primarily in Kotlin, using Jetpack Compose for UI and Room for local data. To build it:

1. Open this repository in a compatible version of Android Studio.
2. Allow Gradle to sync and install the Android SDK required by the project (compile SDK 36, extension level 1).
3. Build and run the `app` configuration on a device or emulator running Android 7.0 (API 24) or later.

A signed release build also needs the signing configuration referenced in `app/build.gradle.kts` (`KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`). Do not commit keystores or signing secrets.

## App details

| | |
| --- | --- |
| Application ID | `com.aistudio.mileagetracker.kxmpzq` |
| Current source version | `3.2` (version code `13`) |
| Minimum Android version | Android 7.0 (API 24) |
| Target SDK | 36 |
| Main technologies | Kotlin, Jetpack Compose, Room, Google Play Services Location |

Automatic tracking uses a foreground location service. Depending on your Android version and the features you enable, Android may request precise or background location, activity recognition, and notification permissions. Manual trip entry is available if you do not want to enable automatic tracking. You can manage permissions in Android settings at any time.

## Project layout

```text
.
├── app/                 # Android application module and source
├── gradle/              # Gradle version catalog and wrapper configuration
├── index.html           # GitHub Pages landing page
├── README.md
├── build.gradle.kts
└── settings.gradle.kts
```

## License

No license file is currently present. Until one is added, do not assume the source is available for reuse, modification, or redistribution under an open-source license.
