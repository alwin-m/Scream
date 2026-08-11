# Project Overview

## Product

SCREAM is an offline-first civilian resilience communication app for internet
outages, wildfire response, remote field work, transport interruptions, and
other infrastructure failures. It keeps public posts, rooms, private chats,
and media messages working across nearby Android devices without requiring a
central server. Hostels, campuses, and events may be test environments, but
they are not the product’s primary mission.

## Current Repository Shape

- Compose Multiplatform foundation: `:shared` contains common UI and platform-neutral contracts; `:desktopApp` is the desktop JVM entry point.
- Existing native Android app: Kotlin, Jetpack Compose, Material 3, Android Gradle Plugin 8.3.0, Kotlin 1.9.22. It remains intact as the first migration consumer.
- Modules: `:app` (current Android product), `:shared` (KMP Android/Desktop library), and `:desktopApp` (desktop application).
- Local state: in-memory `StateFlow`s backed by SharedPreferences JSON and DataStore identity.
- Mesh transports: LAN UDP discovery, LAN TCP message delivery, BLE advertising/scanning, BLE GATT client/server message delivery.
- Web companion: static browser UI plus `web/server.py` local Python bridge.
- Tests: no checked-in test source files were found during this audit.

## Android Runtime Lifecycle

1. `MainActivity` renders `AppNavigation` inside `ScreamTheme`.
2. `MainActivity` asks for Bluetooth, location, audio, and notification permissions as needed.
3. `MainActivity` starts `MeshForegroundService`.
4. `AppNavigation` checks `IdentityViewModel.userProfile`.
5. Unregistered users go to `OnboardingScreen`; registered users go to `HomeScreen`.
6. `MainViewModel` initializes `ScreamRepository`, `TransportManager`, and starts `P2pMeshEngine` once a registered user profile is available.
7. `MeshForegroundService` keeps mesh operations running beyond app UI lifetime.

## Core Data Flow

```text
Compose UI
  -> MainViewModel
  -> ScreamRepository
  -> P2pMeshEngine.broadcastPayload(...)
  -> LAN TCP peers and BLE GATT peers

Incoming LAN/BLE message
  -> P2pMeshEngine
  -> dispatchIncomingMessage(...)
  -> ScreamRepository receive/update method
  -> StateFlow update
  -> Compose recomposition
```

## Major Ownership Boundaries

- `identity/`: local profile registration and persisted identity.
- `model/`: serializable-ish Kotlin data shapes used by UI, repository, and network.
- `data/ScreamRepository.kt`: application state, local persistence, post/room/chat business rules, outbound broadcast triggers.
- `ui/MainViewModel.kt`: UI-facing facade over repository and identity flows.
- `ui/*.kt`: Compose screens and local UI helpers.
- `network/P2pMeshEngine.kt`: shared message envelope, encryption, dedupe, TTL, LAN sockets, BLE incoming message dispatch.
- `network/MeshNetworkManager.kt`: BLE advertise/scan lifecycle and discovery.
- `network/BleGattClient.kt`: outbound/inbound GATT client connections.
- `network/BleGattServer.kt`: GATT service host and notifications.
- `web/`: standalone browser prototype and LAN bridge.

The shared app shell is intentionally minimal in the first migration stage. Existing Android screens and mesh implementations remain available while features move into `shared/src/commonMain` incrementally.

## Known Gaps And Risks

- No automated tests are currently present.
- Repository persistence is SharedPreferences JSON, not a database.
- Android and web protocols are not fully aligned: Android expects encrypted `encryptedData`, while the web bridge uses plain `data`.
- The BLE UI screen is mostly a visual/manual scanning screen; real BLE mesh lifecycle is controlled by `MeshForegroundService` and `MeshNetworkManager`.
- `MeshInfoBottomSheet` mixes real peers with simulated display peers for a richer topology visualization.
- The mesh encryption key is a static app-wide string-derived key, not per-room or per-peer E2E key exchange.
- Permission handling is staged: nearby-discovery permissions are requested at startup, while microphone/camera access is requested only when the related feature is used. The mesh service can remain visible and retry discovery after a denial.
- `AutoSecurityGuard` provides fail-closed envelope validation, rate limiting, temporary route quarantine, and build-signing mismatch detection. This is defense-in-depth, not a replacement for per-peer E2E session keys.
- BitChat private key material is wrapped by Android Keystore; the local database and media blobs still need at-rest encryption work.

## Roadmap Source

The older strategic roadmap is in `docs/PROJECT_PLAN.md`. Use it for product direction, but use this `docs/ai-context` folder for current implementation truth.

