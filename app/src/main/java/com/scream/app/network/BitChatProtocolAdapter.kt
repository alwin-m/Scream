package com.scream.app.network

import android.util.Log
import com.scream.app.model.ChatMessage
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.EncryptionStatus
import com.scream.app.model.MessageKind
import com.scream.app.model.PeerConnectionType
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.model.User
import com.scream.app.network.protocol.DiscoveryListener
import com.scream.app.network.protocol.MessageListener
import com.scream.app.network.protocol.PeerAddress
import com.scream.app.network.protocol.PeerRoute
import com.scream.app.network.protocol.ProtocolAdapter
import com.scream.app.network.protocol.ProtocolConfig
import com.scream.app.network.protocol.SendResult
import com.scream.app.network.protocol.UnifiedMessage
import com.scream.app.network.protocol.UnifiedMessageType
import com.scream.app.network.protocol.UnifiedPeer
import org.json.JSONObject
import java.util.UUID

/**
 * Protocol Interoperability Adapter for BitChat.
 *
 * Implements [ProtocolAdapter] to provide BitChat-compatible communication
 * within the SCREAM multi-protocol architecture.
 *
 * ## Current state (Phase 1)
 * Uses a JSON-based format for peer discovery and message exchange.
 * This is compatible with SCREAM's internal BitChat interop layer but NOT
 * with actual BitChat apps (which use a binary format).
 *
 * ## Phase 2 upgrade
 * Will add `BitChatPacketCodec` for true binary protocol support and
 * Noise XX handshake for E2E encryption.
 *
 * ## Transports
 * - **BLE mesh**: via SCREAM's existing GATT client/server
 * - **Nostr relays**: planned for Phase 3
 */
object BitChatProtocolAdapter : ProtocolAdapter {

    private const val TAG = "BitChatProtocolAdapter"

    val BITCHAT_SERVICE_UUID: UUID = UUID.fromString("0000FDB2-0000-1000-8000-00805F9B34FB")

    override val protocolType: ProtocolType = ProtocolType.BITCHAT

    private var config: ProtocolConfig? = null
    private var messageListener: MessageListener? = null
    private var discoveryListener: DiscoveryListener? = null

    // ── ProtocolAdapter lifecycle ─────────────────────────────────────────────

    override fun initialize(config: ProtocolConfig) {
        this.config = config
        Log.d(TAG, "BitChat adapter initialized for user ${config.localScreamId}")
    }

    override fun shutdown() {
        config = null
        Log.d(TAG, "BitChat adapter shut down")
    }

    // ── ProtocolAdapter outbound ─────────────────────────────────────────────

    override suspend fun sendMessage(message: UnifiedMessage, recipient: PeerAddress): SendResult {
        return broadcastMessage(message)
    }

