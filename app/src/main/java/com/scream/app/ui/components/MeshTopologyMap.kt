package com.scream.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.PeerConnectionType
import com.scream.app.model.PeerTransport
import com.scream.app.model.User
import com.scream.app.ui.theme.*
import kotlin.math.*

// ── Color palette for the topology map ──────────────────────────────────────
private val MapBackground   = Color(0xFF050C1A)
private val GridLine        = Color(0xFF0D1E3A)
private val NodeSelf        = Color(0xFF00D4FF)   // cyan — the local user
private val NodeBle         = Color(0xFF5B6AF5)   // indigo-blue — BLE peers
private val NodeLan         = Color(0xFF00C896)   // teal-green — LAN peers
private val NodeBitchat     = Color(0xFFB060FF)   // violet — BitChat peers
private val NodeUnknown     = Color(0xFF607090)   // muted slate
private val LineColorBle    = Color(0xFF3B4AD6)
private val LineColorLan    = Color(0xFF00A878)
private val LineColorBitchat= Color(0xFF9040E0)
private val LineColorDefault= Color(0xFF334455)

// ── Data structure for a placed node on the canvas ──────────────────────────
private data class NodePlacement(
    val peer: ConnectedPeer?,           // null → local user
    val label: String,
    val color: Color,
    var cx: Float = 0f,
    var cy: Float = 0f,
    val radiusPx: Float = 18f
)

/**
 * MeshTopologyMap
 *
 * A fully offline, zero-dependency animated Canvas composable that renders the
 * SCREAM peer mesh as a circuit-board style topology diagram.
 *
 * - Local user = pulsing cyan node at the centre.
 * - Each connected peer = glowing coloured satellite node.
 * - Peers are arranged in concentric rings based on connection type:
 *     inner ring  → DIRECT connections
 *     outer ring  → NEARBY_DISCOVERED / MESH_REACHABLE
 * - Connection lines are coloured by transport type (BLE / LAN / BitChat).
 * - Tapping a peer node fires [onPeerTap].
 */
@Composable
fun MeshTopologyMap(
    currentUser: User?,
    peers: List<ConnectedPeer>,
    modifier: Modifier = Modifier,
    onPeerTap: (ConnectedPeer) -> Unit = {}
) {
    // ── Animation: pulsing ring on local user node ───────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_anim")

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val lineAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "line_alpha"
    )

    // ── Compute node placements ──────────────────────────────────────────────
    // We track where nodes land so tap detection can work.
    val nodePlacements = remember(peers, currentUser) {
        buildNodePlacements(currentUser, peers)
    }

    Canvas(
        modifier = modifier
            .pointerInput(nodePlacements) {
                detectTapGestures { tapOffset ->
                    // Find the closest node to the tap
                    nodePlacements
                        .filter { it.peer != null }
                        .minByOrNull { node ->
                            val dx = node.cx - tapOffset.x
                            val dy = node.cy - tapOffset.y
                            sqrt(dx * dx + dy * dy)
                        }
                        ?.let { closest ->
                            val dx = closest.cx - tapOffset.x
                            val dy = closest.cy - tapOffset.y
                            val dist = sqrt(dx * dx + dy * dy)
                            if (dist <= closest.radiusPx * 2.5f && closest.peer != null) {
                                onPeerTap(closest.peer)
                            }
                        }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        // Assign positions using canvas size
        positionNodes(nodePlacements, cx, cy, size)

        // ── Background + grid ────────────────────────────────────────────────
        drawRect(color = MapBackground)
        drawGrid(this)

        // ── Concentric reference rings ───────────────────────────────────────
        drawConcentricRings(cx, cy)

        // ── Connection lines ─────────────────────────────────────────────────
        nodePlacements.forEach { node ->
            if (node.peer != null) {
                val lineColor = transportLineColor(node.peer.transport)
                drawLine(
                    color = lineColor.copy(alpha = lineAlpha),
                    start = Offset(cx, cy),
                    end = Offset(node.cx, node.cy),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )
                // Glow effect on the line
                drawLine(
                    color = lineColor.copy(alpha = lineAlpha * 0.3f),
                    start = Offset(cx, cy),
                    end = Offset(node.cx, node.cy),
                    strokeWidth = 6f
                )
            }
        }

        // ── Peer nodes ───────────────────────────────────────────────────────
        nodePlacements.forEach { node ->
            if (node.peer != null) {
                drawPeerNode(node, glowAlpha)
            }
        }

        // ── Local user node (drawn last = on top) ────────────────────────────
        drawSelfNode(cx, cy, pulse, glowAlpha, NodeSelf)
    }
}

// ── Node positioning helpers ─────────────────────────────────────────────────

private fun buildNodePlacements(
    currentUser: User?,
    peers: List<ConnectedPeer>
): List<NodePlacement> {
    val nodes = mutableListOf<NodePlacement>()
    // Self node (position assigned dynamically in positionNodes)
    nodes.add(
        NodePlacement(
            peer = null,
            label = currentUser?.alias?.take(10) ?: "Me",
            color = NodeSelf,
            radiusPx = 22f
        )
    )
    peers.forEach { peer ->
        nodes.add(
            NodePlacement(
                peer = peer,
                label = peer.user.alias.take(10),
                color = peerNodeColor(peer),
                radiusPx = if (peer.connectionType == PeerConnectionType.DIRECT) 18f else 14f
            )
        )
    }
    return nodes
}

private fun positionNodes(
    nodes: List<NodePlacement>,
    cx: Float,
    cy: Float,
    size: Size
) {
    // Self is always the centre
    nodes[0].cx = cx
    nodes[0].cy = cy

    val peerNodes = nodes.drop(1)
    if (peerNodes.isEmpty()) return

    val innerR = minOf(size.width, size.height) * 0.28f
    val outerR = minOf(size.width, size.height) * 0.42f

    val direct = peerNodes.filter { it.peer?.connectionType == PeerConnectionType.DIRECT }
    val nonDirect = peerNodes.filter { it.peer?.connectionType != PeerConnectionType.DIRECT }

    placeOnRing(direct, cx, cy, innerR)
    placeOnRing(nonDirect, cx, cy, outerR)
}

private fun placeOnRing(nodes: List<NodePlacement>, cx: Float, cy: Float, radius: Float) {
    if (nodes.isEmpty()) return
    val step = (2 * PI / nodes.size).toFloat()
    nodes.forEachIndexed { i, node ->
        val angle = step * i - PI.toFloat() / 2f   // start at top
        node.cx = cx + cos(angle) * radius
        node.cy = cy + sin(angle) * radius
    }
}

// ── Canvas draw helpers ──────────────────────────────────────────────────────

private fun drawGrid(scope: DrawScope) {
    val step = 40f
    var x = 0f
    while (x < scope.size.width) {
        scope.drawLine(GridLine, Offset(x, 0f), Offset(x, scope.size.height), strokeWidth = 0.5f)
        x += step
    }
    var y = 0f
    while (y < scope.size.height) {
        scope.drawLine(GridLine, Offset(0f, y), Offset(scope.size.width, y), strokeWidth = 0.5f)
        y += step
    }
}

private fun DrawScope.drawConcentricRings(cx: Float, cy: Float) {
    val radii = listOf(
        minOf(size.width, size.height) * 0.28f,
        minOf(size.width, size.height) * 0.42f
    )
    radii.forEach { r ->
        drawCircle(
            color = Color(0xFF0D2A4A),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 1f)
        )
    }
}

