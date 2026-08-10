package com.scream.app.network.routing

import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.network.protocol.ProtocolAdapter
import com.scream.app.network.protocol.PeerRoute

/**
 * The outcome of a routing decision made by [MessageRouter].
 */
sealed class RoutingDecision {
    /**
     * Send directly through a single adapter over a specific transport.
     * Used when we know exactly which protocol/transport can reach the peer.
     */
    data class Direct(
        val adapter: ProtocolAdapter,
        val route: PeerRoute
    ) : RoutingDecision()

    /**
     * Send through a relay transport (e.g. Nostr relays, mesh couriers).
     * The message may not arrive immediately.
     */
    data class Relay(
        val adapter: ProtocolAdapter,
        val transport: PeerTransport
    ) : RoutingDecision()

    /**
     * Broadcast across all provided adapters.
     * Used for public posts, room messages, and when the best route is unknown.
     */
    data class Broadcast(
        val adapters: List<ProtocolAdapter>
    ) : RoutingDecision()

    /**
     * No route found — the peer is not reachable through any adapter.
     */
    data class Unreachable(
        val reason: String
    ) : RoutingDecision()
}

/**
 * Policy hints that influence routing decisions.
 *
 * The default policy prefers direct connections over relays and
 * SCREAM protocol over BitChat when both are available.
 */
data class RoutingPolicy(
    /** Prefer encrypted routes even if they have higher latency. */
    val preferEncrypted: Boolean = true,

    /** Prefer direct routes over relay/courier routes. */
    val preferDirect: Boolean = true,

    /**
     * When a peer is reachable through multiple protocols, which to prefer.
     * Lower index = higher priority.
     */
    val protocolPriority: List<ProtocolType> = listOf(
        ProtocolType.SCREAM,
        ProtocolType.BITCHAT
    ),

    /**
     * Transport preference order within a protocol.
     * Lower index = higher priority.
     */
    val transportPriority: List<PeerTransport> = listOf(
        PeerTransport.BLE,
        PeerTransport.TCP,
        PeerTransport.WIFI_DIRECT,
        PeerTransport.BLUETOOTH,
        PeerTransport.NEARBY,
        PeerTransport.NOSTR,
        PeerTransport.UNKNOWN
    )
)
