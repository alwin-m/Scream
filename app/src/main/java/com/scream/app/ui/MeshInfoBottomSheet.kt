package com.scream.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.MeshStats
import com.scream.app.model.NetworkStatus
import com.scream.app.model.PeerConnectionType
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.model.User
import com.scream.app.ui.components.AvatarView
import com.scream.app.ui.theme.*
import com.scream.app.model.VersionTrust
import com.scream.app.security.BuildIntegrity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshInfoBottomSheet(
    meshStats: MeshStats,
    connectedPeers: List<ConnectedPeer>,
    onDismiss: () -> Unit,
    onPeerClick: (User) -> Unit
) {
    var selectedNodeForDetail by remember { mutableStateOf<ConnectedPeer?>(null) }
    var isListExpanded by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ScreamSurface,
        contentColor = ScreamWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ScreamOutline)
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mesh Info",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = ScreamWhite
                        )
                        Text(
                            text = "Offline local network diagnostics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ScreamTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = ScreamTextSecondary
                        )
                    }
                }
            }

            // Top Status Block
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    color = ScreamSurfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ScreamSurfaceTop),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                tint = ScreamBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "My Mesh ID",
                                style = MaterialTheme.typography.labelSmall,
                                color = ScreamTextTertiary
                            )
                            Text(
                                text = meshStats.meshId,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ScreamWhite
                            )
                        }
                        NetworkStatusBadge(status = meshStats.networkStatus)
                    }
                }
            }

            // Nearby Share App / Files Sideload Export Card
            item {
                val context = LocalContext.current
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    color = ScreamSurfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ScreamGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Nearby",
                                tint = ScreamGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share SCREAM App Nearby",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ScreamWhite
                            )
                            Text(
                                text = "Send APK via Bluetooth or Nearby Share to phones without SCREAM installed.",
                                style = MaterialTheme.typography.labelSmall,
                                color = ScreamTextTertiary
                            )
                        }
                        Button(
                            onClick = { com.scream.app.utils.ShareUtils.shareScreamApk(context) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ScreamGreen,
                                contentColor = ScreamBlack
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Share", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Network Tree Graph Animation (Dynamic to actual peers)
            item {
                Text(
                    text = "Topology Map",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                MeshVisualizationGraph(
                    peers = connectedPeers,
                    onNodeClick = { peer -> selectedNodeForDetail = peer }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Mesh Information Card
            item {
                Text(
                    text = "Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                MeshDiagnosticsGrid(meshStats = meshStats, totalConnected = connectedPeers.size)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Expandable Device List Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isListExpanded = !isListExpanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Nodes in Range",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ScreamWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = ScreamSurfaceVariant,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "${connectedPeers.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ScreamBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Icon(
                        if (isListExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ScreamTextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Expandable Device List Items
            if (isListExpanded) {
                if (connectedPeers.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = ScreamSurfaceVariant,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Searching for nearby mesh participants...",
                                style = MaterialTheme.typography.bodySmall,
                                color = ScreamTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(connectedPeers, key = { it.user.id }) { peer ->
                        ExpandedPeerListItem(peer = peer, onClick = { selectedNodeForDetail = peer })
                    }
                }
            }

            // Info Footer text
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Encryption transport keys are generated locally. Zero central dependencies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ScreamTextTertiary,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Detailed Node Dialog when tapped
    selectedNodeForDetail?.let { peer ->
        NodeDetailOverlay(
            peer = peer,
            onDismiss = { selectedNodeForDetail = null },
            onMessageClick = {
                selectedNodeForDetail = null
                onDismiss()
                onPeerClick(peer.user)
            }
        )
    }
}

@Composable
fun MeshVisualizationGraph(
    peers: List<ConnectedPeer>,
    onNodeClick: (ConnectedPeer) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_graph")
    
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_ring"
    )

    val flowProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow_particles"
    )

    val outlineColor = ScreamOutline
    val connectionCount = peers.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ScreamSurfaceVariant,
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Center node ("You")
                val rootX = w / 2f
                val rootY = h / 2f

                if (connectionCount > 0) {
                    val radius = 64.dp.toPx()
                    for (i in 0 until connectionCount) {
                        val angle = i * (2 * Math.PI / connectionCount)
                        val peerX = rootX + radius * cos(angle).toFloat()
                        val peerY = rootY + radius * sin(angle).toFloat()

                        // Draw connection link line
                        drawLine(
                            color = outlineColor,
                            start = Offset(rootX, rootY),
                            end = Offset(peerX, peerY),
                            strokeWidth = 1.5.dp.toPx()
                        )

                        // Flowing pulse particle along connection path
                        val px = rootX + (peerX - rootX) * flowProgress
                        val py = rootY + (peerY - rootY) * flowProgress
                        drawCircle(
                            color = ScreamBlue,
                            radius = 3.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Draw central node "You"
            NodeIcon(
                modifier = Modifier,
                avatar = "🐙",
                name = "You",
                color = ScreamBlue,
                pulseScale = pulseSize,
                onClick = {}
            )

            // Draw peer icons dynamically spaced on circle
            if (connectionCount > 0) {
                // Layout nodes around center
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    peers.forEachIndexed { i, peer ->
                        val angle = i * (2 * Math.PI / connectionCount)
                        val radiusDp = 64.dp
                        
                        // We use box alignment offsets
                        val offsetX = radiusDp * cos(angle).toFloat()
                        val offsetY = radiusDp * sin(angle).toFloat()

                        NodeIcon(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = offsetX, y = offsetY),
                            avatar = peer.user.avatar,
                            name = peer.user.alias,
                            color = ScreamViolet,
                            pulseScale = 1.0f,
                            onClick = { onNodeClick(peer) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NodeIcon(
    modifier: Modifier = Modifier,
    avatar: String,
    name: String,
    color: Color,
    pulseScale: Float,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pulseScale > 1.0f) {
                Box(
                    modifier = Modifier
                        .size(42.dp * pulseScale)
                        .clip(CircleShape)
                        .background(color.copy(alpha = (2f - pulseScale).coerceIn(0f, 0.15f)))
                )
            }
            AvatarView(avatarStr = avatar, size = 34.dp, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = ScreamWhite,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 50.dp)
        )
    }
}

@Composable
fun MeshDiagnosticsGrid(meshStats: MeshStats, totalConnected: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagnosticsCard(
                modifier = Modifier.weight(1f),
                label = "Network Status",
                value = meshStats.networkStatus.label,
                icon = Icons.Default.Bluetooth,
                tint = ScreamBlue
            )
            DiagnosticsCard(
                modifier = Modifier.weight(1f),
                label = "Nodes in Range",
                value = "$totalConnected in range",
                icon = Icons.Default.Devices,
                tint = ScreamViolet
            )
        }
    }
}

@Composable
fun DiagnosticsCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color
) {
    Surface(
        modifier = modifier,
        color = ScreamSurfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ScreamTextTertiary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite
                )
            }
        }
    }
}

@Composable
fun ExpandedPeerListItem(
    peer: ConnectedPeer,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val qualityColor = when (peer.quality) {
        ConnectionQuality.EXCELLENT -> SuccessGreen
        ConnectionQuality.GOOD -> ScreamBlue
        ConnectionQuality.WEAK -> WarningAmber
        ConnectionQuality.DISCONNECTED -> StatusOffline
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() },
        color = ScreamSurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(avatarStr = peer.user.avatar, size = 40.dp, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = peer.user.alias,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ScreamWhite
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val isBitChat = peer.protocol == ProtocolType.BITCHAT
                    val isNostr = peer.transport == PeerTransport.NOSTR
                    val badgeTag = when {
                        isNostr -> "🟣 BITCHAT NOSTR"
                        isBitChat -> "🔵 BITCHAT BLE"
                        else -> "🟢 SCREAM NATIVE"
                    }
                    val badgeColor = when {
                        isNostr -> ScreamViolet
                        isBitChat -> ScreamViolet
                        else -> ScreamGreen
                    }

                    Surface(
                        color = badgeColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = badgeTag,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            fontSize = 8.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (peer.isRelayEnabled) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = ScreamBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "RELAY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ScreamBlue,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    // Version trust badge
                    run {
                        val trust = BuildIntegrity.verifyPeerFingerprint(peer.appFingerprint, context)
                        val trustColor = when (trust) {
                            VersionTrust.OFFICIAL -> SuccessGreen
                            VersionTrust.UNVERIFIED -> WarningAmber
                            VersionTrust.MODIFIED -> ErrorRed
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = trustColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${trust.badge} ${trust.label.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = trustColor,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${peer.transport.displayName} · Rssi ${peer.signalStrength}dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = ScreamTextTertiary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "🔋 ${peer.batteryLevel}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (peer.batteryLevel > 30) ScreamTextSecondary else ErrorRed
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(qualityColor)
                )
            }
        }
    }
}

@Composable
fun NodeDetailOverlay(
    peer: ConnectedPeer,
    onDismiss: () -> Unit,
    onMessageClick: () -> Unit
) {
    val distance = remember(peer.signalStrength) {
        val rssi = peer.signalStrength
        val exp = (-69 - rssi) / (10 * 2.2)
        val d = Math.pow(10.0, exp)
        String.format(Locale.US, "%.1f m", d.coerceIn(1.0, 30.0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ScreamSurfaceVariant,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AvatarView(avatarStr = peer.user.avatar, size = 72.dp, fontSize = 36.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = peer.user.alias,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite
                )
                Text(
                    text = peer.user.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = ScreamTextTertiary
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Distance",
                        value = distance
                    )
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Connection",
                        value = peer.quality.displayName
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Battery",
                        value = "${peer.batteryLevel}%"
                    )
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Relay Status",
                        value = if (peer.isRelayEnabled) "Enabled" else "Disabled"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Operating System",
                        value = peer.osVersion
                    )
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Protocol Interop",
                        value = peer.protocol.displayName
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Hop Path Trace",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ScreamSurfaceTop, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "You", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ScreamWhite)
                    Icon(
                        Icons.Default.ArrowRightAlt,
                        contentDescription = null,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        tint = ScreamBlue
                    )
                    Text(text = peer.user.alias, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ScreamBlue)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Connection Log",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val sdf = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    TimelineRow(time = sdf.format(Date(peer.lastSeen - 4000)), text = "Handshake verification completed")
                    TimelineRow(time = sdf.format(Date(peer.lastSeen)), text = "Active mesh synchronization active")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onMessageClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScreamBlue,
                    contentColor = ScreamWhite
                )
            ) {
                Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Chat", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss", color = ScreamTextSecondary)
            }
        }
    )
}

@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        color = ScreamSurfaceTop,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ScreamWhite)
        }
    }
}

@Composable
fun TimelineRow(time: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = ScreamBlue,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(60.dp)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ScreamTextTertiary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = ScreamTextSecondary
        )
    }
}

@Composable
fun NetworkStatusBadge(status: NetworkStatus) {
    val bgColor by animateColorAsState(
        targetValue = when (status) {
            NetworkStatus.ACTIVE -> SuccessGreen.copy(alpha = 0.15f)
            NetworkStatus.LIMITED -> WarningAmber.copy(alpha = 0.15f)
            NetworkStatus.OFFLINE -> StatusOffline.copy(alpha = 0.15f)
        },
        label = "badge_bg"
    )
    val textColor by animateColorAsState(
        targetValue = when (status) {
            NetworkStatus.ACTIVE -> SuccessGreen
            NetworkStatus.LIMITED -> WarningAmber
            NetworkStatus.OFFLINE -> StatusOffline
        },
        label = "badge_text"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color = textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}
