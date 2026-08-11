package com.scream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.NetworkStatus

/**
 * Compact, Android-safe equivalent of a Dynamic Island status surface.
 * It remains a normal Compose layout on devices that do not expose a cutout or
 * live-notification API, and can be reused for downloads or voice activity.
 */
@Composable
fun MeshActivityIsland(
    networkStatus: NetworkStatus,
    peers: List<ConnectedPeer>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val active = networkStatus != NetworkStatus.OFFLINE
    val accent = when (networkStatus) {
        NetworkStatus.ACTIVE -> Color(0xFF00D084)
        NetworkStatus.LIMITED -> Color(0xFFFFB020)
        NetworkStatus.OFFLINE -> Color(0xFF8A93A6)
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color(0xFF101521),
        contentColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Hub, contentDescription = "Mesh activity", tint = accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            androidx.compose.foundation.layout.Column {
                Text(
                    text = if (active) "Mesh ${networkStatus.label.lowercase()}" else "Mesh offline",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = if (peers.isEmpty()) "Listening for nearby peers" else "${peers.size} nearby · tap for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
            }
            Spacer(Modifier.width(9.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(7.dp).clip(CircleShape).background(accent)
            )
        }
    }
}
