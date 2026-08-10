package com.scream.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scream.app.model.Post
import com.scream.app.model.User
import com.scream.app.ui.components.AvatarView
import com.scream.app.ui.theme.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.compose.foundation.Image as ComposeImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: MainViewModel,
    onUserClick: (User) -> Unit
) {
    val context = LocalContext.current
    val posts by viewModel.posts.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var postText by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    // Composer attachment state
    var attachmentBase64 by remember { mutableStateOf<String?>(null) }
    var attachmentMimeType by remember { mutableStateOf<String?>(null) }
    var attachmentDurationMs by remember { mutableStateOf(0L) }

    // Recording states
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordingFile by remember { mutableStateOf<File?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var recordingDuration by remember { mutableStateOf(0L) }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                    attachmentBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                    attachmentMimeType = "image/jpeg"
                    attachmentDurationMs = 0L
                }
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                if (bytes.size > 800_000) {
                    // Alert file too large for mesh
                    android.widget.Toast.makeText(context, "Video is too large for offline sync (max 800KB)", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    attachmentBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    attachmentMimeType = "video/mp4"
                    attachmentDurationMs = 0L
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && recorder == null) {
            val result = startVoiceRecorder(context)
            recorder = result.first
            recordingFile = result.second
            recordingStartedAt = System.currentTimeMillis()
        }
    }

    LaunchedEffect(recorder) {
        if (recorder != null) {
            while (recorder != null) {
                delay(100L)
                recordingDuration = System.currentTimeMillis() - recordingStartedAt
                val remaining = viewModel.maxVoiceDurationMs() - recordingDuration
                if (remaining <= 0L) {
                    recorder?.let {
                        val dur = stopVoiceRecorder(it, recordingStartedAt)
                        recordingFile?.let { file ->
                            attachmentBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                            attachmentMimeType = "audio/m4a"
                            attachmentDurationMs = dur
                            file.delete()
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
            delay(10_000L)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreamBlack)
    ) {
        // ── Composer ────────────────────────────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            color = ScreamSurfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    AvatarView(
                        avatarStr = currentUser?.avatar.orEmpty(),
                        size = 40.dp,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = postText,
                        onValueChange = { if (it.length <= 500) postText = it },
                        placeholder = {
                            Text(
                                "Share to public feed...",
                                color = ScreamTextTertiary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = ScreamTextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = ScreamOutline.copy(alpha = 0.3f),
                            focusedBorderColor = ScreamBlue.copy(alpha = 0.5f),
                            unfocusedContainerColor = ScreamSurfaceTop.copy(alpha = 0.3f),
                            focusedContainerColor = ScreamSurfaceTop.copy(alpha = 0.5f),
                            cursorColor = ScreamBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Voice Recording Live Overlay
                AnimatedVisibility(visible = recorder != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "feed_rec")
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Recording feed audio: ${formatRecordingDuration(recordingDuration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ErrorRed
                        )
                    }
                }

                // Render Attachment preview in composer
                if (attachmentBase64 != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = ScreamSurfaceTop,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    attachmentMimeType?.startsWith("image/") == true -> Icons.Default.Image
                                    attachmentMimeType?.startsWith("video/") == true -> Icons.Default.Videocam
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = ScreamBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    attachmentMimeType?.startsWith("image/") == true -> "Photo Attached"
                                    attachmentMimeType?.startsWith("video/") == true -> "Video Attached"
                                    else -> "Voice Recording Attached (${formatDuration(attachmentDurationMs)})"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ScreamTextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                attachmentBase64 = null
                                attachmentMimeType = null
                                attachmentDurationMs = 0L
                            }) {
                                Icon(Icons.Default.Close, "Remove attachment", tint = ErrorRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attachment options row
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(onClick = { photoLauncher.launch("image/*") }) {
                            Icon(Icons.Default.Photo, "Attach Photo", tint = ScreamTextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { videoLauncher.launch("video/*") }) {
                            Icon(Icons.Default.Videocam, "Attach Video", tint = ScreamTextSecondary, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = {
                            if (recorder == null) {
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
                                        attachmentBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                                        attachmentMimeType = "audio/m4a"
                                        attachmentDurationMs = dur
                                        file.delete()
                                    }
                                }
                                recorder = null
                                recordingFile = null
                                recordingStartedAt = 0L
                                recordingDuration = 0L
                            }
                        }) {
                            Icon(
                                imageVector = if (recorder == null) Icons.Default.Mic else Icons.Default.Stop,
                                contentDescription = "Record Audio",
                                tint = if (recorder != null) ErrorRed else ScreamTextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Send button
                    FilledIconButton(
                        onClick = {
                            if (postText.isNotBlank() || attachmentBase64 != null) {
                                viewModel.createPost(
                                    text = postText.trim(),
                                    mediaBase64 = attachmentBase64,
                                    mediaMimeType = attachmentMimeType,
                                    audioDurationMs = attachmentDurationMs
                                )
                                postText = ""
                                attachmentBase64 = null
                                attachmentMimeType = null
                                attachmentDurationMs = 0L
                            }
                        },
                        enabled = postText.isNotBlank() || attachmentBase64 != null,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = ScreamBlue,
                            contentColor = ScreamWhite,
                            disabledContainerColor = ScreamSurfaceTop,
                            disabledContentColor = ScreamTextTertiary
                        ),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Post",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ── Divider ─────────────────────────────────────────────────────────
        Divider(
            color = ScreamDivider,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // ── Post List ───────────────────────────────────────────────────────
        if (posts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📡",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No posts yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = ScreamTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Be the first to scream into the mesh",
                        style = MaterialTheme.typography.bodySmall,
                        color = ScreamTextTertiary
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(posts, key = { it.id }) { post ->
                    // Increment view locally on render
                    LaunchedEffect(post.id) {
                        viewModel.incrementPostViews(post.id)
                    }

                    PostCard(
                        post = post,
                        now = now,
                        currentUser = currentUser,
                        onUserClick = onUserClick,
                        onLike = { viewModel.likePost(post.id) },
                        onDislike = { viewModel.dislikePost(post.id) },
                        onReshare = { viewModel.resharePost(post.id) },
                        onHideFromFeed = { viewModel.hidePostFromMyFeed(post.id) },
                        onDelete = { viewModel.deletePost(post.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    post: Post,
    now: Long,
    currentUser: User?,
    onUserClick: (User) -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onReshare: () -> Unit,
    onHideFromFeed: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val isOwn = post.user.id == currentUser?.id
    var showLikesPopup by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ScreamSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // ── Header: avatar · alias · ID · time ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onUserClick(post.user) }) {
                    AvatarView(avatarStr = post.user.avatar, size = 36.dp, fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.user.alias,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = ScreamTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = post.user.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = ScreamTextTertiary
                        )
                    }
                    Text(
                        text = "👁️ ${post.views} views",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScreamTextTertiary
                    )
                }

                Text(
                    text = formatRelativeTime(post.createdAt, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = ScreamTextTertiary
                )
            }

            // ── Body ────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(10.dp))
            if (post.body.isNotBlank()) {
                Text(
                    text = post.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ScreamTextPrimary,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Render Media attachments if present
            if (post.mediaBase64 != null) {
                when {
                    post.mediaMimeType?.startsWith("image/") == true -> {
                        val bitmap = remember(post.mediaBase64) {
                            runCatching {
                                val bytes = Base64.decode(post.mediaBase64, Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }.getOrNull()
                        }
                        if (bitmap != null) {
                            var showImageZoom by remember { mutableStateOf(false) }
                            ComposeImage(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Shared photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showImageZoom = true },
                                contentScale = ContentScale.Crop
                            )
                            if (showImageZoom) {
                                Dialog(onDismissRequest = { showImageZoom = false }) {
                                    Surface(shape = RoundedCornerShape(16.dp), color = ScreamBlack) {
                                        Box(contentAlignment = Alignment.Center) {
                                            ComposeImage(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Zoomed photo",
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                    post.mediaMimeType?.startsWith("video/") == true -> {
                        // Styled video card preview
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = ScreamSurfaceTop
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlayCircle, "Play Video", tint = ScreamBlue, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("P2P Video Attachment", style = MaterialTheme.typography.bodySmall, color = ScreamTextSecondary)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    post.mediaMimeType?.startsWith("audio/") == true -> {
                        // Audio message bubble playback
                        VoicePlaybackContent(audioBase64 = post.mediaBase64, durationMs = post.audioDurationMs)
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // ── Action Row ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like count clickable to show popup
                PostActionButton(
                    icon = if (post.isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    count = post.likes,
                    isActive = post.isLiked,
                    activeColor = ScreamBlue,
                    onClick = onLike,
                    onCountClick = { if (post.likes > 0) showLikesPopup = true }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Dislike
                PostActionButton(
                    icon = if (post.isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    count = post.dislikes,
                    isActive = post.isDisliked,
                    activeColor = ErrorRed,
                    onClick = onDislike
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Reshare
                PostActionButton(
                    icon = Icons.Default.Repeat,
                    count = post.reshares,
                    isActive = post.isReshared,
                    activeColor = SuccessGreen,
                    onClick = onReshare
                )

                Spacer(modifier = Modifier.weight(1f))

                // More options menu (Remove from My Feed / Delete Post)
                var showPostMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { showPostMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Post Options",
                            tint = ScreamTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showPostMenu,
                        onDismissRequest = { showPostMenu = false },
                        modifier = Modifier.background(ScreamSurfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Remove from My Feed", color = ScreamTextPrimary) },
                            onClick = {
                                showPostMenu = false
                                onHideFromFeed()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = ScreamTextSecondary, modifier = Modifier.size(18.dp))
                            }
                        )

                        if (isOwn) {
                            DropdownMenuItem(
                                text = { Text("Delete Post", color = ErrorRed) },
                                onClick = {
                                    showPostMenu = false
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Popup listing users who liked the post
    if (showLikesPopup) {
        Dialog(onDismissRequest = { showLikesPopup = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ScreamSurfaceVariant,
                modifier = Modifier.fillMaxWidth().height(240.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Liked by", style = MaterialTheme.typography.titleMedium, color = ScreamWhite, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showLikesPopup = false }) {
                            Icon(Icons.Default.Close, null, tint = ScreamTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(post.likedBy) { alias ->
                            Surface(
                                color = ScreamSurfaceTop,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = alias,
                                    color = ScreamTextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    isActive: Boolean,
    activeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onCountClick: (() -> Unit)? = null
) {
    val tint = if (isActive) activeColor else ScreamTextTertiary

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { onCountClick?.invoke() }
            )
        }
    }
}

@Composable
fun VoicePlaybackContent(audioBase64: String, durationMs: Long) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var playProgress by remember { mutableStateOf(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val totalSteps = 80
            val stepDelay = (durationMs / totalSteps).coerceAtLeast(1L)
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
        modifier = Modifier
            .fillMaxWidth()
            .background(ScreamSurfaceTop, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = ScreamBlue.copy(alpha = 0.15f),
            onClick = {
                isPlaying = !isPlaying
                if (isPlaying) {
                    runCatching {
                        val file = File.createTempFile("scream_feed_play_", ".m4a", context.cacheDir)
                        file.writeBytes(Base64.decode(audioBase64, Base64.NO_WRAP))
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
                }
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Playback",
                    modifier = Modifier.size(18.dp),
                    tint = ScreamBlue
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Canvas(
            modifier = Modifier
                .width(120.dp)
                .height(20.dp)
        ) {
            val w = size.width
            val h = size.height
            val numBars = 20
            val barW = 2.5.dp.toPx()
            val step = w / numBars
            val heights = listOf(0.4f, 0.6f, 0.3f, 0.8f, 0.9f, 0.5f, 0.7f, 0.4f, 0.8f, 0.6f, 0.5f, 0.7f, 0.3f, 0.8f, 0.6f, 0.5f, 0.4f, 0.7f, 0.5f, 0.6f)

            for (i in 0 until numBars) {
                val barH = heights[i] * h
                val fraction = i.toFloat() / numBars
                val active = fraction <= playProgress

                drawRoundRect(
                    color = if (active) ScreamBlue else ScreamTextTertiary,
                    topLeft = Offset(i * step + (step - barW) / 2, (h - barH) / 2),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(1.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = ScreamTextPrimary
        )
    }
}

private fun startVoiceRecorder(context: Context): Pair<MediaRecorder, File> {
    val file = File.createTempFile("scream_feed_voice_", ".m4a", context.cacheDir)
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

fun formatRelativeTime(createdAt: Long, now: Long): String {
    val diff = (now - createdAt).coerceAtLeast(0L)
    val seconds = diff / 1000L
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}

