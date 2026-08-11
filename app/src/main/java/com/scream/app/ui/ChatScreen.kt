package com.scream.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scream.app.model.ChatMessage
import com.scream.app.model.MessageKind
import com.scream.app.model.ProtocolType
import com.scream.app.model.Room
import com.scream.app.model.User
import com.scream.app.ui.components.AvatarView
import com.scream.app.ui.theme.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    room: Room,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var messageText by remember { mutableStateOf("") }

    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var recordingDuration by remember { mutableStateOf(0L) }

    var showMediaMenu by remember { mutableStateOf(false) }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    var editingVoiceFile by remember { mutableStateOf<File?>(null) }
    var editingVoiceDuration by remember { mutableStateOf(0L) }

    var selectedMessageForDetail by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // Dialog & sheet states
    var showMembersSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf<ChatMessage?>(null) }

    val rooms by viewModel.rooms.collectAsState()
    val activeRoom = rooms.find { it.id == room.id } ?: room
    val currentUser by viewModel.currentUser.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val activePeers by viewModel.activePeers.collectAsState()

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        viewModel.sendImageMessage(
            activeRoom.id,
            bytes,
            mimeType,
            replyingToMessage?.id,
            replyingToMessage?.sender?.alias,
            replyingToMessage?.body
        )
        replyingToMessage = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap ?: return@rememberLauncherForActivityResult
        viewModel.sendImageMessage(
            activeRoom.id,
            bitmap.toJpegBytes(),
            "image/jpeg",
            replyingToMessage?.id,
            replyingToMessage?.sender?.alias,
            replyingToMessage?.body
        )
        replyingToMessage = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder == null) {
            val result = startVoiceRecorder(context)
            recorder = result.first
            recordingFile = result.second
            recordingStartedAt = System.currentTimeMillis()
        }
    }

    LaunchedEffect(recorder, recordingStartedAt) {
        if (recorder != null) {
            while (recorder != null) {
                delay(100L)
                recordingDuration = System.currentTimeMillis() - recordingStartedAt
                val remaining = viewModel.maxVoiceDurationMs() - recordingDuration
                if (remaining <= 0L) {
                    recorder?.let {
                        val dur = stopVoiceRecorder(it, recordingStartedAt)
                        recordingFile?.let { file ->
                            editingVoiceFile = file
                            editingVoiceDuration = dur
                        }
                        recorder = null
                        recordingFile = null
                        recordingStartedAt = 0L
                        recordingDuration = 0L
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5000L)
        }
    }

    BackHandler(enabled = isSearching || showMembersSheet || replyingToMessage != null) {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else if (showMembersSheet) {
            showMembersSheet = false
        } else if (replyingToMessage != null) {
            replyingToMessage = null
        }
    }

    val filteredMessages = remember(activeRoom.messages, searchQuery, isSearching) {
        if (isSearching && searchQuery.isNotBlank()) {
            activeRoom.messages.filter { it.body.contains(searchQuery, ignoreCase = true) }
        } else activeRoom.messages
    }

    val pinnedMessage = remember(activeRoom.messages) {
        activeRoom.messages.find { it.pinnedUntil != null && it.pinnedUntil > System.currentTimeMillis() }
    }

    val btStatusColor = when (networkStatus) {
        com.scream.app.model.NetworkStatus.ACTIVE -> StatusConnected
        com.scream.app.model.NetworkStatus.LIMITED -> WarningAmber
        com.scream.app.model.NetworkStatus.OFFLINE -> StatusOffline
    }

    Scaffold(
        containerColor = ScreamBlack,
        topBar = {
            Surface(
                color = ScreamSurface,
                tonalElevation = 0.dp
            ) {
                Column {
                    TopAppBar(
                        title = {
                            if (isSearching) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search messages...", color = ScreamTextTertiary, fontSize = 14.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = ScreamTextTertiary) },
                                    trailingIcon = {
                                        IconButton(onClick = { searchQuery = ""; isSearching = false }, modifier = Modifier.size(20.dp)) {
                                            Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(16.dp), tint = ScreamTextTertiary)
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ScreamOutline,
                                        unfocusedBorderColor = ScreamOutline,
                                        focusedContainerColor = ScreamSurfaceVariant,
                                        unfocusedContainerColor = ScreamSurfaceVariant,
                                        cursorColor = ScreamBlue
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = ScreamTextPrimary)
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AvatarView(avatarStr = activeRoom.icon, size = 40.dp, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activeRoom.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ScreamWhite,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(btStatusColor)
                                            )
                                            Text(
                                                text = "${activeRoom.memberCount} members",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ScreamTextTertiary
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ScreamTextPrimary)
                            }
                        },
                        actions = {
                            if (!isSearching) {
                                if (!activeRoom.name.startsWith("Private:")) {
                                    IconButton(onClick = { showMembersSheet = true }) {
                                        Icon(
                                            Icons.Default.GroupAdd,
                                            contentDescription = if (activeRoom.isPrivate) "Manage Members" else "View Members",
                                            tint = ScreamTextPrimary
                                        )
                                    }
                                }
                                IconButton(onClick = { isSearching = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = ScreamTextPrimary)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ScreamSurface,
                            titleContentColor = ScreamWhite
                        )
                    )

                    AnimatedVisibility(
                        visible = pinnedMessage != null,
                        enter = slideInVertically(tween(200)) + fadeIn(),
                        exit = slideOutVertically(tween(200)) + fadeOut()
                    ) {
                        pinnedMessage?.let { pinMsg ->
                            Surface(
                                color = ScreamSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = null,
                                        tint = ScreamBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Pinned Message",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = ScreamBlue
                                        )
                                        Text(
                                            pinMsg.body,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = ScreamTextSecondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.setChatMessagePinned(activeRoom.id, pinMsg.id, false) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Unpin", modifier = Modifier.size(14.dp), tint = ScreamTextTertiary)
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = ScreamDivider)
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 0.dp,
                color = ScreamSurface
            ) {
                Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                    Divider(color = ScreamDivider)

                    // Reply preview bar
                    AnimatedVisibility(visible = replyingToMessage != null) {
                        replyingToMessage?.let { repMsg ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ScreamSurface)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(ScreamBlue)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Reply to ${repMsg.sender.alias}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ScreamBlue
                                    )
                                    Text(
                                        repMsg.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = ScreamTextSecondary
                                    )
                                }
                                IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp), tint = ScreamTextTertiary)
                                }
                            }
                        }
                    }

                    // Recording state bar
                    AnimatedVisibility(visible = recorder != null) {
                        Surface(
                            color = ScreamSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "rec")
                                val pulseAlpha by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.3f,
                                    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                                    label = "pulse"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(ErrorRed.copy(alpha = pulseAlpha))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Recording ${formatRecordingDuration(recordingDuration)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ErrorRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Composer input row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attachment trigger button
                        Box {
                            IconButton(
                                onClick = { showMediaMenu = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ScreamSurfaceVariant)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Attachments", modifier = Modifier.size(20.dp), tint = ScreamTextSecondary)
                            }
                            DropdownMenu(
                                expanded = showMediaMenu,
                                onDismissRequest = { showMediaMenu = false },
                                modifier = Modifier.background(ScreamSurfaceVariant)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Camera Capture", style = MaterialTheme.typography.bodyMedium, color = ScreamTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(20.dp), tint = ScreamTextSecondary) },
                                    onClick = { showMediaMenu = false; cameraLauncher.launch(null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Photo Library", style = MaterialTheme.typography.bodyMedium, color = ScreamTextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(20.dp), tint = ScreamTextSecondary) },
                                    onClick = { showMediaMenu = false; galleryLauncher.launch("image/*") }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Text input field
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("Message", style = MaterialTheme.typography.bodyMedium, color = ScreamTextTertiary) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ScreamSurfaceVariant,
                                unfocusedContainerColor = ScreamSurfaceVariant,
                                focusedBorderColor = ScreamOutline,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = ScreamBlue
                            ),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = ScreamTextPrimary)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        val isRecording = recorder != null
                        val hasText = messageText.isNotBlank()

                        if (!hasText) {
                            // Mic recording button
                            IconButton(
                                onClick = {
                                    if (!isRecording) {
                                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            val result = startVoiceRecorder(context)
                                            recorder = result.first
                                            recordingFile = result.second
                                            recordingStartedAt = System.currentTimeMillis()
                                        } else {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    } else {
                                        recorder?.let {
                                            val dur = stopVoiceRecorder(it, recordingStartedAt)
                                            recordingFile?.let { file ->
                                                editingVoiceFile = file
                                                editingVoiceDuration = dur
                                            }
                                        }
                                        recorder = null
                                        recordingFile = null
                                        recordingStartedAt = 0L
                                        recordingDuration = 0L
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) ErrorRed else ScreamSurfaceVariant)
                            ) {
                                Icon(
                                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Stop recording" else "Record voice",
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isRecording) ScreamWhite else ScreamTextSecondary
                                )
                            }
                        } else {
                            // Send text button
                            IconButton(
                                onClick = {
                                    viewModel.sendChatMessage(
                                        activeRoom.id,
                                        messageText.trim(),
                                        replyingToMessage?.id,
                                        replyingToMessage?.sender?.alias,
                                        replyingToMessage?.body
                                    )
                                    messageText = ""
                                    replyingToMessage = null
                                    keyboardController?.hide()
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ScreamBlue)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send message",
                                    modifier = Modifier.size(18.dp),
                                    tint = ScreamWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreamBlack)
        ) {
            if (filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📡", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isSearching) "No matches found" else "Mesh Room Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ScreamTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isSearching) "Try another search term" else "Send a message to propagate offline mesh nodes automatically via Bluetooth.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ScreamTextTertiary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredMessages, key = { it.id }) { msg ->
                        CustomChatBubble(
                            msg = msg,
                            avatar = if (activeRoom.isPrivate) {
                                msg.sender.profileImage ?: msg.sender.avatar
                            } else {
                                msg.sender.avatar
                            },
                            onDeleteForMe = { viewModel.deleteChatMessageForMe(activeRoom.id, msg.id) },
                            onDeleteForEveryone = { viewModel.deleteChatMessageForEveryone(activeRoom.id, msg.id) },
                            onPin = { viewModel.setChatMessagePinned(activeRoom.id, msg.id, msg.pinnedUntil == null) },
                            onReply = { replyingToMessage = msg },
                            onForward = { forwardingMessage = msg },
                            onPlayVoice = { playVoiceMessage(context, msg) },
                            onReactionToggle = { emoji -> viewModel.toggleChatMessageReaction(activeRoom.id, msg.id, emoji) },
                            onBookmarkToggle = { viewModel.toggleChatMessageBookmark(activeRoom.id, msg.id) },
                            onShowDetail = { selectedMessageForDetail = msg },
                            now = now
                        )
                    }
                }
            }
        }
    }

    editingVoiceFile?.let { voiceFile ->
        VoiceEditorScreen(
            audioFile = voiceFile,
            durationMs = editingVoiceDuration,
            onSend = { finalFile, finalDur ->
                viewModel.sendVoiceMessage(
                    activeRoom.id,
                    finalFile,
                    finalDur,
                    replyingToMessage?.id,
                    replyingToMessage?.sender?.alias,
                    replyingToMessage?.body
                )
                editingVoiceFile = null
                replyingToMessage = null
            },
            onDiscard = {
                voiceFile.delete()
                editingVoiceFile = null
            }
        )
    }

    selectedMessageForDetail?.let { detailMsg ->
        MessageDetailOverlay(msg = detailMsg, onDismiss = { selectedMessageForDetail = null })
    }

    forwardingMessage?.let { fMsg ->
        AlertDialog(
            onDismissRequest = { forwardingMessage = null },
            containerColor = ScreamSurfaceVariant,
            title = { Text("Forward Message", style = MaterialTheme.typography.titleMedium, color = ScreamTextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rooms.forEach { targetRoom ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (fMsg.kind == MessageKind.TEXT) {
                                            viewModel.sendChatMessage(targetRoom.id, fMsg.body)
                                        } else if (fMsg.kind == MessageKind.VOICE && fMsg.audioBase64 != null) {
                                            val bytes = Base64.decode(fMsg.audioBase64, Base64.NO_WRAP)
                                            val file = File.createTempFile("scream_fwd_", ".m4a", context.cacheDir)
                                            file.writeBytes(bytes)
                                            viewModel.sendVoiceMessage(targetRoom.id, file, fMsg.audioDurationMs)
                                        }
                                        forwardingMessage = null
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(targetRoom.icon, fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(targetRoom.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = ScreamTextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { forwardingMessage = null }) {
                    Text("Cancel", color = ScreamTextSecondary)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ── Delete Confirmation Dialog (Delete for Me vs Everyone) ───────────────
    if (showDeleteConfirmDialog != null) {
        val targetMsg = showDeleteConfirmDialog!!
        val isOwnMessage = targetMsg.sender.id == currentUser?.id

        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            containerColor = ScreamSurfaceVariant,
            title = { Text("Delete Message", color = ScreamWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this message?", color = ScreamTextSecondary) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isOwnMessage) {
                        Button(
                            onClick = {
                                viewModel.deleteChatMessageForEveryone(activeRoom.id, targetMsg.id)
                                showDeleteConfirmDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete for Everyone", color = ScreamWhite)
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.deleteChatMessageForMe(activeRoom.id, targetMsg.id)
                            showDeleteConfirmDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ScreamSurfaceTop),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for Me", color = ScreamTextPrimary)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel", color = ScreamTextSecondary)
                }
            }
        )
    }

    // ── Private Room Invite Members Sheet ────────────────────────────────────
    if (showMembersSheet) {
        val currentMembers = if (activeRoom.isPrivate) {
            activeRoom.members
        } else {
            activePeers.map { it.user.id }
        }
        val nonMembers = activePeers.filter { peer ->
            activeRoom.isPrivate && peer.user.id != currentUser?.id && !currentMembers.contains(peer.user.id)
        }

        Dialog(onDismissRequest = { showMembersSheet = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                shape = RoundedCornerShape(24.dp),
                color = ScreamSurfaceVariant
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Room Members",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ScreamWhite
                        )
                        IconButton(onClick = { showMembersSheet = false }) {
                            Icon(Icons.Default.Close, null, tint = ScreamTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "Current Members (${currentMembers.size + 1})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScreamTextTertiary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Admin row
                        item {
                            Surface(
                                color = ScreamSurfaceTop,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AvatarView(avatarStr = currentUser?.avatar.orEmpty(), size = 32.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "You (Owner)",
                                        color = ScreamWhite,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Added members
                        items(currentMembers) { memberId ->
                            val peer = activePeers.find { it.user.id == memberId }
                            val alias = peer?.user?.alias ?: "Mesh User $memberId"
                            val avatar = peer?.user?.avatar ?: "😎"
                            Surface(
                                color = ScreamSurfaceTop,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AvatarView(avatarStr = avatar, size = 32.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(alias, color = ScreamTextPrimary, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (activeRoom.isPrivate) {
                                        IconButton(
                                            onClick = { viewModel.removeUserFromPrivateRoom(activeRoom.id, memberId) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.RemoveCircle, "Remove", tint = ErrorRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (activeRoom.isPrivate) {
                        Text(
                            "Add Nearby Peers",
                            style = MaterialTheme.typography.labelSmall,
                            color = ScreamTextTertiary
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                        if (nonMembers.isEmpty()) {
                            item {
                                Text(
                                    "No new peers in Bluetooth range",
                                    color = ScreamTextTertiary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            items(nonMembers) { peer ->
                                Surface(
                                    color = ScreamSurfaceTop,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AvatarView(avatarStr = peer.user.avatar, size = 32.dp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(peer.user.alias, color = ScreamTextPrimary, style = MaterialTheme.typography.bodyMedium)
                                        }
                                        IconButton(
                                            onClick = { viewModel.inviteUserToPrivateRoom(activeRoom.id, peer.user.id) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.AddCircle, "Add", tint = ScreamBlue, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomChatBubble(
    msg: ChatMessage,
    avatar: String = msg.sender.avatar,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onPin: () -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onPlayVoice: () -> Unit,
    onReactionToggle: (String) -> Unit,
    onBookmarkToggle: () -> Unit,
    onShowDetail: () -> Unit,
    now: Long
) {
    val isMine = msg.isMine
    var showMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        if (!isMine) {
            AvatarView(avatarStr = avatar, size = 36.dp, fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            if (!isMine) {
                Text(
                    text = msg.sender.alias,
                    style = MaterialTheme.typography.labelSmall,
                    color = ScreamTextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Surface(
                modifier = Modifier.combinedClickable(
                    onClick = { showMenu = true },
                    onLongClick = { showReactionPicker = true }
                ),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                ),
                color = if (isMine) ScreamBlue else ScreamSurfaceVariant,
                contentColor = ScreamWhite
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    if (msg.replyToId != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isMine) Color.White.copy(alpha = 0.12f) else ScreamSurfaceTop
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = msg.replyToSender ?: "Anonymous",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMine) ScreamWhite else ScreamBlue
                                )
                                Text(
                                    text = msg.replyToBody ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isMine) ScreamWhite.copy(alpha = 0.7f) else ScreamTextSecondary
                                )
                            }
                        }
                    }

                    when (msg.kind) {
                        MessageKind.VOICE -> VoiceBubbleContent(msg = msg, isMine = isMine, onPlay = onPlayVoice)
                        MessageKind.IMAGE -> {
                            val bitmap = remember(msg.mediaBase64) { msg.mediaBase64?.toBitmap() }
                            if (bitmap != null) {
                                ComposeImage(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Image message",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(text = "Image loading failed", style = MaterialTheme.typography.bodySmall, color = ScreamTextSecondary)
                            }
                        }
                        MessageKind.TEXT -> {
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp,
                                color = ScreamWhite
                            )
                        }
                    }
                }
            }

            // Reactions list
            if (msg.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    msg.reactions.forEach { (emoji, aliases) ->
                        Surface(
                            modifier = Modifier.clickable { onReactionToggle(emoji) },
                            color = ScreamSurfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "$emoji ${aliases.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ScreamTextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bubble Metadata (time & read receipt)
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (msg.isBookmarked) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = ScreamBlue, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = formatRelativeTime(msg.createdAt, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = ScreamTextTertiary,
                    fontSize = 10.sp
                )
                if (msg.protocol == ProtocolType.BITCHAT) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = ScreamViolet.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "BitChat",
                            style = MaterialTheme.typography.labelSmall,
                            color = ScreamViolet,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
                if (isMine) {
                    Spacer(modifier = Modifier.width(6.dp))
                    val deliveryColor = if (msg.deliveryStatus == "Delivered") ScreamBlueLight else ScreamTextTertiary
                    val deliveryIcon = when (msg.deliveryStatus) {
                        "Sending" -> Icons.Default.Schedule
                        "Delivered" -> Icons.Default.DoneAll
                        else -> Icons.Default.Check
                    }
                    Icon(deliveryIcon, contentDescription = msg.deliveryStatus, tint = deliveryColor, modifier = Modifier.size(12.dp))
                }
            }
        }
    }

    if (showReactionPicker) {
        Dialog(onDismissRequest = { showReactionPicker = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = ScreamSurfaceVariant) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83D\uDE4F").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onReactionToggle(emoji)
                                    showReactionPicker = false
                                }
                                .padding(6.dp)
                        )
                    }
                }
            }
        }
    }

    var showDeleteOptions by remember { mutableStateOf(false) }

    if (showMenu) {
        Dialog(onDismissRequest = { showMenu = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = ScreamSurfaceVariant) {
                Column(modifier = Modifier.padding(8.dp).width(220.dp)) {
                    ChatMenuItem(Icons.Default.Reply, "Reply") { onReply(); showMenu = false }
                    ChatMenuItem(Icons.Default.Forward, "Forward") { onForward(); showMenu = false }
                    ChatMenuItem(Icons.Default.Bookmark, if (msg.isBookmarked) "Remove Bookmark" else "Bookmark") { onBookmarkToggle(); showMenu = false }
                    ChatMenuItem(Icons.Default.PushPin, if (msg.pinnedUntil == null) "Pin" else "Unpin") { onPin(); showMenu = false }
                    ChatMenuItem(Icons.Default.Route, "Route Info") { onShowDetail(); showMenu = false }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = ScreamDivider)
                    ChatMenuItem(Icons.Default.Delete, "Delete Message", tint = ErrorRed) {
                        showMenu = false
                        showDeleteOptions = true
                    }
                }
            }
        }
    }

    if (showDeleteOptions) {
        AlertDialog(
            onDismissRequest = { showDeleteOptions = false },
            containerColor = ScreamSurfaceVariant,
            title = { Text("Delete Message", color = ScreamWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Choose how you want to delete this message.", color = ScreamTextSecondary) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isMine) {
                        Button(
                            onClick = {
                                showDeleteOptions = false
                                onDeleteForEveryone()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = ScreamWhite),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete for Everyone", fontWeight = FontWeight.Bold)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            showDeleteOptions = false
                            onDeleteForMe()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for Me", color = ScreamWhite)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteOptions = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel", color = ScreamTextTertiary)
                }
            }
        )
    }
}

@Composable
private fun ChatMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = ScreamTextPrimary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}

@Composable
fun VoiceBubbleContent(
    msg: ChatMessage,
    isMine: Boolean,
    onPlay: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val totalSteps = 80
            val stepDelay = (msg.audioDurationMs / totalSteps).coerceAtLeast(1L)
            while (isPlaying && playProgress < 1f) {
                delay(stepDelay)
                playProgress += 1f / totalSteps
            }
            isPlaying = false
            playProgress = 0f
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = if (isMine) Color.White.copy(alpha = 0.2f) else ScreamBlue.copy(alpha = 0.15f),
            onClick = {
                isPlaying = !isPlaying
                if (isPlaying) onPlay()
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Playback toggle",
                    modifier = Modifier.size(18.dp),
                    tint = if (isMine) ScreamWhite else ScreamBlue
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        val activeColor = if (isMine) ScreamWhite else ScreamBlue
        val inactiveColor = if (isMine) ScreamWhite.copy(alpha = 0.25f) else ScreamTextTertiary

        Canvas(
            modifier = Modifier
                .width(130.dp)
                .height(22.dp)
        ) {
            val w = size.width
            val h = size.height
            val numBars = 24
            val barW = 2.5.dp.toPx()
            val step = w / numBars

            val heights = listOf(0.35f, 0.55f, 0.3f, 0.7f, 0.85f, 0.5f, 0.9f, 0.6f, 0.4f, 0.8f, 0.65f, 0.5f, 0.7f, 0.35f, 0.8f, 0.9f, 0.55f, 0.3f, 0.6f, 0.45f, 0.75f, 0.5f, 0.4f, 0.65f)

            for (i in 0 until numBars) {
                val barH = heights[i] * h
                val fraction = i.toFloat() / numBars
                val active = fraction <= playProgress

                drawRoundRect(
                    color = if (active) activeColor else inactiveColor,
                    topLeft = Offset(i * step + (step - barW) / 2, (h - barH) / 2),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(1.25.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = formatDuration(msg.audioDurationMs),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = ScreamWhite
        )
    }
}

@Composable
fun MessageDetailOverlay(msg: ChatMessage, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ScreamSurfaceVariant,
        title = { Text("Message Route Details", style = MaterialTheme.typography.titleMedium, color = ScreamTextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = ScreamBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Shield, null, tint = ScreamBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Locally Encrypted Transport", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = ScreamBlue)
                    }
                }

                val routeList = msg.route.ifEmpty { listOf(msg.sender.alias) }
                Surface(
                    color = ScreamSurfaceTop,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        routeList.forEachIndexed { index, peer ->
                            Text(
                                text = peer,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (index == routeList.lastIndex) ScreamBlue else ScreamTextPrimary
                            )
                            if (index < routeList.lastIndex) {
                                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.padding(horizontal = 2.dp), tint = ScreamTextTertiary)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Transit Hops", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                        Text("${(routeList.size - 1).coerceAtLeast(0)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ScreamTextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Est. Propagation", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                        Text("~${(routeList.size * 12) + 4} ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ScreamTextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = ScreamBlue, fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun startVoiceRecorder(context: Context): Pair<MediaRecorder, File> {
    val file = File.createTempFile("scream_voice_", ".m4a", context.cacheDir)
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        @Suppress("DEPRECATION")
        MediaRecorder()
    }
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    recorder.setAudioEncodingBitRate(64_000)
    recorder.setAudioSamplingRate(44_100)
    recorder.setOutputFile(file.absolutePath)
    recorder.prepare()
    recorder.start()
    return recorder to file
}

private fun stopVoiceRecorder(recorder: MediaRecorder, startedAt: Long): Long {
    runCatching { recorder.stop() }
    recorder.release()
    return System.currentTimeMillis() - startedAt
}

private fun playVoiceMessage(context: Context, msg: ChatMessage) {
    val audio = msg.audioBase64 ?: return
    val file = File.createTempFile("scream_play_", ".m4a", context.cacheDir)
    file.writeBytes(Base64.decode(audio, Base64.NO_WRAP))
    MediaPlayer().apply {
        setDataSource(file.absolutePath)
        setOnCompletionListener {
            it.release()
            file.delete()
        }
        prepare()
        start()
    }
}

private fun formatDuration(durationMs: Long): String {
    val seconds = (durationMs / 1000L).coerceAtLeast(1L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

private fun formatRecordingDuration(durationMs: Long): String {
    val seconds = durationMs / 1000L
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins}:${secs.toString().padStart(2, '0')}"
}

private fun Bitmap.toJpegBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray()
}

private fun String.toBitmap(): Bitmap? {
    return runCatching {
        val bytes = Base64.decode(this, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}
