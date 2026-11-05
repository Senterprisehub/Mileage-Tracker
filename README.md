# Mileage Tracker

Mileage Tracker is an Android app for recording vehicle trips, reviewing mileage and estimated driving costs, and exporting trip data. The project describes itself as a private, subscription-free mileage and fuel expense tracker with automatic drive detection and support for multiple vehicles.

> **Release note:** GitHub currently lists the APK asset `Mileage.Tracker.-.v3.1.apk` in the `v3.1` release. The Gradle configuration in this repository currently declares `versionName = "2.0"` and `versionCode = 10`; these values do not match the release asset label. Confirm the APK's actual package metadata and signing details before treating the source configuration as a description of the published binary.

## What it does

- **Trip tracking:** GPS- and activity-based automatic drive tracking, plus manual trip entry. A foreground location service is declared for ongoing tracking.
- **Trip review:** Track distance, duration, locations, and trip notes; categorize trips for business, personal, medical, or charity use as supported by the app UI.
- **Vehicle profiles and cost estimates:** Keep vehicle settings and use them to estimate fuel consumption, fuel expense, and maintenance costs.
- **Fuel prices:** The project advertises current city fuel-price information. Network access is declared, so live price lookups require connectivity; do not assume every app feature works offline.
- **Reports:** Export mileage and trip information in PDF and CSV formats, as advertised by the project.
- **Tracking controls:** Configure automatic tracking and related pause/resume behavior in the app.

Feature descriptions above summarize the repository's existing product documentation and app configuration. They are not a reverse-engineering report of every screen or behavior in the binary.

## Privacy and permissions

The manifest requests internet and network-state access, precise/coarse and background location, activity recognition, notifications, foreground location service, wake lock, boot notification handling, and related battery/alarm permissions. These are sensitive permissions. Grant only the permissions needed for the tracking behavior you choose, and review Android's permission and battery settings on your device.

The project presents trip records as local-first/private. However, the manifest includes internet access and the product advertises online fuel-price lookup. Review the app and its network behavior for your own privacy requirements; this README does not certify that no data is transmitted.

## Get the app

1. Open the [latest GitHub release](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest) in an external browser.
2. Download the APK attached to the release.
3. Open the downloaded APK and follow Android's installation prompts. You may need to allow installation from that browser or file manager in Android settings. Revoke that permission afterward if you do not need it.
4. Review the requested permissions before enabling automatic background tracking.

Android may show a chooser when opening a release download link if multiple eligible browsers are installed and no default is selected. Websites cannot guarantee or force a system chooser on every device or embedded browser. If the link does not open externally, use the in-app browser's **Open in browser** menu option.

### Latest release

- Release: [`v3.1`](https://github.com/Senterprisehub/Mileage-Tracker/releases/tag/v3.1)
- APK asset listed: `Mileage.Tracker.-.v3.1.apk`
- For the latest version, always use the [latest release page](https://github.com/Senterprisehub/Mileage-Tracker/releases/latest).

## Build from source

This is an Android Gradle project written primarily in Kotlin, using Jetpack Compose and Room among its dependencies.

### Requirements

- Android Studio with a compatible Android Gradle Plugin / Gradle installation
- JDK 11-compatible toolchain (the app's Java source/target compatibility is configured to 11)
- Android SDK matching the project's compile SDK configuration

### Build

```bash
git clone https://github.com/Senterprisehub/Mileage-Tracker.git
cd Mileage-Tracker
./gradlew assembleDebug
```

The debug APK is normally written under `app/build/outputs/apk/debug/`. On Windows, use `gradlew.bat assembleDebug`.

For a release build, configure the signing values expected by `app/build.gradle.kts` (`KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`) and provide the required upload keystore/key alias. Never commit keystore files or passwords. The release build is signed using the `upload` alias configured in the app module.

## Project layout

```text
.
├── app/                 # Android application module and manifest
├── gradle/              # Gradle wrapper configuration
├── index.html           # GitHub Pages landing page
├── build.gradle.kts     # Root Gradle plugin configuration
└── settings.gradle.kts  # Gradle project settings
```

## Technical details

- Application ID configured in source: `com.aistudio.mileagetracker.kxmpzq`
- Source `versionName`: `2.0` (see release note above)
- Source `versionCode`: `10`
- Minimum SDK: 24 (Android 7.0)
- Target SDK: 36
- Kotlin / Jetpack Compose UI
- Room persistence libraries and Google Play Services Location are declared as dependencies

These are values from the repository's current Gradle configuration and may differ from the APK attached to a release. Verify the APK independently when binary-specific details matter.

## Landing page download behavior

The root `index.html` requests the latest GitHub release through the Releases API and uses its APK asset URL when available. On Android, the download button issues an `intent://` URL for the system to resolve as an external `VIEW` action. Android may display an app chooser; behavior depends on Android, available handlers, user defaults, and the hosting in-app browser. A web page cannot reliably force a chooser on all platforms. On iOS and desktop, the button opens the release asset in a new browser tab.

## License

No license file was present in the repository when this README was prepared. Unless a license is added, do not assume the source is available for reuse, modification, or redistribution under an open-source license.
