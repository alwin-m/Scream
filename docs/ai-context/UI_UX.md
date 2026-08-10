# UI And UX

## Navigation

File: `AppNavigation.kt`

Routes:
- `onboarding`
- `home`
- `bluetooth`
- `chat/{roomId}`

Start destination:
- `home` if `UserProfile.isRegistered == true`
- `onboarding` otherwise

## Theme

Files:
- `ui/theme/Color.kt`
- `ui/theme/Theme.kt`
- `ui/theme/Type.kt`

The app is **dark-only** per production guidelines. It uses a clean, near-black palette with electric blue-to-violet gradients. Status and navigation bars are rendered transparently. Fonts utilize system SansSerif (Roboto) metrics with polished letter spacing.

## Main Home Shell

File: `HomeScreen.kt`

Responsibilities:
- Top app bar showing SCREAM brand logo and a live Bluetooth connection status pill.
- Navigation bar with filled/outline active states.
- Opens `MeshInfoBottomSheet` when tapping the status pill.
- Opens `UserProfileDialog` when selecting a mesh peer.

Tabs:
- Feed -> `FeedScreen`
- Rooms -> `RoomsScreen`
- Private -> `RoomsList` filtered to private messages
- Profile -> `ProfileScreen`

## Onboarding Screen

File: `OnboardingScreen.kt`

Responsibilities:
- Gradient header branding.
- Avatar Selector displaying a grid of 24 emoji options.
- Refined input styling for user alias, age, and gender.
- Animated mesh entry CTA.

## Feed UI

File: `FeedScreen.kt`

Responsibilities:
- Card composer with character counter, send action, and attachment triggers (photos, videos, voice audio recordings).
- Voice recorder with real-time length tracking, limit enforcement, and dynamic wave visualizer preview.
- Polished post list displaying structured headers (Avatar · Alias · ID · Relative Time), view counts, and attachments (zoomable photo, custom video badge, canvas audio playback).
- Tapping post like count triggers a pop-up listing all user aliases who liked it.

## Rooms UI

File: `RoomsScreen.kt`

Responsibilities:
- Conversation-style room cards with avatar, title, privacy badges (Public/Private), unread badges, and last message previews.
- Create room dialog with custom styled chip selectors, icons, and privacy filters.
- Filters private rooms: only visible to non-members if they are invited or created it.

## Chat UI

File: `ChatScreen.kt`

Responsibilities:
- Top bar with members count, active Bluetooth status, search filtering, and "Room Members" manager (for private room admins).
- "Room Members" manager dialog lets private room admins invite nearby active mesh peers or remove current members.
- Incoming bubbles in dark grey (left), outgoing bubbles in blue (right).
- Multiline composer with integrated camera/photo attachment picker and voice recording toggles.
- Supports replies, image rendering, waveform audio player, reaction overlays, bookmarks, and delivery checkmarks.
- Selective deletion dialog offers "Delete for Me" and "Delete for Everyone" (which broadcasts deletes) options.

## Mesh Info Bottom Sheet

File: `MeshInfoBottomSheet.kt`

Responsibilities:
- **Removed simulated peers (Alice/Bob/Charlie/Dave/Eve)**.
- Renders only active mesh peers in a radial layout centered around the root "You" node.
- Live diagnostics grid.

## Profile Screen

File: `ProfileScreen.kt`, `ui/components/MeshTopologyMap.kt`

Responsibilities:
- User avatar, alias, short ID, stats row (Posts, Likes, Reshares breakdown).
- **Security Identity Card**: Displays local SCREAM Mesh ID, user short ID, AES-256-GCM encryption status, and one-tap copy-to-clipboard functionality.
- **Live Mesh Topology Map**: Custom offline `Canvas` composable rendering an animated dark circuit-board node graph of the local P2P network. Pulsing local user node at center, satellite peer nodes arranged by transport type (BLE, LAN, BitChat), and tap-to-inspect peer actions.
- Settings Center trigger button and Bluetooth transfer screen launcher.
- Background activity mode controls (Active, Deep Sleep, Disabled).
- Edit Profile button opens an editor allowing the user to update alias, age, gender, and pick a custom image from the photo gallery or select an emoji avatar.
- Dynamic card showing local Bluetooth status.
- Shortcut to Bluetooth settings.

## Bluetooth Transfer UI

File: `BluetoothTransferScreen.kt`

Responsibilities:
- Visual radar animation representing real discovery activities.
- Lists real-time discovered device logs and signal strengths from `MeshNetworkManager.kt`.
