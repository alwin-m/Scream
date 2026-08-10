package com.scream.app.model

data class User(
    val id: String,
    val alias: String,
    val avatar: String = "😎",
    val age: String = "",
    val gender: String = ""
)

data class Post(
    val id: String,
    val user: User,
    val body: String,
    val timestamp: String,
    val createdAt: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val dislikes: Int = 0,
    val reshares: Int = 0,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val isReshared: Boolean = false,
    val isResharePost: Boolean = false,
    val originalPostId: String? = null,
    val mediaBase64: String? = null,
    val mediaMimeType: String? = null,
    val audioDurationMs: Long = 0L,
    val views: Int = 0,
    val likedBy: List<String> = emptyList(),
    val isDeletedByAuthor: Boolean = false
)

enum class MessageKind {
    TEXT,
    VOICE,
    IMAGE
}

enum class ProtocolType(val displayName: String, val tag: String) {
    SCREAM("SCREAM Native", "SCREAM"),
    BITCHAT("BitChat Interop", "BITCHAT")
}

/**
 * Encryption status for a specific message or peer connection.
 * Ranges from plaintext to fully authenticated E2E sessions.
 */
enum class EncryptionStatus(val displayName: String) {
    /** No encryption — plaintext. */
    NONE("None"),
    /** Current SCREAM app-wide shared AES-GCM key. */
    SCREAM_SHARED_KEY("Shared Key"),
    /** Noise XX mutually authenticated E2E session. */
    NOISE_SESSION("E2E Encrypted"),
    /** Noise X one-way sealed envelope (for offline recipients). */
    NOISE_SEALED("Sealed Envelope")
}

data class ChatMessage(
    val id: String,
    val sender: User,
    val body: String,
    val timestamp: String,
    val createdAt: Long = System.currentTimeMillis(),
    val pinnedUntil: Long? = null,
    val isMine: Boolean = false,
    val kind: MessageKind = MessageKind.TEXT,
    val audioBase64: String? = null,
    val audioDurationMs: Long = 0L,
    val mediaBase64: String? = null,
    val mediaMimeType: String? = null,
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToBody: String? = null,
    val reactions: Map<String, List<String>> = emptyMap(),
    val route: List<String> = emptyList(),
    val deliveryStatus: String = "Delivered",
    val isBookmarked: Boolean = false,
    val protocol: ProtocolType = ProtocolType.SCREAM,
    val isDeletedForEveryone: Boolean = false
) {
    fun expiresAt(defaultTtlMs: Long): Long = pinnedUntil ?: createdAt + defaultTtlMs
}

data class Room(
    val id: String,
    val name: String,
    val icon: String = "💬",
    val preview: String = "",
    val memberCount: Int = 1,
    val isPrivate: Boolean = false,
    val adminId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val members: List<String> = emptyList()
)


enum class PeerTransport(val displayName: String) {
    BLUETOOTH("Bluetooth"),
    BLE("Bluetooth Low Energy"),
    WIFI_DIRECT("Wi-Fi Direct"),
    NEARBY("Nearby Connections"),
    TCP("TCP/IP"),
    NOSTR("Nostr Relay"),
    UNKNOWN("Unknown")
}

enum class ConnectionQuality(val displayName: String, val label: String) {
    EXCELLENT("Excellent", "excellent"),
    GOOD("Good", "good"),
    WEAK("Weak", "weak"),
    DISCONNECTED("Disconnected", "disconnected");

    companion object {
        fun fromRssi(rssi: Int): ConnectionQuality = when {
            rssi > -50 -> EXCELLENT
            rssi > -70 -> GOOD
            rssi > -85 -> WEAK
            else -> DISCONNECTED
        }
    }
}

enum class PeerConnectionType {
    DIRECT,
    NEARBY_DISCOVERED,
    MESH_REACHABLE
}

data class ConnectedPeer(
    val user: User,
    val transport: PeerTransport = PeerTransport.UNKNOWN,
    val quality: ConnectionQuality = ConnectionQuality.GOOD,
    val connectionType: PeerConnectionType = PeerConnectionType.DIRECT,
    val signalStrength: Int = -60,
    val lastSeen: Long = System.currentTimeMillis(),
    val ipAddress: String = "",
    val isRelay: Boolean = false,
    val batteryLevel: Int = 85,
    val isRelayEnabled: Boolean = true,
    val osVersion: String = "Android",
    val protocol: ProtocolType = ProtocolType.SCREAM,
    val nostrPubkey: String? = null,
    val encryptionStatus: EncryptionStatus = EncryptionStatus.NONE
)

enum class NetworkStatus {
    ACTIVE,
    LIMITED,
    OFFLINE;

    val label: String get() = when (this) {
        ACTIVE -> "Connected"
        LIMITED -> "Weak Connection"
        OFFLINE -> "Disconnected"
    }
}

/**
 * Background activity mode controlling mesh network battery usage.
 */
enum class BackgroundMode(val title: String, val description: String) {
    /** Full background activity: BLE scanning, advertising, and LAN sockets active. */
    ACTIVE("Active", "Full background mesh networking and peer discovery enabled."),
    
    /** Deep Sleep: Background scanning and discovery paused to save battery. */
    DEEP_SLEEP("Deep Sleep", "Background discovery paused. Wake up anytime to search for peers."),
    
    /** Disabled: All mesh networking turned off. */
    DISABLED("Disabled", "Networking completely disabled.")
}

/**
 * Battery visibility preference controlling who can view battery status.
 */
enum class BatteryVisibility(val title: String) {
    EVERYONE("Everyone"),
    FRIENDS("Friends / Contacts"),
    NOBODY("Nobody")
}

data class MeshStats(
    val meshId: String = "SCREAM-0000",
    val totalParticipants: Int = 1,
    val directConnections: Int = 0,
    val nearbyDiscovered: Int = 0,
    val meshReachable: Int = 0,
    val networkStatus: NetworkStatus = NetworkStatus.OFFLINE,
    val connectionQuality: ConnectionQuality = ConnectionQuality.DISCONNECTED
)
