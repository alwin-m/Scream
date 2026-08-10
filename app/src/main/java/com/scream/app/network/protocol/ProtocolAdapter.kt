package com.scream.app.network.protocol

import com.scream.app.model.ProtocolType

/**
 * Common interface that all protocol adapters implement.
 *
 * Each adapter owns one protocol dialect (SCREAM native, BitChat BLE, BitChat Nostr)
 * and translates between the protocol's wire format and [UnifiedMessage].
 *
 * The adapter does NOT decide where to send a message — that is the
 * [com.scream.app.network.routing.MessageRouter]'s job. The adapter only knows
 * *how* to send over its transports when asked.
 */
interface ProtocolAdapter {

    /** Which protocol this adapter handles. */
    val protocolType: ProtocolType

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Called once when the mesh engine starts.
     * The adapter should set up its transport listeners but NOT start
     * discovery — that is controlled by the engine.
     */
    fun initialize(config: ProtocolConfig)

    /** Graceful shutdown. Release sockets, unregister callbacks, etc. */
    fun shutdown()

    // ── Outbound ─────────────────────────────────────────────────────────────

    /**
     * Encode [message] to this protocol's wire format and deliver to [recipient].
     * Returns a result indicating success or the failure reason.
     */
    suspend fun sendMessage(message: UnifiedMessage, recipient: PeerAddress): SendResult

    /**
     * Encode [message] and deliver to all reachable peers on this protocol.
     */
    suspend fun broadcastMessage(message: UnifiedMessage): SendResult

    // ── Inbound ──────────────────────────────────────────────────────────────

    /** Register a listener for messages this adapter decodes from the wire. */
    fun setMessageListener(listener: MessageListener)

    /** Register a listener for peer discovery events. */
    fun setDiscoveryListener(listener: DiscoveryListener)

    // ── Peer queries ─────────────────────────────────────────────────────────

    /** Peers currently reachable through this adapter's transports. */
    fun getReachablePeers(): List<UnifiedPeer>

    // ── Capability ───────────────────────────────────────────────────────────

    /** Whether this adapter can use the given transport type. */
    fun supportsTransport(transport: com.scream.app.model.PeerTransport): Boolean
}

// ── Supporting types ─────────────────────────────────────────────────────────

/** Configuration bag passed during [ProtocolAdapter.initialize]. */
data class ProtocolConfig(
    val localScreamId: String,
    val localAlias: String,
    val localAvatar: String,
    val meshId: String,
    /** BitChat Ed25519 keypair bytes, if available. */
    val bitchatPublicKey: ByteArray? = null,
    val bitchatPrivateKey: ByteArray? = null,
    val nostrRelayUrls: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProtocolConfig) return false
        return localScreamId == other.localScreamId && meshId == other.meshId
    }

    override fun hashCode(): Int = localScreamId.hashCode() * 31 + meshId.hashCode()
}

/** Outcome of a send attempt. */
sealed class SendResult {
    data class Success(val recipientCount: Int = 1) : SendResult()
    data class PartialSuccess(val sent: Int, val failed: Int) : SendResult()
    data class Failure(val reason: String) : SendResult()
}

/** Callback for decoded inbound messages. */
fun interface MessageListener {
    fun onMessageReceived(message: UnifiedMessage)
}

/** Callback for peer discovery / departure. */
interface DiscoveryListener {
    fun onPeerDiscovered(peer: UnifiedPeer)
    fun onPeerUpdated(peer: UnifiedPeer)
    fun onPeerLost(address: PeerAddress)
}
