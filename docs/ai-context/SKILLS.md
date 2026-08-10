# Prompt Routing Skills

Use this as the first decision table for future maintenance tasks.

## If The Prompt Mentions App Screens, Visuals, Layout, Buttons, Colors

Read:

- `UI_UX.md`
- `FILE_REPORTS.md` sections: UI screens, theme, resources

Likely files:

- `app/src/main/java/com/scream/app/ui/HomeScreen.kt`
- `app/src/main/java/com/scream/app/ui/FeedScreen.kt`
- `app/src/main/java/com/scream/app/ui/RoomsScreen.kt`
- `app/src/main/java/com/scream/app/ui/ChatScreen.kt`
- `app/src/main/java/com/scream/app/ui/MeshInfoBottomSheet.kt`
- `app/src/main/java/com/scream/app/ui/theme/*`

## If The Prompt Mentions User Profile, Onboarding, Alias, Avatar, Age, Gender

Read:

- `DATA_STORAGE.md`
- `UI_UX.md`
- `FILE_REPORTS.md` sections: identity, models

Likely files:

- `app/src/main/java/com/scream/app/identity/UserPreferencesRepository.kt`
- `app/src/main/java/com/scream/app/identity/IdentityViewModel.kt`
- `app/src/main/java/com/scream/app/identity/OnboardingScreen.kt`
- `app/src/main/java/com/scream/app/model/Models.kt`

## If The Prompt Mentions Feed, Posts, Likes, Dislikes, Reshares

Read:

- `FUNCTIONS.md`
- `DATA_STORAGE.md`
- `NETWORK.md`
- `FILE_REPORTS.md` sections: data, UI feed

Likely files:

- `app/src/main/java/com/scream/app/data/ScreamRepository.kt`
- `app/src/main/java/com/scream/app/ui/FeedScreen.kt`
- `app/src/main/java/com/scream/app/ui/MainViewModel.kt`
- `app/src/main/java/com/scream/app/network/P2pMeshEngine.kt`

## If The Prompt Mentions Rooms, Private Chat, Chat Messages, Voice, Image, Reactions

Read:

- `FUNCTIONS.md`
- `DATA_STORAGE.md`
- `UI_UX.md`
- `NETWORK.md`

Likely files:

- `app/src/main/java/com/scream/app/data/ScreamRepository.kt`
- `app/src/main/java/com/scream/app/ui/ChatScreen.kt`
- `app/src/main/java/com/scream/app/ui/RoomsScreen.kt`
- `app/src/main/java/com/scream/app/ui/VoiceEditorScreen.kt`
- `app/src/main/java/com/scream/app/network/P2pMeshEngine.kt`

## If The Prompt Mentions Mesh, LAN, UDP, TCP, Routing, Encryption, TTL

Read:

- `NETWORK.md`
- `DATA_STORAGE.md`
- `FILE_REPORTS.md` sections: networking

Likely files:

- `app/src/main/java/com/scream/app/network/P2pMeshEngine.kt`
- `app/src/main/java/com/scream/app/data/ScreamRepository.kt`
- `app/src/main/java/com/scream/app/model/Models.kt`

## If The Prompt Mentions Bluetooth, BLE, GATT, Scanning, Advertising, Permissions

Read:

- `BLUETOOTH.md`
- `NETWORK.md`
- `BUILD_TESTING.md`
- `FILE_REPORTS.md` sections: networking, Android config

Likely files:

- `app/src/main/java/com/scream/app/network/MeshForegroundService.kt`
- `app/src/main/java/com/scream/app/network/MeshNetworkManager.kt`
- `app/src/main/java/com/scream/app/network/BleGattClient.kt`
- `app/src/main/java/com/scream/app/network/BleGattServer.kt`
- `app/src/main/java/com/scream/app/network/BluetoothBootReceiver.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/scream/app/MainActivity.kt`

## If The Prompt Mentions Web, Browser, Local Server, SSE, Python

Read:

- `WEB.md`
- `NETWORK.md`
- `FILE_REPORTS.md` sections: web

Likely files:

- `web/server.py`
- `web/app.js`
- `web/index.html`
- `web/style.css`

## If The Prompt Mentions Build, APK, Gradle, Dependencies, Release

Read:

- `BUILD_TESTING.md`
- `FILE_REPORTS.md` sections: build/config

Likely files:

- `build.gradle.kts`
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.properties`
- `README.md`

## If The Prompt Mentions Documentation Or AI Efficiency

Read:

- `README.md`
- `DOC_UPDATE_RULES.md`
- `FILE_REPORTS.md`

Likely files:

- `AGENTS.md`
- `docs/ai-context/*.md`
- `docs/PROJECT_PLAN.md`

