# Architecture


## High-Level Layers

```text
Android Activity
  MainActivity
    AppNavigation
      OnboardingScreen
      HomeScreen
        FeedScreen
        RoomsScreen
        ChatScreen
        ProfileScreen
        MeshInfoBottomSheet

View models
  IdentityViewModel
  MainViewModel

State and business logic
  UserPreferencesRepository
  ScreamRepository

Network
  MeshForegroundService
  P2pMeshEngine
  MeshNetworkManager
  BleGattClient
  BleGattServer
  BluetoothBootReceiver
  TransportManager
```

## Data Ownership

`ScreamRepository` owns posts, rooms, connected peers, peer counts, mesh stats, network status, persistence, expiry, and network broadcast calls.

`MainViewModel` owns no persistent data. It translates UI actions into repository calls and exposes repository `StateFlow`s to Compose screens.

`UserPreferencesRepository` owns identity persistence through Android DataStore. It is separate from `ScreamRepository`, which uses SharedPreferences.

## Network Ownership

`P2pMeshEngine` is the protocol and routing owner. It builds the network envelope, encrypts payloads, deduplicates by message ID, decrements TTL, forwards over LAN and BLE, and dispatches incoming message types into repository receive/update methods.

`MeshNetworkManager` only owns BLE discovery. It advertises the SCREAM service UUID, scans for the same UUID, maintains discovered device state, and tells `BleGattClient` to connect.

`BleGattClient` and `BleGattServer` only move JSON strings over BLE GATT. They chunk and reassemble payloads, then pass complete JSON messages to `P2pMeshEngine.handleIncomingBleMessage`.

`MeshForegroundService` owns long-running mesh lifecycle. UI code should not try to keep BLE/LAN alive directly.

## Compose State Pattern

Screens call `collectAsState()` on `MainViewModel` flows:

- `currentUser`
- `posts`
- `rooms`
- `activePeers`
- `peerCount`
- `meshStats`
- `networkStatus`

User actions call `MainViewModel` methods, which call `ScreamRepository`.

## Message Envelope

Android outbound messages are built in `P2pMeshEngine.broadcastPayload`:

```json
{
  "version": 1,
  "id": "uuid",
  "type": "CHAT_MESSAGE",
  "sourcePeerId": "#ABCD",
  "timestamp": 1234567890,
  "ttl": 6,
  "meshId": "SCREAM-ABCD",
  "sender": {
    "id": "#ABCD",
    "alias": "Alias",
    "avatar": "avatar"
  },
  "route": ["Alias"],
  "encryptedData": {
    "alg": "AES-256-GCM",
    "iv": "base64",
    "cipherText": "base64"
  }
}
```

Incoming handlers fall back to plain `data` if `encryptedData` cannot be decrypted. This keeps some compatibility with the web bridge.

## Persistence

Identity:

- DataStore name: `scream_identity`
- Keys: uuid, alias, age, gender, emoji avatar, is registered

Posts/rooms/messages:

- SharedPreferences name: `scream_local_store`
- Keys: `posts`, `rooms`, `mesh_id`
- Format: JSON arrays built manually in `ScreamRepository`
- Expiry: normal content expires after 48 hours; pinned messages can live up to 30 days

## Background Behavior

`MeshForegroundService` is started by:

- `MainActivity` on launch after permission request
- `BluetoothBootReceiver` when Bluetooth turns on
- `BluetoothBootReceiver` after boot if Bluetooth is already on

The service starts foreground notification channel `scream_mesh_channel` and returns `START_STICKY`.

