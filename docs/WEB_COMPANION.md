# SCREAM Web Companion & Python Bridge Documentation

## Overview

The `web/` directory contains the standalone Web companion interface and local Python bridge (`web/server.py`). It serves as an offline-first browser UI and local network bridge, allowing desktop browsers to join the local SCREAM P2P mesh network over LAN UDP discovery and TCP message transport.

---

## Architecture & Surface Components

1. **`web/index.html`**:
   - Single Page Application (SPA) container for Onboarding, Header Navigation, Main Body Tabs (Feed, Rooms, Private Chats, Profile), Modals (Create Room, Mesh Diagnostics, QR Identity Code), and Overlay Chat.
   - Built with semantic HTML5 elements, explicit unique component IDs, and dark theme support.

2. **`web/style.css`**:
   - Modern dark design system utilizing CSS custom properties (`:root` tokens for colors, borders, radius, HSL gradients).
   - Glassmorphism overlays, audio visualizer keyframe animations, pulsing network status indicators, custom scrollbars, and fluid card layouts.

3. **`web/app.js`**:
   - Single-file browser application engine managing state (`currentUser`, `posts`, `rooms`, `activePeers`, `currentRoom`).
   - Server-Sent Events (`EventSource('/api/events')`) handler for live streaming updates (`INIT`, `PEERS_UPDATE`, `NEW_POST`, `NEW_ROOM`, `CHAT_MESSAGE`, `UPDATE_POST`).
   - Voice note recorder via `MediaRecorder` API with dynamic wave preview.
   - Photo attachment reader (`FileReader` base64 encoding).
   - Web Bluetooth device scanner (`navigator.bluetooth`).
   - Security Identity clipboard copying & QR Identity modal triggers.

4. **`web/server.py`**:
   - Multi-threaded Python server handling:
     - HTTP static file serving (`http.server.SimpleHTTPRequestHandler`) on port 8000.
     - REST API endpoints for user registration, broadcasting posts, creating rooms, sending chat messages, and liking/disliking/resharing posts.
     - SSE stream handler (`/api/events`) keeping browser clients updated in real time.
     - LAN UDP Broadcast Heartbeat listener and sender on port 8888.
     - LAN TCP Server on port 8889 for receiving JSON payloads from Android and peer nodes.
     - Cross-platform encrypted envelope support using Python `cryptography` (`AES-256-GCM`).

---

## Network Ports & Protocol Specs

| Protocol | Port | Description |
| :--- | :--- | :--- |
| **HTTP** | `8000` | Serves web app static files, REST APIs, and `/api/events` SSE stream. |
| **UDP** | `8888` | Broadcasts heartbeat discovery packets (`255.255.255.255:8888`) every 3 seconds. |
| **TCP** | `8889` | Receives and forwards structured JSON mesh envelopes to discovered peer IPs. |

---

## Server REST API Reference

### `POST /api/register`
Registers the current browser user profile with the local bridge server.
```json
{
  "user": {
    "id": "#A1B2",
    "alias": "Maverick",
    "avatar": "😎",
    "age": "28",
    "gender": "Male"
  }
}
```

### `POST /api/post`
Broadcasts a new scream/post to the local mesh.
```json
{
  "body": "Heading out into the field!",
  "mediaBase64": "",
  "mediaMimeType": "",
  "audioDurationMs": 0
}
```

### `POST /api/chat`
Sends a message into a public room or private 1-on-1 chat.
```json
{
  "roomId": "private_#C3D4",
  "body": "Mesh link established."
}
```

### `POST /api/room/create`
Creates a public or private room.
```json
{
  "name": "Field Operations",
  "icon": "🏕️",
  "isPrivate": false
}
```

### `POST /api/like`, `/api/dislike`, `/api/reshare`
Updates post reaction counts across connected peers.
```json
{
  "postId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## Running the Web Bridge

From the repository root:

```powershell
python web\server.py
```

Then open your browser at:

```text
http://localhost:8000
```

---

## Security & Civilian Resilience Note

The Web Companion represents local civilian resilience software designed to maintain communication during infrastructure outages. It utilizes shared network AES-256-GCM envelope encryption and bounded TTL hop counts. Content retention is limited to local memory and persistent storage rules (48-hour default).
