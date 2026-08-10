// ── SCREAM WEB CLIENT ENGINE ──
let currentUser = null;
let posts = [];
let rooms = [];
let activePeers = [];
let currentRoom = null;
let selectedEmoji = '😎';

// Media Attachment State
let attachedPhotoBase64 = null;
let attachedPhotoMime = null;
let recordedAudioBase64 = null;
let recordedAudioDurationMs = 0;

// Voice Recorder State
let mediaRecorder = null;
let audioChunks = [];
let recordStartTime = 0;
let recordTimerInterval = null;

// ── ONBOARDING AVATAR PICKER ──
document.querySelectorAll('.emoji-opt').forEach(el => {
  el.addEventListener('click', () => {
    document.querySelectorAll('.emoji-opt').forEach(e => e.style.transform = 'scale(1)');
    el.style.transform = 'scale(1.25)';
    selectedEmoji = el.dataset.emoji;
    document.getElementById('avatar-display').textContent = selectedEmoji;
  });
});

document.getElementById('btn-enter').addEventListener('click', enterApp);

function enterApp() {
  const aliasInput = document.getElementById('input-alias').value.trim();
  const alias = aliasInput || 'Anonymous';
  const age = document.getElementById('input-age').value.trim();
  const gender = document.getElementById('select-gender').value;

  const rawId = Math.random().toString(36).substring(2, 6).toUpperCase();
  const userId = `#${rawId}`;

  currentUser = {
    id: userId,
    alias: alias,
    avatar: selectedEmoji,
    age: age,
    gender: gender
  };

  // Switch to App Screen
  document.getElementById('screen-onboarding').classList.remove('active');
  document.getElementById('screen-app').classList.add('active');

  // Update Profile Tab
  document.getElementById('profile-avatar-display').textContent = currentUser.avatar;
  document.getElementById('profile-alias-display').textContent = currentUser.alias;
  document.getElementById('profile-mesh-id').textContent = `SCREAM-${currentUser.id}`;
  document.getElementById('composer-avatar').textContent = currentUser.avatar;

  // Register with local web bridge server
  fetch('/api/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ user: currentUser })
  }).catch(e => console.warn('Register warning:', e));

  // Connect Real-Time SSE Stream
  initEventSource();
}

// ── TAB NAVIGATION ──
document.querySelectorAll('.nav-tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.nav-tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
    tab.classList.add('active');
    const targetTab = tab.dataset.tab;
    document.getElementById(`tab-${targetTab}`).classList.add('active');
  });
});

// ── PHOTO ATTACHMENT HANDLER ──
const photoInput = document.getElementById('input-file-photo');
if (photoInput) {
  photoInput.addEventListener('change', (e) => {
    const file = e.target.files[0];
    if (!file) return;
    attachedPhotoMime = file.type || 'image/jpeg';
    const reader = new FileReader();
    reader.onload = (event) => {
      const base64Data = event.target.result.split(',')[1];
      attachedPhotoBase64 = base64Data;
      const previewImg = document.getElementById('preview-image');
      previewImg.src = event.target.result;
      previewImg.classList.remove('hidden');
      document.getElementById('attachment-preview').classList.remove('hidden');
    };
    reader.readAsDataURL(file);
  });
}

// ── VOICE NOTE RECORDING ──
const btnRecordVoice = document.getElementById('btn-record-voice');
if (btnRecordVoice) {
  btnRecordVoice.addEventListener('click', async () => {
    if (mediaRecorder && mediaRecorder.state === 'recording') {
      stopRecording();
    } else {
      startRecording();
    }
  });
}

