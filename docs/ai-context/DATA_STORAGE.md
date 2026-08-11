# Data And Storage

## Models

Defined in `app/src/main/java/com/scream/app/model/Models.kt`.

### User

Fields:

- `id`
- `alias`
- `avatar`
- `profileImage` (optional base64 photo for private conversations; public surfaces use `avatar`)
- `age`
- `gender`

Used by identity, posts, messages, peer info, profile dialogs, network sender metadata.

### Post

Fields:

- identity: `id`, `user`
- content: `body`, `timestamp`, `createdAt`, `mediaBase64`, `mediaMimeType`, `audioDurationMs`
- engagement: `likes`, `dislikes`, `reshares`, `views`, `likedBy`
- local flags: `isLiked`, `isDisliked`, `isReshared`
- reshare metadata: `isResharePost`, `originalPostId`

### ChatMessage

Fields:

- identity: `id`, `sender`
- content: `body`, `kind`
- timing: `timestamp`, `createdAt`, `pinnedUntil`
- ownership: `isMine`
- voice: `audioBase64`, `audioDurationMs`
- media: `mediaBase64`, `mediaMimeType`
- reply: `replyToId`, `replyToSender`, `replyToBody`
- social/local: `reactions`, `isBookmarked`
- network UI: `route`, `deliveryStatus`

`expiresAt(defaultTtlMs)` returns `pinnedUntil` or `createdAt + defaultTtlMs`.

### Room

Fields:

- `id`
- `name`
- `icon`
- `preview`
- `memberCount`
- `isPrivate`
- `adminId`
- `messages`
- `members`


### Peer And Mesh Models

- `PeerTransport`: BLUETOOTH, BLE, WIFI_DIRECT, NEARBY, TCP, UNKNOWN
- `ConnectionQuality`: EXCELLENT, GOOD, WEAK, DISCONNECTED
- `PeerConnectionType`: DIRECT, NEARBY_DISCOVERED, MESH_REACHABLE
- `ConnectedPeer`: user plus transport, quality, signal, last seen, relay/battery display fields
- `NetworkStatus`: ACTIVE, LIMITED, OFFLINE
- `MeshStats`: mesh ID, counts, status, quality

## Identity Storage

File: `identity/UserPreferencesRepository.kt`

Technology: Android DataStore preferences.

DataStore name: `scream_identity`.

Keys:

- `uuid`
- `alias`
- `age`
- `gender`
- `emoji_avatar`
- `profile_image` (optional base64 photo shared in private chat identity metadata)
- `is_registered`

User registration always generates a fresh UUID.

## App Content Storage

File: `data/ScreamRepository.kt`

Technology: Android SharedPreferences with manual JSON serialization.

Preferences name: `scream_local_store`.

Keys:

- `mesh_id`
- `posts`
- `rooms`

Persistence is called after most mutations.

## JSON Serialization Hotspots

When adding/changing model fields, update both write and read paths:

- `User.toJson`
- `Post.toJson`
- `ChatMessage.toJson`
- `Room.toJson`
- `JSONObject.toUser`
- `postFromJson`
- `chatMessageFromJson`
- `roomFromJson`

Also update network payload creation/parsing if the field should cross devices.

## Backup Behavior

`backup_rules.xml` and `data_extraction_rules.xml` include SharedPreferences. That means persisted app content can be included in Android backup/device transfer. Revisit these files for privacy-sensitive changes.

## Current Storage Limitations

- No Room/database layer.
- Large media and voice files are base64 strings inside SharedPreferences JSON, which can become heavy.
- No schema migrations.
- No per-room storage separation.
- No content index/search beyond in-memory filtering.
- SQLite/media content is not yet encrypted at rest. BitChat private key material is separately wrapped with an Android Keystore AES-GCM key.

