# Network

## Current Transport Stack

SCREAM currently uses both LAN and BLE paths:

- LAN discovery: UDP broadcast on port `8888`.
- LAN message delivery: TCP server/client on port `8889`.
- BLE discovery: BLE advertising/scanning of fixed SCREAM service UUID and BitChat service UUID (`0000FDB2-0000-1000-8000-00805F9B34FB`).
- BLE message delivery: GATT write and notify characteristics.
- Dual-Protocol Interoperability: Supports both native SCREAM mesh protocol and BitChat protocol interop via `BitChatProtocolAdapter.kt`.

The central protocol owner is `P2pMeshEngine.kt`.

## Transport Boundary

The transport layer now has an additive platform boundary in
`network/transport/Transport.kt`. A `Transport` moves opaque bytes and exposes
peer discovery, connection, sending, and receive callbacks. It must not know
about SCREAM message types, encryption, TTL, deduplication, or persistence.

`TransportCoordinator` provides registration and fan-out discovery for future
LAN, BLE, Nearby Connections, Wi-Fi Direct, and desktop mDNS/BLE
implementations. The current `P2pMeshEngine` remains the runtime owner of LAN
and BLE behavior in this milestone; the existing protocol adapters and engine
are intentionally unchanged so the boundary can be adopted transport by
transport without changing current message delivery.

## Multi-Protocol Architecture

SCREAM uses a layered multi-protocol architecture:

```text
Compose UI → MainViewModel → ScreamRepository → P2pMeshEngine
                                                       │
                                              ┌────────┴────────┐
                                              │  MessageRouter   │
                                              └────────┬────────┘
                                                       │
                                         ┌─────────────┼─────────────┐
                                         │                           │
                                  ScreamProtocolAdapter    BitChatProtocolAdapter
                                         │                           │
                                    LAN TCP/UDP                 BLE (JSON)
                                    SCREAM BLE               Nostr (Phase 3)
```

### Key Components

- `ProtocolAdapter` interface (`network/protocol/ProtocolAdapter.kt`): Common contract that all protocol adapters implement — initialize, send, broadcast, peer discovery.
- `UnifiedMessage` (`network/protocol/UnifiedMessage.kt`): Protocol-agnostic message model with `PeerAddress`, `PeerRoute`, `UnifiedPeer`.
- `ScreamProtocolAdapter` (`network/protocol/ScreamProtocolAdapter.kt`): Wraps existing SCREAM envelope encoding/decoding (AES-GCM, JSON).
- `BitChatProtocolAdapter` (`network/BitChatProtocolAdapter.kt`): Implements `ProtocolAdapter` for BitChat interop. Currently uses JSON format (Phase 1); binary codec planned (Phase 2).
- `MessageRouter` (`network/routing/MessageRouter.kt`): Central routing engine. Given a message and target, decides which adapter and transport to use.
- `RoutingPolicy` (`network/routing/RoutingPolicy.kt`): Configurable routing preferences (prefer encrypted, prefer direct, protocol priority).
- `PeerManager` (`network/peer/PeerManager.kt`): Unified peer registry across all protocols. Tracks routes, supports identity linking, emits `StateFlow<List<UnifiedPeer>>`.
- `IdentityManager` (`identity/IdentityManager.kt`): Manages dual identity (SCREAM UUID + BitChat Ed25519 keypair).
- `BitChatIdentity` (`identity/BitChatIdentity.kt`): Ed25519 public key + 8-byte sender ID (SHA-256 truncated).

### Protocol Types

Defined in `model/Models.kt`:

- `ProtocolType.SCREAM` — native SCREAM mesh protocol
- `ProtocolType.BITCHAT` — BitChat interop protocol

### Encryption Status

Per-message/per-peer encryption tracking via `EncryptionStatus`:

- `NONE` — plaintext
- `SCREAM_SHARED_KEY` — app-wide AES-GCM shared key
- `NOISE_SESSION` — Noise XX E2E (Phase 2)
- `NOISE_SEALED` — Noise X sealed envelope (Phase 2)

### Peer Transports

`PeerTransport` includes `NOSTR` for relay-based peers (Phase 3).

### Database

`ScreamDbHelper` v2 adds tables: `bitchat_identity`, `peer_identity_map`, `noise_sessions`.


## LAN Flow

### Discovery

`P2pMeshEngine.listenUdpBroadcast` listens on UDP port 8888 for `HEARTBEAT` packets.

