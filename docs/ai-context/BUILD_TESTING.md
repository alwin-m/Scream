# Build, Testing, And Release

## Stack

- Android Gradle Plugin: 8.3.0
- Kotlin Android plugin: 1.9.22
- Compose Multiplatform plugin: 1.6.10
- Gradle wrapper: 8.7
- compileSdk: 34
- targetSdk: 34
- minSdk: 26
- Compose compiler extension: 1.5.10
- Compose BOM: 2024.02.01

## Main Dependencies

Defined in `app/build.gradle.kts`:

- AndroidX core KTX
- Lifecycle runtime KTX
- Activity Compose
- Compose UI, graphics, tooling preview
- Material3
- Accompanist permissions
- DataStore preferences
- Navigation Compose
- Lifecycle ViewModel Compose
- Material Icons Extended
- JUnit and Android test dependencies
- Shared KMP UI: Compose Runtime, Foundation, Material 3, and UI

## Build Commands

Debug APK:

```powershell
.\gradlew.bat :app:assembleDebug
```

Compose Multiplatform foundation checks:

```powershell
.\gradlew.bat :shared:compileKotlinDesktop :desktopApp:compileKotlin
```

Run the desktop application with:

```powershell
.\gradlew.bat :desktopApp:run
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Convenience copy used by the project:

```text
SCREAM-debug.apk
```

## Web Bridge Command

```powershell
python web\server.py
```

Server URL:

```text
http://localhost:8000
```

## Testing Status

No checked-in unit test or androidTest source files were found during this audit.

Suggested verification by task type:

- UI-only Compose change: build debug APK at minimum.
- Repository/model change: add/update unit tests if a test structure is introduced; otherwise build and manually exercise flows.
- Network/BLE change: build, install on two Android devices, test same Wi-Fi LAN, then test BLE discovery/message path.
- Web change: run `python web\server.py` and exercise browser flows.
- Manifest/permission change: test on Android 12+ and Android 13+ devices/emulators if possible.

Startup permissions are intentionally staged. The activity requests nearby-discovery
permissions only; microphone and camera permissions are requested by the voice/photo
flows when the user invokes them. The app does not automatically open the system
battery-optimization settings screen on launch.

## Generated Artifacts

Do not review or edit by default:

- `.gradle/`
- `app/build/`
- `gradle-bin/`
- `gradle-8.7-bin.zip`
- `SCREAM-debug.apk`

The repository currently shows many generated build artifacts as modified/untracked in git status. Treat those as build output unless the user specifically asks to manage repository hygiene.

