package com.scream.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scream.app.data.ScreamRepository
import com.scream.app.identity.dataStore
import com.scream.app.identity.UserPreferencesRepository
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.User
import com.scream.app.ui.components.AvatarView
import com.scream.app.ui.components.MeshTopologyLegend
import com.scream.app.ui.components.MeshTopologyMap
import com.scream.app.ui.components.QrIdentityDialog
import com.scream.app.ui.components.ScreamQrIdentity
import kotlinx.coroutines.launch
import com.scream.app.ui.theme.*
import java.io.ByteArrayOutputStream

@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onOpenBluetoothTransfer: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onStartPrivateChat: (User) -> Unit = {}
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val posts by viewModel.posts.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()
    val activePeers by viewModel.activePeers.collectAsState()

    val myPosts = posts.filter { it.user.id == currentUser?.id }
    val myLikes = myPosts.sumOf { it.likes }
    val myReshares = myPosts.sumOf { it.reshares }

    val meshId = remember { ScreamRepository.getMeshId() }

    var showEditDialog by remember { mutableStateOf(false) }
    var showPostsDialog by remember { mutableStateOf(false) }
    var showLikesDialog by remember { mutableStateOf(false) }
    var showResharesDialog by remember { mutableStateOf(false) }
    var selectedPeerForDialog by remember { mutableStateOf<ConnectedPeer?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScopeOuter = rememberCoroutineScope()

    val btStatusLabel = when (networkStatus) {
        com.scream.app.model.NetworkStatus.ACTIVE -> "Connected via Bluetooth"
        com.scream.app.model.NetworkStatus.LIMITED -> "Discovering nearby devices…"
        com.scream.app.model.NetworkStatus.OFFLINE -> "Bluetooth Offline"
    }
    val btStatusColor = when (networkStatus) {
        com.scream.app.model.NetworkStatus.ACTIVE -> StatusConnected
        com.scream.app.model.NetworkStatus.LIMITED -> WarningAmber
        com.scream.app.model.NetworkStatus.OFFLINE -> StatusOffline
    }

    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreamBlack)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp)
    ) {
    item {
        // Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = ScreamGradient,
                    shape = CircleShape
                )
        ) {
            AvatarView(
                avatarStr = currentUser?.avatar.orEmpty(),
                profileImage = currentUser?.profileImage,
                size = 100.dp,
                fontSize = 48.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Alias
        Text(
            text = currentUser?.alias ?: "Anonymous",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ScreamTextPrimary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = currentUser?.id ?: "#0000",
            style = MaterialTheme.typography.bodyMedium,
            color = ScreamTextTertiary
        )
        
        com.scream.app.identity.SecurityUtils.getPublicKeyFingerprint(currentUser?.publicKey)?.let { fp ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(color = ScreamSurfaceVariant, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = "🔑 $fp",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = ScreamGreen,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Edit Profile Button
        Button(
            onClick = { showEditDialog = true },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ScreamSurfaceVariant,
                contentColor = ScreamTextPrimary
            )
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Profile", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Edit My Profile", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Stats Row
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ScreamSurfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    number = "${myPosts.size}",
                    label = "Posts",
                    onClick = { showPostsDialog = true }
                )
                StatItem(
                    number = "$myLikes",
                    label = "Likes",
                    onClick = { showLikesDialog = true }
                )
                StatItem(
                    number = "$myReshares",
                    label = "Reshares",
                    onClick = { showResharesDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    } // end item: header section

    // ── Security Identity Card ────────────────────────────────────────────
    item {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF070E1F),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = ScreamBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Security Identity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ScreamWhite
                    )
                }
                Spacer(Modifier.height(12.dp))

                // SCREAM Mesh ID
                IdentityRow(
                    label = "Mesh ID",
                    value = meshId,
                    context = context
                )
                Spacer(Modifier.height(8.dp))

                // User short ID
                IdentityRow(
                    label = "User ID",
                    value = currentUser?.id ?: "#0000",
                    context = context
                )
                Spacer(Modifier.height(8.dp))

                // Hardware Device Model (GrapheneOS style)
                val deviceProfile = ScreamRepository.getDeviceHardwareProfile()
                IdentityRow(
                    label = "Hardware Model",
                    value = "${deviceProfile.deviceName} (${deviceProfile.androidVersion})",
                    context = context
                )

                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = ScreamBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "AES-256-GCM",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ScreamBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        color = Color(0xFF00C896).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "E2E Ready",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00C896),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        color = ScreamGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        val fp = com.scream.app.security.BuildIntegrity.getFingerprint(context)
                        Text(
                            "✅ Official (${fp.contributorTag})",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ScreamGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // ── Mesh Topology Map ─────────────────────────────────────────────────
    item {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            color = Color(0xFF050C1A),
            shape = RoundedCornerShape(18.dp)
        ) {
            Box {
                MeshTopologyMap(
                    currentUser = currentUser,
                    peers = activePeers,
                    modifier = Modifier.fillMaxSize(),
                    onPeerTap = { peer -> selectedPeerForDialog = peer }
                )

                // Overlay header
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = Color(0xFF00D4FF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Live Mesh Network",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00D4FF)
                        )
                    }
                    Text(
                        "${activePeers.size} peer${if (activePeers.size == 1) "" else "s"} visible · local radio field",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF00D4FF).copy(alpha = 0.6f)
                    )
                }

                // Legend overlay
                MeshTopologyLegend(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )

                // Network status badge top-right
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    color = btStatusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(btStatusColor)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            btStatusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = btStatusColor,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                    color = Color.Black.copy(alpha = 0.48f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Signal view · no GPS tracking",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }

    item { Spacer(Modifier.height(12.dp)) }

    item {
        // Bluetooth Status Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = btStatusColor.copy(alpha = 0.08f),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = btStatusColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = btStatusLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = btStatusColor
                    )
                    Text(
                        text = "Secure offline mesh network",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScreamTextTertiary
                    )
                }
            }
        }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }

    // Settings Center Trigger
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenBluetoothTransfer,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScreamSurfaceVariant,
                    contentColor = ScreamTextPrimary
                )
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bluetooth", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScreamBlue,
                    contentColor = ScreamWhite
                )
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            }
        }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }

    item {
        Button(
            onClick = { showQrDialog = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF182B55),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text("Share or scan QR identity", fontWeight = FontWeight.SemiBold)
                Text("Connect without typing a username", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.68f))
            }
        }
    }

    item { Spacer(modifier = Modifier.height(16.dp)) }

    item {
        // Background Activity / Deep Sleep Card
        val userPrefsRepo = remember { UserPreferencesRepository(context.dataStore) }
        val userProfile by userPrefsRepo.userProfileFlow.collectAsState(initial = null)
        val currentBgMode = userProfile?.backgroundMode ?: com.scream.app.model.BackgroundMode.ACTIVE
        val coroutineScope = rememberCoroutineScope()

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ScreamSurfaceVariant,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Background Activity",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ScreamWhite
                    )
                    if (currentBgMode == com.scream.app.model.BackgroundMode.DEEP_SLEEP) {
                        Surface(
                            color = WarningAmber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "💤 DEEP SLEEP ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Controls background networking & battery consumption. Deep Sleep hides the app from network scans.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ScreamTextTertiary
                )

                Spacer(modifier = Modifier.height(12.dp))

                com.scream.app.model.BackgroundMode.values().forEach { mode ->
                    val isSelected = currentBgMode == mode
                    val selectedColor = when (mode) {
                        com.scream.app.model.BackgroundMode.ACTIVE -> ScreamGreen
                        com.scream.app.model.BackgroundMode.DEEP_SLEEP -> WarningAmber
                        com.scream.app.model.BackgroundMode.DISABLED -> StatusOffline
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                coroutineScope.launch {
                                    userPrefsRepo.setBackgroundMode(mode)
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    userPrefsRepo.setBackgroundMode(mode)
                                }
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = selectedColor,
                                unselectedColor = ScreamTextTertiary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ScreamWhite else ScreamTextSecondary
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = ScreamTextTertiary
                            )
                        }
                    }
                }
            }
        } // end Surface
    } // end item: background activity

    item {
        // Optional stats metadata details
        val metaList = mutableListOf<String>()
        if (!currentUser?.age.isNullOrEmpty()) metaList.add("Age: ${currentUser?.age}")
        if (!currentUser?.gender.isNullOrEmpty()) metaList.add("Gender: ${currentUser?.gender}")

        if (metaList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = metaList.joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = ScreamTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy footer
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = ScreamTextTertiary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "No servers · Data stays on your device",
                style = MaterialTheme.typography.labelSmall,
                color = ScreamTextTertiary,
                textAlign = TextAlign.Center
            )
        }
    } // end item: footer
    } // end LazyColumn

    // Snackbar host
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    )
    } // end Box

    // ── Edit Profile Dialog ──────────────────────────────────────────────────
    if (showEditDialog) {
        var tempAlias by remember { mutableStateOf(currentUser?.alias.orEmpty()) }
        var tempAge by remember { mutableStateOf(currentUser?.age.orEmpty()) }
        var tempGender by remember { mutableStateOf(currentUser?.gender.orEmpty()) }
        var tempEmojiAvatar by remember { mutableStateOf(currentUser?.avatar.orEmpty()) }
        var tempProfileImage by remember { mutableStateOf(currentUser?.profileImage) }

        val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        val bytes = out.toByteArray()
                        tempProfileImage = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = ScreamSurfaceVariant,
            title = { Text("Edit Profile", color = ScreamWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { galleryLauncher.launch("image/*") }
                            .clip(CircleShape)
                            .border(2.dp, ScreamBlue, CircleShape)
                    ) {
                        AvatarView(avatarStr = tempProfileImage ?: tempEmojiAvatar, size = 72.dp, fontSize = 32.sp)
                    }
                    Text(
                        "Private chats use your photo; public chats use your emoji",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScreamTextTertiary
                    )

                    Text(
                        "Public avatar",
                        style = MaterialTheme.typography.labelMedium,
                        color = ScreamTextSecondary,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("😎", "😊", "🔥", "🌟", "🦊", "🐼", "🚀", "🎧").forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (tempEmojiAvatar == emoji) ScreamBlue.copy(alpha = 0.25f) else ScreamSurfaceTop)
                                    .clickable { tempEmojiAvatar = emoji },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 19.sp) }
                        }
                    }

                    OutlinedTextField(
                        value = tempAlias,
                        onValueChange = { tempAlias = it },
                        label = { Text("Alias", color = ScreamTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = ScreamOutline,
                            focusedBorderColor = ScreamBlue,
                            unfocusedContainerColor = ScreamSurfaceTop,
                            focusedContainerColor = ScreamSurfaceTop,
                            cursorColor = ScreamBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempAge,
                        onValueChange = { tempAge = it },
                        label = { Text("Age (Optional)", color = ScreamTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = ScreamOutline,
                            focusedBorderColor = ScreamBlue,
                            unfocusedContainerColor = ScreamSurfaceTop,
                            focusedContainerColor = ScreamSurfaceTop,
                            cursorColor = ScreamBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempGender,
                        onValueChange = { tempGender = it },
                        label = { Text("Gender (Optional)", color = ScreamTextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = ScreamOutline,
                            focusedBorderColor = ScreamBlue,
                            unfocusedContainerColor = ScreamSurfaceTop,
                            focusedContainerColor = ScreamSurfaceTop,
                            cursorColor = ScreamBlue
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateProfile(
                            alias = tempAlias.ifBlank { "Anonymous" },
                            age = tempAge,
                            gender = tempGender,
                            avatar = tempEmojiAvatar.ifBlank { "😎" },
                            profileImage = tempProfileImage
                        )
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ScreamBlue)
                ) {
                    Text("Save", color = ScreamWhite)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = ScreamTextSecondary)
                }
            }
        )
    }

    // ── My Posts Dialog ──────────────────────────────────────────────────────
    if (showPostsDialog) {
        Dialog(onDismissRequest = { showPostsDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                shape = RoundedCornerShape(16.dp),
                color = ScreamSurfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("My Posts", style = MaterialTheme.typography.titleMedium, color = ScreamWhite, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showPostsDialog = false }) {
                            Icon(Icons.Default.Close, null, tint = ScreamTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (myPosts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No posts created yet", color = ScreamTextTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(myPosts) { post ->
                                Surface(
                                    color = ScreamSurfaceTop,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(post.body, style = MaterialTheme.typography.bodyMedium, color = ScreamTextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("👍 ${post.likes} · 👁️ ${post.views}", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                                            Text(post.timestamp, style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
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

    // ── Likes Log Dialog ─────────────────────────────────────────────────────
    if (showLikesDialog) {
        val likesBreakdown = remember(myPosts) {
            myPosts.flatMap { it.likedBy }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
        }

        Dialog(onDismissRequest = { showLikesDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                shape = RoundedCornerShape(16.dp),
                color = ScreamSurfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Likes Log", style = MaterialTheme.typography.titleMedium, color = ScreamWhite, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showLikesDialog = false }) {
                            Icon(Icons.Default.Close, null, tint = ScreamTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (likesBreakdown.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No likes received yet", color = ScreamTextTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(likesBreakdown) { (alias, count) ->
                                Surface(
                                    color = ScreamSurfaceTop,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(alias, style = MaterialTheme.typography.bodyMedium, color = ScreamTextPrimary, fontWeight = FontWeight.SemiBold)
                                        Text("Liked your posts: $count times", style = MaterialTheme.typography.labelSmall, color = ScreamBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Reshares Dialog ──────────────────────────────────────────────────────
    if (showResharesDialog) {
        Dialog(onDismissRequest = { showResharesDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(16.dp),
                color = ScreamSurfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reshares Log", style = MaterialTheme.typography.titleMedium, color = ScreamWhite, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showResharesDialog = false }) {
                            Icon(Icons.Default.Close, null, tint = ScreamTextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔁", fontSize = 32.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Total Post Reshares: $myReshares", style = MaterialTheme.typography.bodyMedium, color = ScreamTextPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog && currentUser != null) {
        QrIdentityDialog(
            identity = ScreamQrIdentity(
                userId = currentUser!!.id,
                alias = currentUser!!.alias,
                avatar = currentUser!!.avatar,
                meshId = meshId
            ),
            onDismiss = { showQrDialog = false }
        )
    }

    // ── Peer tap dialog (from topology map) ────────────────────────────────
    selectedPeerForDialog?.let { peer ->
        UserProfileDialog(
            user = peer.user,
            currentUser = currentUser,
            connectedPeer = peer,
            onDismiss = { selectedPeerForDialog = null },
            onStartPrivateChat = onStartPrivateChat
        )
    }
}

@Composable
fun StatItem(number: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = ScreamBlue
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ScreamTextTertiary
        )
    }
}

/**
 * Shows a labelled identity value row with a copy-to-clipboard icon button.
 */
@Composable
fun IdentityRow(
    label: String,
    value: String,
    context: Context
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ScreamTextTertiary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(3.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1A30), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = ScreamTextPrimary,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = ScreamTextTertiary,
                    modifier = Modifier.size(14.dp)
                )

            }
        }
    }
}
