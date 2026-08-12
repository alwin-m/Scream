package com.scream.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.Room
import com.scream.app.model.User
import com.scream.app.ui.theme.*
import com.scream.app.ui.components.MeshActivityIsland

enum class HomeTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FEED("Feed", Icons.Default.Campaign, Icons.Outlined.Campaign),
    ROOMS("Rooms", Icons.Default.ChatBubble, Icons.Outlined.ChatBubble),
    PRIVATE("Private", Icons.Default.Lock, Icons.Outlined.Lock),
    PROFILE("Profile", Icons.Default.Person, Icons.Outlined.Person)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenRoom: (Room) -> Unit,
    onOpenBluetoothTransfer: () -> Unit
) {
    // Keep the originating room/private tab selected when returning from chat.
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.FEED) }
    var selectedUserForDialog by remember { mutableStateOf<User?>(null) }
    var selectedPeerForDialog by remember { mutableStateOf<ConnectedPeer?>(null) }
    var showMeshInfoSheet by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    val activePeers by viewModel.activePeers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val meshStats by viewModel.meshStats.collectAsState()
    val networkStatus by viewModel.networkStatus.collectAsState()

    LaunchedEffect(Unit) {
        com.scream.app.network.MeshNetworkManager.setFastDiscoveryMode(true)
        kotlinx.coroutines.delay(12_000L)
        com.scream.app.network.MeshNetworkManager.setFastDiscoveryMode(false)
    }

    Scaffold(
        containerColor = ScreamBlack,
        topBar = {
            Surface(
                color = ScreamSurface,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // SCREAM brand
                    Text(
                        text = "SCREAM",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = ScreamWhite,
                        letterSpacing = 2.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    MeshActivityIsland(
                        networkStatus = networkStatus,
                        peers = activePeers,
                        onClick = { showMeshInfoSheet = true }
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = ScreamSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(64.dp)
            ) {
                HomeTab.values().forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ScreamBlue,
                            selectedTextColor = ScreamBlue,
                            unselectedIconColor = ScreamTextTertiary,
                            unselectedTextColor = ScreamTextTertiary,
                            indicatorColor = ScreamBlue.copy(alpha = 0.1f)
                        )
                    )
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
            // Crossfade between tabs
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(250),
                label = "tab_crossfade"
            ) { tab ->
                when (tab) {
                    HomeTab.FEED -> FeedScreen(
                        viewModel = viewModel,
                        onUserClick = { user -> selectedUserForDialog = user; selectedPeerForDialog = null }
                    )
                    HomeTab.ROOMS -> RoomsScreen(
                        viewModel = viewModel,
                        onRoomClick = onOpenRoom
                    )
                    HomeTab.PRIVATE -> RoomsList(
                        rooms = rooms.filter { it.isPrivate },
                        emptyText = "No private messages yet",
                        currentUserId = currentUser?.id,
                        onRoomClick = onOpenRoom,
                        onDeleteRoom = { viewModel.deleteRoom(it.id) }
                    )
                    HomeTab.PROFILE -> ProfileScreen(
                        viewModel = viewModel,
                        onOpenBluetoothTransfer = onOpenBluetoothTransfer,
                        onOpenSettings = { showSettingsScreen = true },
                        onStartPrivateChat = { peer -> onOpenRoom(viewModel.getOrCreatePrivateRoom(peer)) }
                    )
                }
            }
        }
    }

    if (showSettingsScreen) {
        Dialog(
            onDismissRequest = { showSettingsScreen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            SettingsScreen(onBack = { showSettingsScreen = false })
        }
    }

    if (showMeshInfoSheet) {
        MeshInfoBottomSheet(
            meshStats = meshStats,
            connectedPeers = activePeers,
            onDismiss = { showMeshInfoSheet = false },
            onPeerClick = { peer ->
                showMeshInfoSheet = false
                selectedUserForDialog = peer
                selectedPeerForDialog = activePeers.find { it.user.id == peer.id }
            }
        )
    }

    selectedUserForDialog?.let { targetUser ->
        UserProfileDialog(
            user = targetUser,
            currentUser = currentUser,
            connectedPeer = selectedPeerForDialog,
            onDismiss = { selectedUserForDialog = null; selectedPeerForDialog = null },
            onStartPrivateChat = { peer ->
                selectedUserForDialog = null
                selectedPeerForDialog = null
                val privateRoom = viewModel.getOrCreatePrivateRoom(peer)
                onOpenRoom(privateRoom)
            }
        )
    }
}