async function startRecording() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    alert('Audio recording is not supported in this browser.');
    return;
  }
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioChunks = [];
    mediaRecorder = new MediaRecorder(stream);
    
    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) audioChunks.push(e.data);
    };

    mediaRecorder.onstop = () => {
      const blob = new Blob(audioChunks, { type: 'audio/webm' });
      const reader = new FileReader();
      reader.onloadend = () => {
        recordedAudioBase64 = reader.result.split(',')[1];
        document.getElementById('preview-audio').classList.remove('hidden');
        document.getElementById('attachment-preview').classList.remove('hidden');
      };
      reader.readAsDataURL(blob);

      // Stop audio stream tracks
      stream.getTracks().forEach(track => track.stop());
    };

    mediaRecorder.start();
    recordStartTime = Date.now();
    document.getElementById('recording-indicator').classList.remove('hidden');
    
    recordTimerInterval = setInterval(() => {
      const elapsed = Math.floor((Date.now() - recordStartTime) / 1000);
      recordedAudioDurationMs = elapsed * 1000;
      const mins = String(Math.floor(elapsed / 60)).padStart(2, '0');
      const secs = String(elapsed % 60).padStart(2, '0');
      document.getElementById('rec-timer').textContent = `${mins}:${secs}`;
      document.getElementById('recorded-audio-duration').textContent = `${mins}:${secs}`;
    }, 1000);

  } catch (err) {
    alert('Microphone permission denied or not available.');
  }
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    mediaRecorder.stop();
    clearInterval(recordTimerInterval);
    document.getElementById('recording-indicator').classList.add('hidden');
  }
}

// Clear recorded audio preview
const btnClearAudio = document.getElementById('btn-clear-audio');
if (btnClearAudio) {
  btnClearAudio.addEventListener('click', () => {
    recordedAudioBase64 = null;
    recordedAudioDurationMs = 0;
    document.getElementById('preview-audio').classList.add('hidden');
  });
}

// ── PUBLISH FEED POST ──
document.getElementById('btn-publish-post').addEventListener('click', () => {
  const bodyText = document.getElementById('composer-text').value.trim();
  if (!bodyText && !attachedPhotoBase64 && !recordedAudioBase64) {
    alert('Please enter a message or attach media.');
    return;
  }

  const payload = {
    body: bodyText,
    mediaBase64: attachedPhotoBase64 || "",
    mediaMimeType: attachedPhotoMime || "",
    audioDurationMs: recordedAudioDurationMs
  };

  fetch('/api/post', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  }).then(() => {
    document.getElementById('composer-text').value = '';
    attachedPhotoBase64 = null;
    recordedAudioBase64 = null;
    recordedAudioDurationMs = 0;
    document.getElementById('preview-image').classList.add('hidden');
    document.getElementById('preview-audio').classList.add('hidden');
    document.getElementById('attachment-preview').classList.add('hidden');
  });
});

// ── REAL-TIME EVENT SOURCE (SSE) ──
function initEventSource() {
  const evtSource = new EventSource('/api/events');

  evtSource.addEventListener('INIT', (e) => {
    const data = JSON.parse(e.data);
    activePeers = data.peers || [];
    posts = data.posts || [];
    rooms = data.rooms || [];
    renderPeerCount();
    renderPosts();
    renderRooms();
  });

  evtSource.addEventListener('PEERS_UPDATE', (e) => {
    activePeers = JSON.parse(e.data);
    renderPeerCount();
  });

  evtSource.addEventListener('NEW_POST', (e) => {
    const post = JSON.parse(e.data);
    if (!posts.some(p => p.id === post.id)) {
      posts.unshift(post);
      renderPosts();
    }
  });

  evtSource.addEventListener('NEW_ROOM', (e) => {
    const room = JSON.parse(e.data);
    if (!rooms.some(r => r.id === room.id)) {
      rooms.push(room);
      renderRooms();
    }
  });

  evtSource.addEventListener('CHAT_MESSAGE', (e) => {
    const data = JSON.parse(e.data);
    const room = rooms.find(r => r.id === data.roomId);
    if (room) {
      if (!room.messages) room.messages = [];
      room.messages.push(data.message);
      room.preview = `${data.message.sender.alias}: ${data.message.body}`;
      renderRooms();
      if (currentRoom && currentRoom.id === data.roomId) {
        renderChatMessages();
      }
    }
  });

  evtSource.addEventListener('UPDATE_POST', (e) => {
    const updatedPost = JSON.parse(e.data);
    const idx = posts.findIndex(p => p.id === updatedPost.id);
    if (idx !== -1) {
      posts[idx] = { ...posts[idx], ...updatedPost };
      renderPosts();
    }
  });
}

// ── UI RENDER HELPERS ──
function renderPeerCount() {
  const count = activePeers.length + 1;
  document.getElementById('txt-peer-count').textContent = `${count} Peer${count > 1 ? 's' : ''}`;
  document.getElementById('stat-peers').textContent = count;
}

