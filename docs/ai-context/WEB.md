# Web Companion And Python Bridge

## Purpose

The `web/` folder is a standalone browser prototype and local LAN bridge. It is not part of the Android app build. Full documentation is available in [`docs/WEB_COMPANION.md`](file:///d:/scream/docs/WEB_COMPANION.md).

Run from repository root:

```powershell
python web\server.py
```

Then open:

```text
http://localhost:8000
```

## Files

- `web/index.html`: static markup for onboarding, header navigation, feed, rooms, private chats, profile, Security Identity Card, chat overlay, room modal, diagnostics modal, and QR identity code modal.
- `web/style.css`: modern dark responsive UI styling with CSS custom properties, glassmorphism cards, HSL color tokens, animations, and custom scrollbars.
- `web/app.js`: browser state, UI rendering, character counter, voice note recording, photo upload, identity copy button, QR modal, fetch calls, SSE handling.
- `web/server.py`: HTTP static server, API endpoints, SSE stream, UDP/TCP mesh bridge, and AES-256-GCM envelope encryption/decryption.

## Browser App Flow

1. User enters alias/avatar/optional age/gender.
2. `app.js` generates a local `#XXXX` ID.
3. Browser posts `/api/register`.
4. Browser starts `EventSource('/api/events')`.
5. Browser receives `INIT`, `PEERS_UPDATE`, `NEW_POST`, `NEW_ROOM`, `CHAT_MESSAGE`, `UPDATE_POST`.
6. Feed, rooms, profile identity card, and chat are updated in DOM.

## Server API

GET:

- `/api/events`: Server-Sent Events stream.

POST:

- `/api/register`
- `/api/post`
- `/api/chat`
- `/api/room/create`
- `/api/like`
- `/api/dislike`
- `/api/reshare`

## Server Mesh & Security

Ports:

- HTTP: 8000
- UDP heartbeat: 8888
- TCP messages: 8889

`run_udp_mesh`:

- Binds UDP 8888.
- Sends heartbeat every 3 seconds when a web user exists.
- Receives peer heartbeats and broadcasts `PEERS_UPDATE` over SSE.

`run_tcp_server`:

- Binds TCP 8889.
- Reads JSON messages from Android/web peers.
- Supports both Android `encryptedData` AES-256-GCM payloads and plain JSON fallbacks via `handle_remote_event`.

`send_to_android_peers`:

- Wraps payloads in structured mesh envelopes with `encryptedData` cipherText using AES-256-GCM and transmits over TCP 8889.