private fun DrawScope.drawSelfNode(
    cx: Float,
    cy: Float,
    pulse: Float,
    glowAlpha: Float,
    color: Color
) {
    val nodeR = 22f
    // Expanding pulse ring
    val pulseR = nodeR + pulse * 40f
    val pulseAlpha = (1f - pulse) * 0.5f
    drawCircle(color = color.copy(alpha = pulseAlpha), radius = pulseR, center = Offset(cx, cy), style = Stroke(2f))

    // Outer glow
    drawCircle(color = color.copy(alpha = glowAlpha * 0.35f), radius = nodeR + 14f, center = Offset(cx, cy))
    // Core
    drawCircle(color = color.copy(alpha = 0.18f), radius = nodeR + 6f, center = Offset(cx, cy))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0.6f)),
            center = Offset(cx, cy),
            radius = nodeR
        ),
        radius = nodeR,
        center = Offset(cx, cy)
    )
    // Inner dot
    drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 5f, center = Offset(cx, cy))
}

private fun DrawScope.drawPeerNode(node: NodePlacement, glowAlpha: Float) {
    val r = node.radiusPx
    val center = Offset(node.cx, node.cy)

    // Glow halo
    drawCircle(color = node.color.copy(alpha = glowAlpha * 0.25f), radius = r + 10f, center = center)
    // Body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(node.color, node.color.copy(alpha = 0.5f)),
            center = center,
            radius = r
        ),
        radius = r,
        center = center
    )
    // Ring outline
    drawCircle(color = node.color.copy(alpha = 0.6f), radius = r, center = center, style = Stroke(1.5f))
    // Inner highlight
    drawCircle(color = Color.White.copy(alpha = 0.4f), radius = r * 0.3f, center = Offset(node.cx, node.cy - r * 0.3f))
}

// ── Color helpers ─────────────────────────────────────────────────────────────

private fun peerNodeColor(peer: ConnectedPeer): Color = when (peer.transport) {
    PeerTransport.BLE, PeerTransport.BLUETOOTH -> NodeBle
    PeerTransport.TCP, PeerTransport.WIFI_DIRECT, PeerTransport.NEARBY -> NodeLan
    PeerTransport.NOSTR -> NodeBitchat
    else -> NodeUnknown
}

private fun transportLineColor(transport: PeerTransport): Color = when (transport) {
    PeerTransport.BLE, PeerTransport.BLUETOOTH -> LineColorBle
    PeerTransport.TCP, PeerTransport.WIFI_DIRECT, PeerTransport.NEARBY -> LineColorLan
    PeerTransport.NOSTR -> LineColorBitchat
    else -> LineColorDefault
}

// ── Legend composable ─────────────────────────────────────────────────────────

@Composable
fun MeshTopologyLegend(modifier: Modifier = Modifier) {
    val items = listOf(
        "You" to NodeSelf,
        "BLE Peer" to NodeBle,
        "LAN Peer" to NodeLan,
        "BitChat Peer" to NodeBitchat
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = color)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            }
        }
    }
}