function renderPosts() {
  const feedList = document.getElementById('feed-list');
  document.getElementById('stat-posts').textContent = posts.filter(p => p.user && p.user.id === currentUser.id).length;

  if (posts.length === 0) {
    feedList.innerHTML = '<div class="empty-state">No mesh broadcasts yet. Be the first to scream!</div>';
    return;
  }

  feedList.innerHTML = posts.map(post => {
    const user = post.user || { alias: 'Anonymous', avatar: '😎' };
    const imgHtml = post.mediaBase64 ? `<img src="data:${post.mediaMimeType || 'image/jpeg'};base64,${post.mediaBase64}" class="post-image-attachment" />` : '';
    const audioHtml = post.audioDurationMs ? `<div class="audio-waveform-bar"><button class="btn-circle-sm" onclick="playAudio('data:audio/webm;base64,${post.mediaBase64}')">▶</button><span>Voice note (${Math.round(post.audioDurationMs / 1000)}s)</span></div>` : '';

    return `
      <div class="card post-card">
        <div class="post-header">
          <div class="post-user">
            <span>${user.avatar || '😎'}</span>
            <span>${user.alias}</span>
          </div>
          <span class="post-time">${post.time || 'Just now'}</span>
        </div>
        <div class="post-body">${escapeHtml(post.body || '')}</div>
        ${imgHtml}
        ${audioHtml}
        <div class="post-footer">
          <button class="post-action-btn" onclick="likePost('${post.id}')">❤️ ${post.likes || 0}</button>
          <button class="post-action-btn" onclick="dislikePost('${post.id}')">👎 ${post.dislikes || 0}</button>
          <button class="post-action-btn" onclick="resharePost('${post.id}')">🔁 ${post.reshares || 0}</button>
        </div>
      </div>
    `;
  }).join('');
}

function renderRooms() {
  const roomsList = document.getElementById('rooms-list');
  const privateList = document.getElementById('private-rooms-list');

  const publicRooms = rooms.filter(r => !r.isPrivate);
  const privateRooms = rooms.filter(r => r.isPrivate);

  roomsList.innerHTML = publicRooms.length ? publicRooms.map(r => `
    <div class="card room-card" onclick="openChat('${r.id}')">
      <div class="room-icon">${r.icon || '💬'}</div>
      <div class="room-info">
        <div class="room-name">${escapeHtml(r.name)} ${r.adminId === currentUser?.id ? '<span class="admin-badge">Admin</span>' : ''}</div>
        <div class="room-preview">${escapeHtml(r.preview || 'No messages yet')}</div>
      </div>
    </div>
  `).join('') : '<div class="empty-state">No public rooms yet. Click "+ Create Room" to start one!</div>';

  let privateHtml = activePeers.map(peer => `
    <div class="card room-card" onclick="startPrivateChatWithPeer('${peer.id}')">
      <div class="room-icon">${peer.avatar || '👤'}</div>
      <div class="room-info">
        <div class="room-name">
          <span class="status-dot green"></span> ${escapeHtml(peer.alias || 'Peer')}
        </div>
        <div class="room-preview">Click to start 1-on-1 private chat</div>
      </div>
    </div>
  `).join('');

  if (privateRooms.length) {
    privateHtml += privateRooms.map(r => `
      <div class="card room-card" onclick="openChat('${r.id}')">
        <div class="room-icon">${r.icon || '🔒'}</div>
        <div class="room-info">
          <div class="room-name">${escapeHtml(r.name)}</div>
          <div class="room-preview">${escapeHtml(r.preview || 'Private encrypted chat')}</div>
        </div>
      </div>
    `).join('');
  }

  privateList.innerHTML = privateHtml || '<div class="empty-state">No nearby peers online to chat with yet.</div>';
}

function startPrivateChatWithPeer(peerId) {
  const peer = activePeers.find(p => p.id === peerId);
  const roomId = `private_${peerId}`;
  let room = rooms.find(r => r.id === roomId);

  if (!room) {
    room = {
      id: roomId,
      name: `Private: ${peer ? peer.alias : 'Peer'}`,
      icon: peer ? (peer.avatar || '🔒') : '🔒',
      preview: 'Private chat started',
      memberCount: 2,
      isPrivate: true,
      adminId: currentUser.id,
      messages: []
    };
    rooms.push(room);
    renderRooms();
  }
  openChat(roomId);
}

