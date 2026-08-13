import os
import sys
import time
import json
import socket
import hashlib
import base64
import uuid
import threading
import http.server
import socketserver

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    HAS_CRYPTO = True
    KEY_BYTES = hashlib.sha256(b"SCREAM_LOCAL_MESH_V1").digest()
    aesgcm = AESGCM(KEY_BYTES)
except ImportError:
    HAS_CRYPTO = False
    aesgcm = None

HTTP_PORT = 8000
UDP_PORT = 8888
TCP_PORT = 8889

discovered_peers = {}  # id -> {user, ip, lastSeen}
current_web_user = None

posts_db = []
rooms_db = []

event_clients = []

def encrypt_payload(data_dict):
    if not HAS_CRYPTO or not aesgcm:
        return {}
    try:
        raw = json.dumps(data_dict).encode('utf-8')
        iv = os.urandom(12)
        ct = aesgcm.encrypt(iv, raw, None)
        return {
            "alg": "AES-256-GCM",
            "iv": base64.b64encode(iv).decode('utf-8'),
            "cipherText": base64.b64encode(ct).decode('utf-8')
        }
    except Exception as e:
        print("Encryption warning:", e)
        return {}

def decrypt_payload(enc_data):
    if not enc_data or not HAS_CRYPTO or not aesgcm:
        return None
    try:
        if isinstance(enc_data, str):
            enc_data = json.loads(enc_data)
        iv = base64.b64decode(enc_data.get("iv", ""))
        ct = base64.b64decode(enc_data.get("cipherText", ""))
        pt = aesgcm.decrypt(iv, ct, None)
        return json.loads(pt.decode('utf-8'))
    except Exception:
        return None

# --- UDP HEARTBEAT DISCOVERY ---
def run_udp_mesh():
    udp_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    udp_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        udp_socket.bind(('', UDP_PORT))
    except Exception as e:
        print("UDP bind warning:", e)

    def heartbeat_sender():
        send_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        send_sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        while True:
            if current_web_user:
                try:
                    user_json = {
                        "id": current_web_user.get("id"),
                        "alias": current_web_user.get("alias"),
                        "avatar": current_web_user.get("avatar", "😎"),
                        "batteryLevel": 99,
                        "osVersion": "Web Dashboard (Browser)"
                    }
                    payload = json.dumps({
                        "type": "HEARTBEAT",
                        "sender": user_json,
                        "meshId": f"WEB-{current_web_user.get('id', '0000')[:4]}"
                    }).encode('utf-8')
                    send_sock.sendto(payload, ('255.255.255.255', UDP_PORT))
                except Exception:
                    pass
            time.sleep(3)

    threading.Thread(target=heartbeat_sender, daemon=True).start()

    while True:
        try:
            data, addr = udp_socket.recvfrom(4096)
            sender_ip = addr[0]
            obj = json.loads(data.decode('utf-8'))
            if obj.get("type") == "HEARTBEAT":
                user = obj.get("user", {})
                user_id = user.get("id")
                if user_id and (not current_web_user or user_id != current_web_user.get("id")):
                    discovered_peers[user_id] = {
                        "user": user,
                        "ip": sender_ip,
                        "lastSeen": time.time()
                    }
                    broadcast_event("PEERS_UPDATE", [p["user"] for p in discovered_peers.values()])
        except Exception:
            pass

# --- TCP LISTENER FROM ANDROID / LAPTOP PEERS ---
def run_tcp_server():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind(('', TCP_PORT))
        server.listen(10)
    except Exception as e:
        print("TCP bind error:", e)
        return

    while True:
        try:
            conn, addr = server.accept()
            data = conn.recv(1048576)  # 1MB buffer for photos/media
            if data:
                text = data.decode('utf-8')
                obj = json.loads(text)
                handle_remote_event(obj)
            conn.close()
        except Exception:
            pass