    override suspend fun broadcastMessage(message: UnifiedMessage): SendResult {
        return try {
            // Encode to BitChat JSON format and deliver via BLE
            val encoded = when (message.type) {
                UnifiedMessageType.ANNOUNCEMENT -> {
                    val cfg = config ?: return SendResult.Failure("Not initialized")
                    buildBitChatAnnouncement(
                        User(cfg.localScreamId, cfg.localAlias, cfg.localAvatar),
                        cfg.meshId
                    )
                }
                UnifiedMessageType.CHAT -> {
                    val chatMsg = ChatMessage(
                        id = message.id,
                        sender = User(
                            id = message.sender.address.screamId ?: "unknown",
                            alias = message.sender.displayAlias,
                            avatar = message.sender.displayAvatar
                        ),
                        body = message.body,
                        timestamp = "Just now",
                        createdAt = message.timestamp,
                        kind = MessageKind.TEXT,
                        protocol = ProtocolType.BITCHAT
                    )
                    val targetId = message.recipient?.screamId
                    encodeChatMessage(chatMsg, targetId)
                }
                else -> return SendResult.Failure("Unsupported type: ${message.type}")
            }
            // Actual BLE send is handled by P2pMeshEngine's broadcastViaBluetooth
            SendResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "BitChat broadcast failed: ${e.message}")
            SendResult.Failure(e.message ?: "Unknown error")
        }
    }

    // ── ProtocolAdapter inbound ──────────────────────────────────────────────

    override fun setMessageListener(listener: MessageListener) {
        this.messageListener = listener
    }

    override fun setDiscoveryListener(listener: DiscoveryListener) {
        this.discoveryListener = listener
    }

    /**
     * Called by P2pMeshEngine when a BitChat message arrives (BLE or LAN).
     * Notifies both the discovery listener and message listener.
     */
    fun handleIncomingBitChatPayload(json: JSONObject, transport: PeerTransport = PeerTransport.BLE, rssi: Int = -60) {
        val peer = parseBitChatPeerAsUnified(json, transport, rssi)
        discoveryListener?.onPeerDiscovered(peer)

        val chatMsg = parseBitChatAsUnifiedMessage(json)
        if (chatMsg != null) {
            messageListener?.onMessageReceived(chatMsg)
        }
    }

    override fun getReachablePeers(): List<UnifiedPeer> = emptyList()

    override fun supportsTransport(transport: PeerTransport): Boolean {
        return transport in listOf(PeerTransport.BLE, PeerTransport.NOSTR)
    }

    // ── Payload detection ────────────────────────────────────────────────────

    fun isBitChatPayload(json: JSONObject): Boolean {
        val proto = json.optString("protocol")
        val type = json.optString("type")
        val bitChatMarker = json.has("bitchat_version") || json.has("bitchat") || json.has("nickname") || type.startsWith("BITCHAT_")
        return proto.equals("BITCHAT", ignoreCase = true) || proto.startsWith("BITCHAT", ignoreCase = true) || bitChatMarker
    }

    // ── Peer parsing ─────────────────────────────────────────────────────────

    fun parseBitChatPeer(json: JSONObject, transport: PeerTransport = PeerTransport.BLE, rssi: Int = -60): ConnectedPeer {
        val senderObj = json.optJSONObject("sender")
        val rawId = senderObj?.optString("id") ?: json.optString("senderId", json.optString("peerId", "BC-${json.optString("nickname", "Peer").hashCode()}"))
        val senderId = if (rawId.startsWith("#")) rawId else "#$rawId"
        val alias = senderObj?.optString("alias") ?: json.optString("nickname", json.optString("alias", "BitChat Peer"))
        val avatar = senderObj?.optString("avatar") ?: json.optString("avatar", "⚡")

        val user = User(
            id = senderId,
            alias = alias,
            avatar = avatar
        )

        return ConnectedPeer(
            user = user,
            transport = transport,
            quality = ConnectionQuality.fromRssi(rssi),
            connectionType = PeerConnectionType.DIRECT,
            signalStrength = rssi,
            lastSeen = System.currentTimeMillis(),
            osVersion = json.optString("osVersion", "BitChat Protocol Node"),
            protocol = ProtocolType.BITCHAT
        )
    }

    /** Parse into the unified peer model used by PeerManager. */
    private fun parseBitChatPeerAsUnified(
        json: JSONObject,
        transport: PeerTransport,
        rssi: Int
    ): UnifiedPeer {
        val senderObj = json.optJSONObject("sender")
        val rawId = senderObj?.optString("id")
            ?: json.optString("senderId", json.optString("peerId", "BC-${json.optString("nickname", "Peer").hashCode()}"))
        val senderId = if (rawId.startsWith("#")) rawId else "#$rawId"
        val alias = senderObj?.optString("alias")
            ?: json.optString("nickname", json.optString("alias", "BitChat Peer"))
        val avatar = senderObj?.optString("avatar")
            ?: json.optString("avatar", "⚡")

        return UnifiedPeer(
            address = PeerAddress(screamId = senderId),
            displayAlias = alias,
            displayAvatar = avatar,
            routes = listOf(
                PeerRoute(
                    protocol = ProtocolType.BITCHAT,
                    transport = transport,
                    address = "ble://$senderId",
                    quality = ConnectionQuality.fromRssi(rssi),
                    isEncrypted = false,  // No Noise session yet (Phase 2)
                    isDirect = true
                )
            )
        )
    }

    // ── Message parsing ──────────────────────────────────────────────────────

    fun parseBitChatMessage(json: JSONObject): ChatMessage? {
        val body = json.optString("text", json.optString("content", json.optString("message", "")))
        if (body.isBlank()) return null

        val peer = parseBitChatPeer(json)
        val msgId = json.optString("id", UUID.randomUUID().toString())
        val timestampMs = json.optLong("timestamp", System.currentTimeMillis())

        return ChatMessage(
            id = msgId,
            sender = peer.user,
            body = body,
            timestamp = "Just now",
            createdAt = timestampMs,
            kind = MessageKind.TEXT,
            protocol = ProtocolType.BITCHAT
        )
    }

    /** Parse into the unified message model used by MessageRouter. */
    private fun parseBitChatAsUnifiedMessage(json: JSONObject): UnifiedMessage? {
        val body = json.optString("text", json.optString("content", json.optString("message", "")))
        if (body.isBlank()) return null

        val peer = parseBitChatPeerAsUnified(json, PeerTransport.BLE, -60)
        val msgId = json.optString("id", UUID.randomUUID().toString())
        val timestampMs = json.optLong("timestamp", System.currentTimeMillis())

        val type = json.optString("type", "")
        val unifiedType = when {
            type.contains("HEARTBEAT", ignoreCase = true) -> UnifiedMessageType.ANNOUNCEMENT
            type.contains("CHAT", ignoreCase = true) -> UnifiedMessageType.CHAT
            else -> UnifiedMessageType.CHAT
        }

        return UnifiedMessage(
            id = msgId,
            type = unifiedType,
            sender = peer,
            recipient = null,
            body = body,
            metadata = mapOf("bitchatType" to type),
            timestamp = timestampMs,
            sourceProtocol = ProtocolType.BITCHAT,
            encryptionStatus = EncryptionStatus.NONE  // No Noise yet (Phase 2)
        )
    }

    // ── Encoding ─────────────────────────────────────────────────────────────

    fun buildBitChatAnnouncement(user: User, meshId: String): JSONObject {
        return JSONObject().apply {
            put("protocol", "BITCHAT")
            put("bitchat_version", "1.0")
            put("type", "BITCHAT_HEARTBEAT")
            put("nickname", user.alias)
            put("senderId", user.id)
            put("meshId", meshId)
            put("sender", JSONObject().apply {
                put("id", user.id)
                put("alias", user.alias)
                put("avatar", user.avatar)
            })
            put("timestamp", System.currentTimeMillis())
        }
    }

    fun encodeChatMessage(message: ChatMessage, targetPeerId: String? = null): JSONObject {
        return JSONObject().apply {
            put("protocol", "BITCHAT")
            put("bitchat_version", "1.0")
            put("type", "BITCHAT_CHAT")
            put("id", message.id)
            put("senderId", message.sender.id)
            put("nickname", message.sender.alias)
            put("targetId", targetPeerId ?: "*")
            put("text", message.body)
            put("content", message.body)
            put("timestamp", message.createdAt)
            put("sender", JSONObject().apply {
                put("id", message.sender.id)
                put("alias", message.sender.alias)
                put("avatar", message.sender.avatar)
            })
        }
    }
}