function openChat(roomId) {
  currentRoom = rooms.find(r => r.id === roomId);
  if (!currentRoom) return;

  document.getElementById('chat-room-icon').textContent = currentRoom.icon || '💬';
  document.getElementById('chat-room-name').textContent = currentRoom.name;
  document.getElementById('chat-room-sub').textContent = `🟢 Active · ${currentRoom.memberCount || 1} member online`;

  renderChatMessages();
  document.getElementById('overlay-chat').classList.remove('hidden');
}

function renderChatMessages() {
  if (!currentRoom) return;
  const container = document.getElementById('chat-messages-list');
  const msgs = currentRoom.messages || [];

  container.innerHTML = msgs.map(m => {
    const isMine = m.sender && m.sender.id === currentUser.id;
    return `
      <div class="chat-msg ${isMine ? 'mine' : 'other'}">
        <span class="chat-sender">${m.sender?.alias || 'Peer'}</span>
        <div class="chat-body">${escapeHtml(m.body)}</div>
      </div>
    `;
  }).join('');

  container.scrollTop = container.scrollHeight;
}

document.getElementById('btn-close-chat').addEventListener('click', () => {
  document.getElementById('overlay-chat').classList.add('hidden');
  currentRoom = null;
});

document.getElementById('chat-btn-send').addEventListener('click', () => {
  const text = document.getElementById('chat-input-text').value.trim();
  if (!text || !currentRoom) return;

  fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roomId: currentRoom.id, body: text })
  }).then(() => {
    document.getElementById('chat-input-text').value = '';
  });
});

// ── CREATE ROOM MODAL ──
document.getElementById('btn-create-room').addEventListener('click', () => {
  document.getElementById('modal-create-room').classList.remove('hidden');
});

document.getElementById('btn-cancel-room').addEventListener('click', () => {
  document.getElementById('modal-create-room').classList.add('hidden');
});

document.getElementById('btn-confirm-room').addEventListener('click', () => {
  const name = document.getElementById('modal-room-name').value.trim();
  const icon = document.getElementById('modal-room-icon').value.trim() || '💬';
  const isPrivate = document.getElementById('modal-room-private').checked;

  if (!name) return;

  fetch('/api/room/create', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, icon, isPrivate })
  }).then(() => {
    document.getElementById('modal-create-room').classList.add('hidden');
  });
});

// ── WEB BLUETOOTH SCANNING ──
const btnWebBt = document.getElementById('btn-web-bt');
if (btnWebBt) {
  btnWebBt.addEventListener('click', async () => {
    if (!navigator.bluetooth) {
      alert('Web Bluetooth is not supported on this browser/OS. Requires Chrome / Edge / Opera over HTTPS or localhost.');
      return;
    }
    try {
      const device = await navigator.bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: ['0000fea5-0000-1000-8000-00805f9b34fb']
      });
      alert(`Discovered Bluetooth device: ${device.name || device.id}`);
    } catch (e) {
      console.warn('Web Bluetooth scan cancelled or error:', e);
    }
  });
}

// ── DIAGNOSTICS & TOPOLOGY OVERLAY ──
document.getElementById('btn-peers').addEventListener('click', () => {
  const peersList = document.getElementById('peers-list');
  peersList.innerHTML = activePeers.map(p => `
    <div class="card" style="margin-bottom:10px;">
      <div style="display:flex;align-items:center;gap:10px;">
        <span>${p.avatar || '📱'}</span>
        <div>
          <strong>${escapeHtml(p.alias || 'Anonymous')}</strong>
          <div style="font-size:12px;color:var(--text-muted);">IP: ${p.ip || 'LAN / BLE'} | Battery: ${p.batteryLevel || 95}%</div>
        </div>
      </div>
    </div>
  `).join('') || '<div class="empty-state">No other peers currently connected to mesh.</div>';
  document.getElementById('modal-peers').classList.remove('hidden');
});

document.getElementById('btn-close-peers').addEventListener('click', () => {
  document.getElementById('modal-peers').classList.add('hidden');
});

// Actions
function likePost(id) { fetch('/api/like', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ postId: id }) }); }
function dislikePost(id) { fetch('/api/dislike', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ postId: id }) }); }
function resharePost(id) { fetch('/api/reshare', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ postId: id }) }); }

function escapeHtml(str) {
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