def handle_remote_event(obj):
    msg_type = obj.get("type")
    sender = obj.get("sender", {})
    
    # Try to decrypt encryptedData if present
    data = None
    if "encryptedData" in obj:
        data = decrypt_payload(obj["encryptedData"])
    if not data:
        data = obj.get("data", {})

    if not sender or (current_web_user and sender.get("id") == current_web_user.get("id")):
        return

    if msg_type == "NEW_POST":
        post_obj = {
            "id": data.get("id", str(uuid.uuid4())),
            "user": sender,
            "body": data.get("body", ""),
            "time": "Just now",
            "mediaBase64": data.get("mediaBase64", ""),
            "mediaMimeType": data.get("mediaMimeType", ""),
            "audioDurationMs": data.get("audioDurationMs", 0),
            "views": data.get("views", 1),
            "likes": 0, "dislikes": 0, "reshares": 0,
            "likedBy": []
        }
        if not any(p["id"] == post_obj["id"] for p in posts_db):
            posts_db.insert(0, post_obj)
            broadcast_event("NEW_POST", post_obj)

    elif msg_type == "NEW_ROOM":
        room_obj = {
            "id": data.get("id", str(uuid.uuid4())),
            "name": data.get("name", "New Room"),
            "icon": data.get("icon", "💬"),
            "preview": data.get("preview", ""),
            "memberCount": data.get("memberCount", 1),
            "isPrivate": data.get("isPrivate", False),
            "adminId": data.get("adminId", ""),
            "messages": [],
            "members": data.get("members", [])
        }
        if not any(r["id"] == room_obj["id"] for r in rooms_db):
            rooms_db.append(room_obj)
            broadcast_event("NEW_ROOM", room_obj)

    elif msg_type == "CHAT_MESSAGE":
        room_id = data.get("roomId")
        msg_obj = {
            "id": data.get("id", str(uuid.uuid4())),
            "sender": sender,
            "body": data.get("body", ""),
            "kind": data.get("kind", "TEXT"),
            "audioBase64": data.get("audioBase64", ""),
            "audioDurationMs": data.get("audioDurationMs", 0),
            "mediaBase64": data.get("mediaBase64", ""),
            "mediaMimeType": data.get("mediaMimeType", ""),
            "replyToId": data.get("replyToId"),
            "replyToSender": data.get("replyToSender"),
            "replyToBody": data.get("replyToBody"),
            "reactions": {}
        }
        for r in rooms_db:
            if r["id"] == room_id:
                if not any(m.get("id") == msg_obj["id"] for m in r["messages"]):
                    r["messages"].append(msg_obj)
                    r["preview"] = f"{sender.get('alias')}: {data.get('body')}"
                    broadcast_event("CHAT_MESSAGE", {"roomId": room_id, "message": msg_obj})
                break

    elif msg_type in ("LIKE_POST", "DISLIKE_POST", "RESHARE_POST"):
        post_id = data.get("postId")
        for p in posts_db:
            if p["id"] == post_id:
                if msg_type == "LIKE_POST":
                    p["likes"] = p.get("likes", 0) + 1
                elif msg_type == "DISLIKE_POST":
                    p["dislikes"] = p.get("dislikes", 0) + 1
                elif msg_type == "RESHARE_POST":
                    p["reshares"] = p.get("reshares", 0) + 1
                broadcast_event("UPDATE_POST", p)
                break

def send_to_android_peers(type_str, payload_data):
    if not current_web_user:
        return
    
    enc = encrypt_payload(payload_data)
    msg_obj = {
        "version": 1,
        "id": str(uuid.uuid4()),
        "type": type_str,
        "sourcePeerId": current_web_user.get("id"),
        "timestamp": int(time.time() * 1000),
        "ttl": 6,
        "meshId": f"WEB-{current_web_user.get('id', '0000')[:4]}",
        "sender": current_web_user,
        "data": payload_data,
        "encryptedData": enc
    }
    msg_bytes = json.dumps(msg_obj).encode('utf-8')
    for p in list(discovered_peers.values()):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(2.0)
            s.connect((p["ip"], TCP_PORT))
            s.sendall(msg_bytes)
            s.close()
        except Exception:
            pass

def broadcast_event(event_name, payload):
    data_str = f"event: {event_name}\ndata: {json.dumps(payload)}\n\n"
    dead_clients = []
    for wfile in list(event_clients):
        try:
            wfile.write(data_str.encode('utf-8'))
            wfile.flush()
        except Exception:
            dead_clients.append(wfile)
    for d in dead_clients:
        if d in event_clients:
            event_clients.remove(d)