`P2pMeshEngine.sendUdpHeartbeats` broadcasts every 3 seconds to `255.255.255.255:8888`.

Heartbeat shape:

```json
{
  "type": "HEARTBEAT",
  "user": {
    "id": "#ABCD",
    "alias": "Alias",
    "avatar": "avatar"
  },
  "meshId": "SCREAM-ABCD"
}
```

Discovered LAN peers are stored in a concurrent `peerMap` with transport `TCP`; BLE and LAN updates may arrive on different callback threads.

### Delivery

`listenTcpServer` opens server socket on port 8889. Each accepted socket is handled by `handleTcpClient`.

Outbound LAN delivery uses `sendToKnownPeers`, which sends the message envelope to every peer in `peerMap` except optional incoming IP and except `ble://` pseudo-address peers. The engine uses connect timeouts, closes sockets during shutdown, and sends heartbeats to both the global and interface broadcast addresses for better Wi-Fi compatibility.

## BLE Flow In Relation To Network Layer

BLE-specific files move complete JSON strings, but do not own app message semantics.

```text
MeshNetworkManager discovers BLE peer
  -> BleGattClient.connectToDevice
  -> BleGattClient receives/writes JSON strings
  -> BleGattServer receives/notifies JSON strings
  -> P2pMeshEngine.handleIncomingBleMessage
  -> ScreamRepository
```

## Message Envelope

Outbound envelope is created by `P2pMeshEngine.broadcastPayload`.

Fields:

- `version`: protocol version, currently `1`
- `id`: unique message UUID
- `type`: app event type
- `sourcePeerId`: local user ID
- `timestamp`: milliseconds
- `ttl`: default `6`
- `meshId`: repository mesh ID
- `sender`: user metadata
- `route`: aliases visited by message
- `encryptedData`: AES-GCM encrypted payload object

## Encryption

Payload encryption:

- Algorithm string: `AES-256-GCM`
- Cipher: `AES/GCM/NoPadding`
- IV: 12 random bytes
- Tag: 128 bits
- Key: SHA-256 digest of static string `SCREAM_LOCAL_MESH_V1`

This is app-wide shared-key encryption, not real user-specific end-to-end encryption.

Incoming code tries:

1. Decrypt `encryptedData`.
2. Fallback to plain `data` object.
3. Fallback to empty JSON object.

## Deduplication

`seenMessageIds` is a synchronized access-order `LinkedHashMap` capped at 2048 IDs.

Incoming messages:

- If `id` has been seen, drop.
- If `sourcePeerId` equals current user ID, drop.
- Otherwise remember ID, dispatch, and forward if TTL allows.

## TTL And Forwarding

LAN incoming messages call `forwardMessageIfAlive`.

BLE incoming messages call `forwardMessageViaBluetooth`.

Forwarding behavior:

- If `ttl <= 1`, do not forward.
- Append current user alias to `route`.
- Decrement TTL.
- Forward over LAN and BLE where applicable.

## Message Types

Handled in `dispatchIncomingMessage`:

- `NEW_POST`
- `LIKE_POST`
- `DISLIKE_POST`
- `RESHARE_POST`
- `UNRESHARE_POST`
- `DELETE_POST`
- `NEW_ROOM`
- `DELETE_ROOM`
- `CHAT_MESSAGE`
- `DELETE_CHAT_MESSAGE`
- `PIN_CHAT_MESSAGE`
- `MESSAGE_REACTION`

When adding a new cross-device action:

1. Add repository local mutation.
2. Add outbound payload and `broadcastPayload` call.
3. Add `dispatchIncomingMessage` handler.
4. Add JSON persistence if state must survive restart.
5. Update this file and `FUNCTIONS.md`.

## Peer Quality

LAN signal strength is estimated from IP range, not measured:

- `192.168.*` -> -50
- `10.*` -> -55
- `172.*` -> -60
- other -> -70

`ConnectionQuality.fromRssi` maps RSSI-like values to EXCELLENT/GOOD/WEAK/DISCONNECTED.

## Web Bridge Compatibility

`web/server.py` sends plain messages with:

```json
{
  "type": "NEW_POST",
  "sender": {},
  "data": {}
}
```

Android can read plain `data` on incoming messages, but Android outbound messages use `encryptedData`. If web/Android interop breaks, inspect both `web/server.py` and `P2pMeshEngine.decryptPayload`.

