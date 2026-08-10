package com.scream.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.identity.UserPreferencesRepository
import com.scream.app.identity.dataStore
import com.scream.app.model.BackgroundMode
import com.scream.app.model.BatteryVisibility
import com.scream.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userPrefs = remember { UserPreferencesRepository(context.dataStore) }
    val profile by userPrefs.userProfileFlow.collectAsState(initial = null)

    val currentMode = profile?.backgroundMode ?: BackgroundMode.ACTIVE
    val batteryVis = profile?.batteryVisibility ?: BatteryVisibility.FRIENDS
    val isAutoDeepSleep = profile?.isAutoDeepSleepEnabled ?: true
    val threshold = profile?.autoDeepSleepThreshold ?: 20
    val isOffline = profile?.isPermanentOffline ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings Center", color = ScreamWhite, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ScreamWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ScreamSurface)
            )
        },
        containerColor = ScreamBlack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Section 1: Networking & Offline Mode ───────────────────────
            item {
                SettingsSectionHeader(title = "Networking & Discovery", icon = Icons.Default.Bluetooth)
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ScreamSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("SCREAM Networking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ScreamWhite)
                                Text("Disable all network discovery and P2P sockets", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                            }
                            Switch(
                                checked = !isOffline,
                                onCheckedChange = { active ->
                                    coroutineScope.launch {
                                        userPrefs.setPermanentOffline(!active)
                                    }
                                }
                            )
                        }

                        if (isOffline) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = StatusOffline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚠️ SCREAM is currently Offline. Network discovery is completely disabled.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningAmber,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = ScreamBorder, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Background Activity Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ScreamWhite)
                        Spacer(modifier = Modifier.height(8.dp))

                        BackgroundMode.values().forEach { mode ->
                            val isSel = currentMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        coroutineScope.launch { userPrefs.setBackgroundMode(mode) }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSel, onClick = {
                                    coroutineScope.launch { userPrefs.setBackgroundMode(mode) }
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(mode.title, fontWeight = FontWeight.SemiBold, color = if (isSel) ScreamWhite else ScreamTextSecondary)
                                    Text(mode.description, style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                                }
                            }
                        }
                    }
                }
            }

            // ── Section 2: Privacy Settings ─────────────────────────────────
            item {
                SettingsSectionHeader(title = "Privacy & Visibility", icon = Icons.Default.Lock)
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ScreamSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Battery Status Visibility", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ScreamWhite)
                        Text("Controls who can view your device battery status", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                        Spacer(modifier = Modifier.height(12.dp))

                        BatteryVisibility.values().forEach { vis ->
                            val isSel = batteryVis == vis
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        coroutineScope.launch { userPrefs.setBatteryVisibility(vis) }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSel, onClick = {
                                    coroutineScope.launch { userPrefs.setBatteryVisibility(vis) }
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(vis.title, style = MaterialTheme.typography.bodyMedium, color = if (isSel) ScreamWhite else ScreamTextSecondary)
                            }
                        }
                    }
                }
            }

            // ── Section 3: Battery Optimization Automation ──────────────────
            item {
                SettingsSectionHeader(title = "Battery Saving Automation", icon = Icons.Default.BatteryChargingFull)
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ScreamSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Deep Sleep at Low Battery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ScreamWhite)
                                Text("Automatically pause discovery when battery drops below threshold", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                            }
                            Switch(
                                checked = isAutoDeepSleep,
                                onCheckedChange = { enabled ->
                                    coroutineScope.launch {
                                        userPrefs.setAutoDeepSleep(enabled, threshold)
                                    }
                                }
                            )
                        }

                        if (isAutoDeepSleep) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Threshold: $threshold%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ScreamBlue)
                            Slider(
                                value = threshold.toFloat(),
                                onValueChange = { valInt ->
                                    coroutineScope.launch {
                                        userPrefs.setAutoDeepSleep(true, valInt.toInt())
                                    }
                                },
                                valueRange = 10f..30f,
                                steps = 3
                            )
                        }
                    }
                }
            }

            // ── Section 4: Security & Encryption ────────────────────────────
            item {
                SettingsSectionHeader(title = "Security & Encryption", icon = Icons.Default.Security)
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = ScreamSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = ScreamGreen, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("AES-256-GCM & Ed25519 Ready", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = ScreamWhite)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "SCREAM uses local shared keys for P2P mesh and hardware-backed cryptographic identity for BitChat interop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ScreamTextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = ScreamBlue, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ScreamWhite)
    }
}
