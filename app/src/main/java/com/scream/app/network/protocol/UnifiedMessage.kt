package com.scream.app.network.protocol

import com.scream.app.model.ConnectionQuality
import com.scream.app.model.EncryptionStatus
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType

/**
 * Transport-agnostic message used internally by the MessageRouter and PeerManager.
 *
 * All protocol adapters convert their native formats to/from this type so the
 * rest of the app never depends on SCREAM-specific or BitChat-specific wire shapes.
 */
data class UnifiedMessage(
    val id: String,
    val type: UnifiedMessageType,
    val sender: UnifiedPeer,
    val recipient: PeerAddress?,      // null = broadcast
    val body: String,
    val metadata: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis(),
    val sourceProtocol: ProtocolType,
    val encryptionStatus: EncryptionStatus = EncryptionStatus.NONE
)

/**
 * High-level message categories that every protocol must map into.
 */
enum class UnifiedMessageType {
    /** A text or media chat message (1:1 or room). */
    CHAT,
    /** Peer presence / heartbeat advertisement. */
    ANNOUNCEMENT,
    /** Noise or other handshake message. */
    HANDSHAKE,
    /** Store-and-forward sync (gossip summaries, want-msg, etc.). */
    SYNC,
    /** Post to a public or room feed. */
    POST,
    /** Reaction, like, reshare, delete, pin — actions on existing content. */
    ACTION,
    /** Room lifecycle (create, delete, invite, remove). */
    ROOM
}

/**
 * An opaque address that identifies a peer across protocols.
 *
 * Exactly one of [screamId] or [bitchatSenderId] is non-null for a given peer.
 * When both are non-null, the identities have been linked.
 */
data class PeerAddress(
    val screamId: String? = null,
    val bitchatSenderId: ByteArray? = null,
    val nostrPubkey: String? = null
) {
    /** Short display-friendly identifier. */
    val displayId: String
        get() = screamId
            ?: nostrPubkey?.take(8)
            ?: bitchatSenderId?.let { "BC-${it.take(4).joinToString("") { b -> "%02x".format(b) }}" }
            ?: "unknown"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerAddress) return false
        if (screamId != null && screamId == other.screamId) return true
        if (bitchatSenderId != null && other.bitchatSenderId != null &&
            bitchatSenderId.contentEquals(other.bitchatSenderId)) return true
        if (nostrPubkey != null && nostrPubkey == other.nostrPubkey) return true
        return false
    }

    override fun hashCode(): Int {
        // Deterministic hash — prefer screamId, then nostrPubkey, then senderId
        return screamId?.hashCode()
            ?: nostrPubkey?.hashCode()
            ?: bitchatSenderId?.contentHashCode()
            ?: 0
    }
}

/**
 * Unified view of a peer regardless of which protocol discovered them.
 */
data class UnifiedPeer(
    val address: PeerAddress,
    val displayAlias: String,
    val displayAvatar: String = "😎",
    val routes: List<PeerRoute> = emptyList(),
    val lastSeen: Long = System.currentTimeMillis()
) {
    /** The best (lowest-latency, highest-quality) route to this peer, if any. */
    val bestRoute: PeerRoute?
        get() = routes
            .filter { it.quality != ConnectionQuality.DISCONNECTED }
            .minByOrNull { it.routePriority }

    val bestProtocol: ProtocolType?
        get() = bestRoute?.protocol

    val isNearby: Boolean
        get() = routes.any {
            it.transport == PeerTransport.BLE || it.transport == PeerTransport.TCP
        }

    val hasNostrPubkey: Boolean
        get() = address.nostrPubkey != null
}

/**
 * A single route through which a peer can be reached.
 */
data class PeerRoute(
    val protocol: ProtocolType,
    val transport: PeerTransport,
    val address: String,                     // IP, BLE MAC, Nostr pubkey
    val quality: ConnectionQuality = ConnectionQuality.GOOD,
    val isEncrypted: Boolean = false,
    val isDirect: Boolean = true,            // false = relay/courier
    val latency: Long? = null
) {
    /**
     * Lower is better. Direct BLE/TCP beats relayed Nostr.
     */
    internal val routePriority: Int
        get() {
            var score = 0
            if (!isDirect) score += 100
            score += when (transport) {
                PeerTransport.BLE -> 0
                PeerTransport.TCP -> 10
                PeerTransport.WIFI_DIRECT -> 20
                PeerTransport.BLUETOOTH -> 30
                PeerTransport.NEARBY -> 40
                PeerTransport.NOSTR -> 200
                PeerTransport.UNKNOWN -> 300
            }
            score += when (quality) {
                ConnectionQuality.EXCELLENT -> 0
                ConnectionQuality.GOOD -> 5
                ConnectionQuality.WEAK -> 20
                ConnectionQuality.DISCONNECTED -> 1000
            }
            return score
        }
}
