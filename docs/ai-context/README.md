# SCREAM AI Context

Last audited: 2026-07-27

This folder exists so maintenance agents do not reread the whole project for every task. Start here, route the request to the correct topic file, then inspect only the source files named there.

## Project Snapshot

SCREAM is an offline-first nearby communication app. The main product is a native Android app written in Kotlin and Jetpack Compose. It supports onboarding, a public feed, rooms, private chats, voice messages, image messages, message actions, local persistence, LAN mesh messaging, and BLE discovery/GATT message transport.

There is also a separate `web/` prototype and local Python bridge. It serves a browser UI and speaks a simpler LAN mesh protocol over UDP/TCP. Treat it as a companion/demo surface, not as the Android app runtime.

## Read Order

Use this order unless the user asks for broad review:

1. `SKILLS.md` - prompt-to-doc routing.
2. `PROJECT_OVERVIEW.md` - product and current architecture.
3. One focused area doc:
   - `UI_UX.md`
   - `FUNCTIONS.md`
   - `DATA_STORAGE.md`
   - `NETWORK.md`
   - `BLUETOOTH.md`
   - `WEB.md`
   - `BUILD_TESTING.md`
4. `FILE_REPORTS.md` - exact file ownership and update notes.
5. Source files named by the area doc.

Only use broad `rg` scans if the area docs do not answer where the change belongs.

## Important Current-State Corrections

The root `README.md` says true Bluetooth/BLE is future work. The current source now includes BLE advertising/scanning plus a BLE GATT client/server. The most accurate current network description is in `NETWORK.md` and `BLUETOOTH.md`.

The web bridge still uses an older unencrypted payload shape with `data`, while Android's `P2pMeshEngine` now wraps payloads in an encrypted `encryptedData` envelope. Keep this mismatch in mind when changing cross-platform messaging.

## Primary Source Map

- Android entry/navigation: `app/src/main/java/com/scream/app/MainActivity.kt`, `AppNavigation.kt`
- App state/business logic: `app/src/main/java/com/scream/app/ui/MainViewModel.kt`, `app/src/main/java/com/scream/app/data/ScreamRepository.kt`
- Models: `app/src/main/java/com/scream/app/model/Models.kt`
- Identity/onboarding: `app/src/main/java/com/scream/app/identity/*`
- Compose screens: `app/src/main/java/com/scream/app/ui/*`
- Theme: `app/src/main/java/com/scream/app/ui/theme/*`
- LAN/BLE mesh: `app/src/main/java/com/scream/app/network/*`
- Android config: `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`
- Web bridge/demo: `web/*`

## Do Not Read By Default

These are generated, downloaded, or binary-heavy:

- `.gradle/`
- `app/build/`
- `gradle-bin/`
- `gradle-8.7-bin.zip`
- `SCREAM-debug.apk`
- `SCREAM-logo.png`
- launcher PNGs unless the task is about icons/assets

## Documentation Maintenance Rule

When a code change changes behavior, ownership, APIs, routes, permissions, network envelopes, storage schema, or UI workflows, update the matching doc in this folder before finishing.

