package com.scream.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.model.Room
import com.scream.app.ui.components.AvatarView
import com.scream.app.ui.theme.*

@Composable
fun RoomsScreen(
    viewModel: MainViewModel,
    onRoomClick: (Room) -> Unit
) {
    val rooms by viewModel.rooms.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = ScreamBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ScreamBlue,
                contentColor = ScreamWhite,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Room")
            }
        }
    ) { padding ->
        val filteredRooms = remember(rooms, currentUser) {
            rooms.filter { room ->
                val isDirectChat = room.name.startsWith("Private:")
                if (isDirectChat) {
                    false
                } else if (!room.isPrivate) {
                    true
                } else {
                    val myId = currentUser?.id.orEmpty()
                    room.adminId == myId || room.members.contains(myId)
                }
            }
        }

        RoomsList(
            rooms = filteredRooms,
            emptyText = "No rooms available",
            currentUserId = currentUser?.id,
            onRoomClick = onRoomClick,
            onDeleteRoom = { viewModel.deleteRoom(it.id) },
            modifier = Modifier.padding(padding)
        )
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, icon, isPrivate ->
                val newRoom = viewModel.createRoom(name, icon, isPrivate)
                showCreateDialog = false
                onRoomClick(newRoom)
            }
        )
    }
}

@Composable
fun RoomsList(
    rooms: List<Room>,
    emptyText: String,
    currentUserId: String?,
    onRoomClick: (Room) -> Unit,
    onDeleteRoom: (Room) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rooms.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ScreamBlack)
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "💬", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.titleMedium,
                    color = ScreamTextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Create a room to start mesh discussions",
                    style = MaterialTheme.typography.bodySmall,
                    color = ScreamTextTertiary
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ScreamBlack),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(rooms, key = { it.id }) { room ->
            RoomCard(
                room = room,
                canDelete = room.adminId.isNotBlank() && room.adminId == currentUserId,
                onClick = { onRoomClick(room) },
                onDelete = { onDeleteRoom(room) }
            )
        }
    }
}

@Composable
fun RoomCard(
    room: Room,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = ScreamBlack
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Room Icon Avatar
            AvatarView(
                avatarStr = room.icon,
                size = 52.dp,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Name + Preview message
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ScreamTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (room.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (room.isPrivate) ScreamViolet else ScreamBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (room.isPrivate) "Private" else "Public",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScreamTextTertiary,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = room.preview.ifEmpty { "No messages yet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = ScreamTextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Member count badge
            if (room.memberCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ScreamSurfaceVariant
                ) {
                    Text(
                        text = "${room.memberCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScreamTextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Delete button (visible if admin)
            if (canDelete) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete room",
                        tint = ScreamTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Divider
    Divider(
        color = ScreamDivider,
        modifier = Modifier.padding(start = 86.dp, end = 20.dp)
    )
}

@Composable
fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("💬") }
    var isPrivate by remember { mutableStateOf(false) }
    val iconOptions = listOf("💬", "🌐", "🔥", "💻", "🎓", "🌙", "🎮", "🎵")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ScreamSurfaceVariant,
        titleContentColor = ScreamTextPrimary,
        textContentColor = ScreamTextSecondary,
        title = {
            Text(
                "Create Room",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    placeholder = { Text("Room name", color = ScreamTextTertiary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = ScreamOutline,
                        focusedBorderColor = ScreamBlue,
                        unfocusedContainerColor = ScreamSurfaceTop.copy(alpha = 0.3f),
                        focusedContainerColor = ScreamSurfaceTop.copy(alpha = 0.5f),
                        cursorColor = ScreamBlue
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "Select Icon",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScreamTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    iconOptions.take(4).forEach { icon ->
                        val selected = selectedIcon == icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) ScreamBlue.copy(alpha = 0.15f)
                                    else ScreamSurfaceTop.copy(alpha = 0.5f)
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 20.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    iconOptions.drop(4).forEach { icon ->
                        val selected = selectedIcon == icon
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) ScreamBlue.copy(alpha = 0.15f)
                                    else ScreamSurfaceTop.copy(alpha = 0.5f)
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(icon, fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "Access Level",
                    style = MaterialTheme.typography.labelMedium,
                    color = ScreamTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isPrivate,
                        onClick = { isPrivate = false },
                        leadingIcon = {
                            Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Public") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScreamBlue.copy(alpha = 0.15f),
                            selectedLabelColor = ScreamBlue,
                            selectedLeadingIconColor = ScreamBlue
                        )
                    )
                    FilterChip(
                        selected = isPrivate,
                        onClick = { isPrivate = true },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("Private") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScreamViolet.copy(alpha = 0.15f),
                            selectedLabelColor = ScreamViolet,
                            selectedLeadingIconColor = ScreamViolet
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (roomName.isNotBlank()) onCreate(roomName, selectedIcon, isPrivate) },
                enabled = roomName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScreamBlue,
                    contentColor = ScreamWhite
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ScreamTextSecondary)
            }
        }
    )
}
