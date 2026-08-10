package com.scream.app.network.routing

import android.util.Log
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.network.peer.PeerManager
import com.scream.app.network.protocol.PeerAddress
import com.scream.app.network.protocol.ProtocolAdapter
import com.scream.app.network.protocol.SendResult
import com.scream.app.network.protocol.UnifiedMessage
import com.scream.app.network.protocol.UnifiedMessageType

/**
 * Central message routing engine.
 *
 * Given an outbound [UnifiedMessage] and an optional target [PeerAddress],
 * the router decides which [ProtocolAdapter](s) should carry the message
 * and over which transport.
 *
 * Routing rules (in priority order):
 * 1. If the message is targeted and the peer is in [PeerManager], pick the
 *    best known route (direct > relay, encrypted > plain, low-latency first).
 * 2. If the peer is known only on one protocol, use that protocol's adapter.
 * 3. If the message is a broadcast (no recipient), fan out to all adapters.
 * 4. If no route is found, return [RoutingDecision.Unreachable].
 */
class MessageRouter(
    private val peerManager: PeerManager,
    private val policy: RoutingPolicy = RoutingPolicy()
) {
    companion object {
        private const val TAG = "MessageRouter"
    }

    private val adapters = mutableMapOf<ProtocolType, ProtocolAdapter>()

    /** Register an adapter. Call before [start]. */
    fun registerAdapter(adapter: ProtocolAdapter) {
        adapters[adapter.protocolType] = adapter
        Log.d(TAG, "Registered adapter: ${adapter.protocolType.displayName}")
    }

    fun getAdapter(protocol: ProtocolType): ProtocolAdapter? = adapters[protocol]

    fun getAllAdapters(): Collection<ProtocolAdapter> = adapters.values

    // ── Routing ──────────────────────────────────────────────────────────────

    /**
     * Decide how to deliver [message] to [target].
     * If [target] is null the message is a broadcast.
     */
    fun route(message: UnifiedMessage, target: PeerAddress? = null): RoutingDecision {
        // Broadcasts go to every adapter
        if (target == null || message.type == UnifiedMessageType.POST) {
            return RoutingDecision.Broadcast(adapters.values.toList())
        }

        val peer = peerManager.resolve(target)

        if (peer == null) {
            // Unknown peer — if we have a Nostr pubkey, try relay delivery
            if (target.nostrPubkey != null) {
                val bitchatAdapter = adapters[ProtocolType.BITCHAT]
                if (bitchatAdapter != null) {
                    return RoutingDecision.Relay(bitchatAdapter, PeerTransport.NOSTR)
                }
            }
            return RoutingDecision.Unreachable("Peer not found: ${target.displayId}")
        }

        // Pick the best available route
        val bestRoute = peer.bestRoute
        if (bestRoute != null) {
            val adapter = adapters[bestRoute.protocol]
            if (adapter != null) {
                return if (bestRoute.isDirect) {
                    RoutingDecision.Direct(adapter, bestRoute)
                } else {
                    RoutingDecision.Relay(adapter, bestRoute.transport)
                }
            }
        }

        // Fallback: broadcast to all
        return if (adapters.isNotEmpty()) {
            RoutingDecision.Broadcast(adapters.values.toList())
        } else {
            RoutingDecision.Unreachable("No adapters registered")
        }
    }

    /**
     * Execute a routing decision: actually deliver the message.
     */
    suspend fun execute(message: UnifiedMessage, target: PeerAddress? = null): SendResult {
        val decision = route(message, target)
        Log.d(TAG, "Routing ${message.type} → $decision")

        return when (decision) {
            is RoutingDecision.Direct -> {
                decision.adapter.sendMessage(message, target ?: message.recipient ?: return SendResult.Failure("No recipient"))
            }
            is RoutingDecision.Relay -> {
                decision.adapter.sendMessage(message, target ?: message.recipient ?: return SendResult.Failure("No recipient"))
            }
            is RoutingDecision.Broadcast -> {
                var sent = 0
                var failed = 0
                for (adapter in decision.adapters) {
                    when (val result = adapter.broadcastMessage(message)) {
                        is SendResult.Success -> sent += result.recipientCount
                        is SendResult.PartialSuccess -> {
                            sent += result.sent
                            failed += result.failed
                        }
                        is SendResult.Failure -> failed++
                    }
                }
                if (sent > 0) SendResult.PartialSuccess(sent, failed)
                else SendResult.Failure("All adapters failed")
            }
            is RoutingDecision.Unreachable -> SendResult.Failure(decision.reason)
        }
    }
}
