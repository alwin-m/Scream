# Web Companion And Python Bridge

## Purpose

The `web/` folder is a standalone browser prototype and local LAN bridge. It is not part of the Android app build.

Run from repository root:

```powershell
python web\server.py
```

Then open:

```text
http://localhost:8000
```

## Files

- `web/index.html`: static markup for onboarding, sidebar, feed, rooms, profile, chat, and user modal.
- `web/style.css`: dark responsive UI styling.
- `web/app.js`: browser state, UI rendering, fetch calls, SSE handling.
- `web/server.py`: HTTP static server, API endpoints, SSE stream, UDP/TCP mesh bridge.
- `web/logo.png`: generated logo.

## Browser App Flow

1. User enters alias/avatar/optional age/gender.
2. `app.js` generates a local `#XXXX` ID.
3. Browser posts `/api/register`.
4. Browser starts `EventSource('/api/events')`.
5. Browser receives `INIT`, `PEERS_UPDATE`, `NEW_POST`, `CHAT_MESSAGE`, `UPDATE_POST`.
6. Feed, rooms, profile, and chat are updated in DOM.

## Server API

GET:

- `/api/events`: Server-Sent Events stream.

POST:

- `/api/register`
- `/api/post`
- `/api/chat`
- `/api/like`
- `/api/dislike`
- `/api/reshare`

## Server Mesh

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
- Calls `handle_remote_event`.

`send_to_android_peers`:

- Sends plain JSON to discovered peer IPs on TCP 8889.

## Protocol Caveat

The web bridge uses old plain payloads:

```json
{
  "type": "CHAT_MESSAGE",
  "sender": {},
  "data": {}
}
```

Android now sends encrypted envelopes with `encryptedData`. Android still accepts plain `data` fallback on incoming, but the web bridge does not decrypt Android outbound `encryptedData`.

If cross-platform compatibility matters, update `web/server.py` to understand Android's envelope or update Android to emit a compatibility payload intentionally.

## UI Caveats

- Web posts/rooms are in memory only.
- Web room creation does not broadcast a `NEW_ROOM` network event.
- Web media/voice actions show alerts; no actual file/audio transfer.
- Web likes/dislikes/reshares update server state but `UPDATE_POST` handling in browser currently just rerenders existing local data.

