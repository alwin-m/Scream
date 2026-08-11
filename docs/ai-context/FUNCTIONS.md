# Functions And Feature Behavior

## Identity

Registration happens through `OnboardingScreen`.

- Default alias fallback: `Anonymous`
- Default avatar fallback: emoji avatar string
- UUID: random UUID stored in DataStore
- Runtime app user ID: first four UUID chars uppercased with `#` prefix
- Public posts and public rooms use the user's emoji avatar. An optional gallery photo is persisted separately and included in private-chat identity metadata.

Main files:

- `identity/UserPreferencesRepository.kt`
- `identity/IdentityViewModel.kt`
- `identity/OnboardingScreen.kt`
- `ui/MainViewModel.kt`

## Feed Posts

Post creation:

1. `FeedScreen` validates composer text and calls `MainViewModel.createPost`.
2. `MainViewModel` gets `currentUser` or fallback anonymous user.
3. `ScreamRepository.createPost` creates a `Post`, prepends it to `_posts`, persists data, and broadcasts `NEW_POST`.
4. `P2pMeshEngine` sends the encrypted envelope to LAN and BLE peers.

Post reactions:

- `likePost` toggles local like state and clears dislike if needed.
- `dislikePost` toggles local dislike state and clears like if needed.
- `resharePost` increments reshare count and prepends a copied reshare post.
- `unresharePost` removes local reshare copies and decrements count.
- `deletePost` removes the post and reshares of that post.

Network message types:

- `NEW_POST`
- `LIKE_POST`
- `DISLIKE_POST`
- `RESHARE_POST`
- `UNRESHARE_POST`
- `DELETE_POST`

## Rooms

Default rooms are created inside `ScreamRepository`:

- `r1`: General Mesh
- `r2`: Tech & Ideas

Room creation:

1. `RoomsScreen` shows `CreateRoomDialog`.
2. `MainViewModel.createRoom` calls `ScreamRepository.createRoom`.
3. Repository creates a UUID room ID, sets admin ID, persists, and broadcasts `NEW_ROOM`.

Room deletion:

- Only allowed if `room.adminId` is blank or matches current user.
- Broadcast type: `DELETE_ROOM`.

Private rooms:

- `getOrCreatePrivateRoom(currentUser, peer)` uses ID `private_${peer.id}`.
- Private room name format is `Private: ${peer.alias}`.
- Private rooms are still stored in the shared room list.
- Public room member counts refresh from live mesh peers. Private room counts are based on the owner plus invited members.

## Chat

Text send:

1. `ChatScreen` calls `MainViewModel.sendChatMessage`.
2. Repository creates a local `ChatMessage` with `deliveryStatus = "Sending"`.
3. It immediately stores a copy with `deliveryStatus = "Delivered"`.
4. It broadcasts `CHAT_MESSAGE`.

Voice send:

- `ChatScreen` records `.m4a` temp files with `MediaRecorder`.
- Max duration: 2 minutes.
- After recording, `VoiceEditorScreen` is shown.
- Repository base64-encodes the file in `audioBase64`.

Image send:

- Gallery uses `ActivityResultContracts.GetContent`.
- Camera uses `ActivityResultContracts.TakePicturePreview`.
- Images are compressed to JPEG at quality 82 for camera captures.
- Repository base64-encodes bytes in `mediaBase64`.

Message actions:

- Reply: stores reply ID, sender alias, and body.
- Forward: sends the original text or voice message to another room.
- Reactions: toggles emoji -> list of aliases, broadcasts `MESSAGE_REACTION`.
- Bookmark: local-only toggle.
- Pin: sets `pinnedUntil`; broadcasts `PIN_CHAT_MESSAGE`.
- Delete: removes message and broadcasts `DELETE_CHAT_MESSAGE`.
- Route info: displays route aliases from message envelope.

Network message types:

- `CHAT_MESSAGE`
- `DELETE_CHAT_MESSAGE`
- `PIN_CHAT_MESSAGE`
- `MESSAGE_REACTION`

## Expiry

`ScreamRepository.purgeExpiredContent`:

- Removes posts older than 48 hours.
- Removes chat messages whose `expiresAt` is in the past.
- Normal message expiration: `createdAt + 48 hours`.
- Pinned message expiration: `pinnedUntil`.

The cleanup runs:

- On repository initialization.
- Hourly in `MainViewModel`.

## Mesh Status

`ScreamRepository.updateActivePeers` computes:

- Direct count
- Nearby discovered count
- Mesh reachable count
- Total participants = peers + local user
- Network status:
  - `ACTIVE` if direct peers exist and best quality is not weak
  - `LIMITED` if nearby or direct peers exist
  - `OFFLINE` otherwise

`HomeScreen` and `MeshInfoBottomSheet` render this state.

