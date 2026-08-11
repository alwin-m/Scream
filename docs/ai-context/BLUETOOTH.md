# Bluetooth And BLE

## Current BLE Pieces

- `MeshForegroundService.kt`: starts BLE manager and GATT server in the background.
- `MeshNetworkManager.kt`: advertises and scans for SCREAM BLE service UUID.
- `BleGattServer.kt`: hosts GATT service and characteristics.
- `BleGattClient.kt`: connects to discovered SCREAM GATT servers and sends messages.
- `BluetoothBootReceiver.kt`: starts foreground service on Bluetooth ON or device boot.
- `BluetoothTransferScreen.kt`: visual/manual UI screen, not the real scanner owner.
- `AndroidManifest.xml`: declares Bluetooth, location, foreground-service, wake-lock, boot permissions.

## Permissions

Manifest declares:

- Legacy Bluetooth: `BLUETOOTH`, `BLUETOOTH_ADMIN` with max SDK 30
- Android 12+: `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- Location: `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION`
- Nearby Wi-Fi: `NEARBY_WIFI_DEVICES`
- Foreground service: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- Audio/camera: `RECORD_AUDIO`, `CAMERA`
- Notifications: `POST_NOTIFICATIONS`
- Boot: `RECEIVE_BOOT_COMPLETED`

`MainActivity.requestRequiredPermissions` requests Bluetooth scan/connect/advertise on Android 12+, legacy BLE location only on older Android versions, and notifications on Android 13+. Microphone and camera permissions remain feature-scoped.

## Service Startup

`MeshForegroundService` starts from:

- `MainActivity.startMeshService`
- `BluetoothBootReceiver` on Bluetooth ON
- `BluetoothBootReceiver` on boot if Bluetooth is enabled

Service behavior:

- Creates notification channel.
- Calls `startForeground`.
- Initializes `ScreamRepository`.
- Reads registered user profile.
- Starts `MeshNetworkManager`.
- Starts `BleGattServer`.
- Starts `P2pMeshEngine` if a registered user exists.

## BLE Discovery

File: `MeshNetworkManager.kt`

Advertising:

- Uses `BluetoothLeAdvertiser`.
- Requires adapter enabled and multiple advertisement support.
- Advertises fixed service UUID from `BleGattServer.SCREAM_SERVICE_UUID`.
- Connectable advertisement.
- Balanced mode and medium TX power.

Scanning:

- Uses `BluetoothLeScanner`.
- Filters by SCREAM service UUID.
- Low power scan mode.
- On SCREAM result, stores discovered device and calls `BleGattClient.connectToDevice`.
- A physical address is stored once even when the device advertises both SCREAM
  and BitChat UUIDs; authenticated GATT messages merge the placeholder into the
  sender identity.

Bluetooth state receiver:

- On `STATE_ON`, restarts advertise/scan and marks network active.
- On `STATE_TURNING_OFF`, stops advertise/scan and marks network offline.

## GATT UUIDs

Defined in `BleGattServer.kt`. Treat these as protocol constants:

- Service: `0000FEA5-0000-1000-8000-00805F9B34FB`
- Write characteristic: `0000FEA6-0000-1000-8000-00805F9B34FB`
- Notify characteristic: `0000FEA7-0000-1000-8000-00805F9B34FB`
- CCCD descriptor: `00002902-0000-1000-8000-00805F9B34FB`
- BitChat Service Interop: `0000FDB2-0000-1000-8000-00805F9B34FB` (defined in `BitChatProtocolAdapter.kt`)

Do not change these without migration/backward-compatibility planning.

## GATT Server

File: `BleGattServer.kt`

Responsibilities:

- Opens a GATT server.
- Adds service with write and notify characteristics.
- Tracks connected devices.
- Processes incoming characteristic writes.
- Sends notifications to connected devices.
- Chunks outbound messages larger than 180 bytes.
- Reassembles inbound chunked messages.
- Dispatches complete JSON strings to `P2pMeshEngine.handleIncomingBleMessage`.

## GATT Client

File: `BleGattClient.kt`

Responsibilities:

- Connects to discovered BLE devices.
- Requests MTU 512.
- Discovers services.
- Subscribes to notify characteristic.
- Stores write characteristic.
- Queues messages until service discovery completes.
- Sends messages to all connected GATT peers.
- Reassembles incoming chunks.
- Reconnects with exponential backoff: 5s, 10s, 20s, capped at 60s.

## BLE Chunk Format

Both client and server use:

```text
SCHK:<shortId>:<index>/<total>:<base64Data>
```

Raw chunk size:

- Client writes: 120 bytes
- Server notifications: 120 bytes

Single-packet threshold:

- 180 bytes

## Important Caveats

- `BluetoothTransferScreen` does not control the real BLE scanner.
- BLE peer identity comes from message sender metadata after message exchange; scan results themselves only identify devices.
- GATT connection counts and BLE discovered devices are not currently exposed directly to UI state except through message-driven peer updates and some diagnostic helpers.
- Permission denial is logged, but service still tries to run and can retry discovery later. Delayed GATT reconnects are invalidated when the mesh is stopped so a disabled service cannot silently reconnect. Invalid or abusive GATT envelopes are rejected and their route is temporarily quarantined.

