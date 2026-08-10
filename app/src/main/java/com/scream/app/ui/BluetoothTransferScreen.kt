package com.scream.app.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.network.MeshNetworkManager
import com.scream.app.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothTransferScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isBluetoothEnabled by remember { mutableStateOf(checkBluetoothEnabled(context)) }
    var discoveredList by remember { mutableStateOf(emptyList<MeshNetworkManager.DiscoveredDevice>()) }

    // Periodically query the real scanner state
    LaunchedEffect(isBluetoothEnabled) {
        while (isBluetoothEnabled) {
            isBluetoothEnabled = checkBluetoothEnabled(context)
            discoveredList = MeshNetworkManager.getDiscoveredDevices()
            delay(1500L)
        }
    }

    // Pulse animation for scanning radar
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Scaffold(
        containerColor = ScreamBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Bluetooth Discovery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ScreamWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ScreamWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ScreamSurface,
                    titleContentColor = ScreamWhite
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ScreamBlack)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Radar Ring Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isBluetoothEnabled) {
                    Box(
                        modifier = Modifier
                            .size((130 * pulseScale).dp)
                            .clip(CircleShape)
                            .background(ScreamBlue.copy(alpha = pulseAlpha))
                    )
                    Box(
                        modifier = Modifier
                            .size((90 * pulseScale).dp)
                            .clip(CircleShape)
                            .background(ScreamViolet.copy(alpha = pulseAlpha * 0.8f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ScreamBlue, ScreamViolet)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isBluetoothEnabled) Icons.Default.BluetoothSearching else Icons.Default.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = ScreamWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (!isBluetoothEnabled) "Bluetooth is Off" else "Scanning Offline Mesh...",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (!isBluetoothEnabled)
                        "Turn on Bluetooth to search for nearby SCREAM users automatically."
                    else
                        "Devices running SCREAM will connect automatically. Make sure Bluetooth is enabled on both devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScreamTextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Action or List Area
            if (!isBluetoothEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ScreamBlue,
                            contentColor = ScreamWhite
                        )
                    ) {
                        Text("Enable Bluetooth", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Text(
                    text = "Discovered Devices (${discoveredList.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScreamTextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                )

                if (discoveredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Devices, null, tint = ScreamTextTertiary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No devices in range yet", style = MaterialTheme.typography.bodySmall, color = ScreamTextTertiary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredList) { dev ->
                            val isScream = dev.isScreamDevice
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ScreamSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(if (isScream) ScreamBlue.copy(alpha = 0.15f) else ScreamSurfaceTop),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            if (isScream) Icons.Default.CellTower else Icons.Default.Bluetooth,
                                            contentDescription = null,
                                            tint = if (isScream) ScreamBlue else ScreamTextTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dev.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ScreamWhite,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = dev.address,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ScreamTextTertiary
                                        )
                                    }

                                    if (isScream) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ScreamBlue.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "SCREAM",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ScreamBlue,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            "${dev.rssi} dBm",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ScreamTextTertiary
                                        )
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

private fun checkBluetoothEnabled(context: Context): Boolean {
    return try {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter?.isEnabled == true
    } catch (e: Exception) {
        false
    }
}
