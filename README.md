# SCREAM 🐙

SCREAM is an offline-first, peer-to-peer communication platform for internet outages, wildfire response, remote field work, transport interruptions, and other conditions where ordinary infrastructure is unavailable or unreliable. Nearby Android devices discover one another and carry public posts, rooms, private conversations, voice, and media across a local mesh.

SCREAM is civilian resilience software, not a substitute for emergency services, a military communications system, or a guarantee of delivery.

In SCREAM, every message you send is a **scream** — a local voice in the nearby mesh. The name and blue logo represent free expression: say what matters, even when networks are unavailable.

## Aim


- Keep communication available when internet infrastructure fails.
- Provide public and private communication modes without requiring a central server.
- Forward messages through nearby devices with bounded hop counts and duplicate suppression.
- Keep content local by default and expire retained messages and posts after 48 hours.
- Make security status, transport status, and uncertainty visible to the user.
- Reduce unnecessary surveillance exposure; SCREAM does not promise anonymity.

## Current Features

- Profile onboarding with alias and avatar.
- Nearby public feed for screams/posts.
- Rooms for group conversations.
- Private rooms with nearby peers.
- Local-network peer discovery and message sharing.
- Message envelopes with IDs, timestamps, TTL, and duplicate filtering.
- Automatic security admission: encrypted-envelope checks, replay/size/rate
  limits, temporary peer quarantine, build-integrity checks, and route fallback.
- Keystore-wrapped identity key material and one-device/one-peer BLE deduplication.
- Local persistence for posts and chat messages.
- Automatic cleanup after 48 hours.
- Message routing uses a bounded TTL/hop limit; the 48-hour lifetime is separate from route reach.
- Manual delete button for your own chat messages.
- Blue SCREAM app icon and theme.

## Compose Multiplatform Foundation

SCREAM is being migrated incrementally to a shared Compose Multiplatform architecture:

- `shared/` contains common Compose UI and platform-neutral contracts.
- `desktopApp/` is the desktop JVM application entry point.
- `app/` remains the existing Android product while screens and business logic move into `shared/src/commonMain`.

Build the shared and desktop foundation with:

```powershell
.\gradlew.bat :shared:compileKotlinDesktop :desktopApp:compileKotlin
```

The first shared shell is intentionally small. Existing Android feed, rooms, chat, identity, and mesh features remain available during migration.

## Platform and technology

The Android app is written in Kotlin with Jetpack Compose and Material 3. It
uses Android APIs for BLE/GATT, LAN sockets, notifications, DataStore, and
local SQLite persistence. The current compatibility boundary is `minSdk 26`
(Android 8.0), `targetSdk 34`, and `compileSdk 34`. Hardware support still
depends on the device’s Bluetooth, Wi-Fi, background-execution, and
power-management behavior.

## Important Network Note

The current app has a working local P2P foundation over LAN discovery and TCP message sharing. The source also includes BLE advertising/scanning plus a BLE GATT client/server transport foundation. Production-grade Bluetooth UX, permission education, and resumable encrypted file/document transfer are still roadmap work.

The scalable direction is a mesh:

- Each phone connects only to nearby peers.
- Messages are forwarded with TTL/hop limits.
- Duplicate message IDs prevent loops.
- The network grows as more people nearby install the app.

A single phone cannot directly connect to millions or billions of devices. The powerful version is many nearby devices forwarding safely as a mesh.

## APK Location

After building, the debug APK is created here:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This project also keeps a convenient copy at:

```text
SCREAM-debug.apk
```

## Build APK

From the project root:

```powershell
.\gradlew.bat :app:assembleDebug
```

Then copy the APK if needed:

```powershell
Copy-Item app\build\outputs\apk\debug\app-debug.apk SCREAM-debug.apk -Force
```

On a normal developer machine with Gradle installed, this also works:

```powershell
gradle :app:assembleDebug
```

## Install APK

Connect an Android phone with USB debugging enabled, then run:

```powershell
adb install -r SCREAM-debug.apk
```

Or manually copy `SCREAM-debug.apk` to your phone and open it from the file manager.

## Run For Testing

1. Install the APK on two Android devices.
2. Connect both devices to the same Wi‑Fi network.
3. Open SCREAM and create a profile on each phone.
4. Watch the mesh status and peer count.
5. Send a scream/post or create a room.
6. Messages should appear on nearby connected devices.

## Data Lifetime

SCREAM stores posts and chat messages locally on the phone. Content automatically disappears after 48 hours.

Users can also delete their own chat messages manually.

## Roadmap

- True Bluetooth/BLE nearby discovery.
- Better runtime permission flow.
- Delivery status and retry queue.
- Local database storage for stronger persistence.
- Per-peer/per-room end-to-end session keys using an audited key-exchange design.
- File/document sharing with accept/decline controls.
- Production-grade mesh routing across multiple hops.
- QR bootstrap and encrypted file transfer. QR is an encoding, not an encryption
  method; every import must be authenticated and user-confirmed.
- High-assurance per-peer and per-room E2E sessions with audited key exchange.
- Resumable encrypted file transfer with explicit consent, cancellation, and
  integrity verification.
- Offline stories: short-lived local story capsules with an explicit audience,
  expiry policy, and mesh propagation budget.
- Mesh-aware local search without uploading a global contact graph.

See `docs/PROJECT_PLAN.md` for the full roadmap.

## Security and safety

Read [SECURITY.md](SECURITY.md) for the threat model and current protections,
and [SAFETY.md](SAFETY.md) before using SCREAM during an outage or emergency.
SCREAM is experimental software and must not be the only emergency channel.

## AI Maintenance Docs

Future AI agents should start with `AGENTS.md` and `docs/ai-context/README.md` before scanning the project. The docs in `docs/ai-context/` map prompts to the exact source areas to inspect, which keeps maintenance work token-efficient.

## License

SCREAM is open source under the MIT License. See `LICENSE`. The license grants
permission to use and modify the software; it is not a security certification,
availability guarantee, or authorization to use the app in life-critical
operations.