# --- HTTP HANDLER ---
class ScreamHttpHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass

    def do_GET(self):
        if self.path == '/api/events':
            self.send_response(200)
            self.send_header('Content-Type', 'text/event-stream')
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('Connection', 'keep-alive')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()

            event_clients.append(self.wfile)

            init_payload = {
                "peers": [p["user"] for p in discovered_peers.values()],
                "posts": posts_db,
                "rooms": rooms_db
            }
            try:
                init_str = f"event: INIT\ndata: {json.dumps(init_payload)}\n\n"
                self.wfile.write(init_str.encode('utf-8'))
                self.wfile.flush()
            except Exception:
                return

            while True:
                time.sleep(10)
                try:
                    self.wfile.write(b": keepalive\n\n")
                    self.wfile.flush()
                except Exception:
                    break
            return

        super().do_GET()

    def do_POST(self):
        global current_web_user
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')
        data = json.loads(body) if body else {}

        if self.path == '/api/register':
            current_web_user = data.get("user")
            self._send_json({"status": "ok"})

        elif self.path == '/api/post':
            post_obj = {
                "id": str(uuid.uuid4()),
                "user": current_web_user,
                "body": data.get("body", ""),
                "time": "Just now",
                "mediaBase64": data.get("mediaBase64", ""),
                "mediaMimeType": data.get("mediaMimeType", ""),
                "audioDurationMs": data.get("audioDurationMs", 0),
                "views": 1,
                "likes": 0, "dislikes": 0, "reshares": 0,
                "likedBy": []
            }
            posts_db.insert(0, post_obj)
            broadcast_event("NEW_POST", post_obj)
            send_to_android_peers("NEW_POST", {
                "id": post_obj["id"],
                "body": post_obj["body"],
                "mediaBase64": post_obj["mediaBase64"],
                "mediaMimeType": post_obj["mediaMimeType"],
                "audioDurationMs": post_obj["audioDurationMs"]
            })
            self._send_json({"status": "ok", "post": post_obj})

        elif self.path == '/api/chat':
            room_id = data.get("roomId")
            text = data.get("body", "")
            msg_obj = {
                "id": str(uuid.uuid4()),
                "sender": current_web_user,
                "body": text,
                "kind": data.get("kind", "TEXT"),
                "audioBase64": data.get("audioBase64", ""),
                "audioDurationMs": data.get("audioDurationMs", 0),
                "mediaBase64": data.get("mediaBase64", ""),
                "mediaMimeType": data.get("mediaMimeType", ""),
                "reactions": {}
            }
            for r in rooms_db:
                if r["id"] == room_id:
                    r["messages"].append(msg_obj)
                    r["preview"] = f"{current_web_user.get('alias')}: {text}"
                    break
            broadcast_event("CHAT_MESSAGE", {"roomId": room_id, "message": msg_obj})
            send_to_android_peers("CHAT_MESSAGE", {
                "id": msg_obj["id"],
                "roomId": room_id,
                "body": text,
                "kind": msg_obj["kind"],
                "audioBase64": msg_obj["audioBase64"],
                "audioDurationMs": msg_obj["audioDurationMs"],
                "mediaBase64": msg_obj["mediaBase64"],
                "mediaMimeType": msg_obj["mediaMimeType"]
            })
            self._send_json({"status": "ok"})

        elif self.path == '/api/room/create':
            new_room = {
                "id": str(uuid.uuid4()),
                "name": data.get("name", "New Room"),
                "icon": data.get("icon", "💬"),
                "preview": "Room created",
                "memberCount": 1,
                "isPrivate": data.get("isPrivate", False),
                "adminId": current_web_user.get("id"),
                "messages": [],
                "members": [current_web_user.get("alias")]
            }
            rooms_db.append(new_room)
            broadcast_event("NEW_ROOM", new_room)
            send_to_android_peers("NEW_ROOM", new_room)
            self._send_json({"status": "ok", "room": new_room})

        elif self.path in ('/api/like', '/api/dislike', '/api/reshare'):
            post_id = data.get("postId")
            action_type = "LIKE_POST" if self.path == '/api/like' else ("DISLIKE_POST" if self.path == '/api/dislike' else "RESHARE_POST")
            for p in posts_db:
                if p["id"] == post_id:
                    if self.path == '/api/like':
                        p["likes"] = p.get("likes", 0) + 1
                    elif self.path == '/api/dislike':
                        p["dislikes"] = p.get("dislikes", 0) + 1
                    elif self.path == '/api/reshare':
                        p["reshares"] = p.get("reshares", 0) + 1
                    broadcast_event("UPDATE_POST", p)
                    break
            send_to_android_peers(action_type, {"postId": post_id})
            self._send_json({"status": "ok"})

        else:
            self.send_error(404)

    def _send_json(self, obj):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps(obj).encode('utf-8'))

if __name__ == "__main__":
    web_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(web_dir)
    threading.Thread(target=run_udp_mesh, daemon=True).start()
    threading.Thread(target=run_tcp_server, daemon=True).start()
    
    print(f"SCREAM Web Mesh Server running at http://localhost:{HTTP_PORT}")
    with socketserver.TCPServer(("", HTTP_PORT), ScreamHttpHandler) as httpd:
        httpd.serve_forever()
