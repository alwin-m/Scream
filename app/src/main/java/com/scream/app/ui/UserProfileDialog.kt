package com.scream.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.ProtocolType
import com.scream.app.model.User
import com.scream.app.ui.components.AvatarView
import com.scream.app.ui.theme.*

@Composable
fun UserProfileDialog(
    user: User,
    connectedPeer: ConnectedPeer? = null,
    onDismiss: () -> Unit,
    onStartPrivateChat: (User) -> Unit
) {
    val quality = connectedPeer?.quality
    val transport = connectedPeer?.transport

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ScreamSurfaceVariant,
        titleContentColor = ScreamTextPrimary,
        textContentColor = ScreamTextSecondary,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                AvatarView(
                    avatarStr = user.avatar,
                    size = 80.dp,
                    fontSize = 42.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Name
                Text(
                    text = user.alias,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ScreamWhite
                )

                // ID
                Text(
                    text = user.id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScreamTextTertiary
                )

                if (connectedPeer != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    val isBitChat = connectedPeer.protocol == ProtocolType.BITCHAT
                    val badgeColor = if (isBitChat) ScreamViolet else ScreamGreen
                    val protocolTitle = if (isBitChat) "This user is connected through BitChat" else "Connected via SCREAM P2P"
                    val protocolTag = if (isBitChat) "🔵 BitChat Peer" else "🟢 SCREAM Native Peer"

                    // Connection status & protocol badge card
                    Surface(
                        color = ScreamSurfaceTop,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(badgeColor)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = protocolTag,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ScreamWhite
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = protocolTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = ScreamTextSecondary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            HorizontalDivider(color = ScreamBorder, thickness = 0.5.dp)

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Active Transport", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                                    Text(
                                        text = transport?.displayName ?: "BLE Mesh",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ScreamWhite
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Security", style = MaterialTheme.typography.labelSmall, color = ScreamTextTertiary)
                                    Text(
                                        text = "🔐 ${connectedPeer.encryptionStatus.displayName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ScreamGreen
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Offline Mesh Participant",
                        style = MaterialTheme.typography.bodySmall,
                        color = ScreamTextTertiary
                    )
                }

                val details = mutableListOf<String>()
                if (user.age.isNotEmpty()) details.add("Age: ${user.age}")
                if (user.gender.isNotEmpty()) details.add("Gender: ${user.gender}")

                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = details.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScreamTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStartPrivateChat(user) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScreamBlue,
                    contentColor = ScreamWhite
                )
            ) {
                Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Private Chat", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = ScreamTextSecondary)
            }
        }
    )
}
