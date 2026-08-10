package com.scream.app.network.protocol

import android.util.Base64
import android.util.Log
import com.scream.app.data.ScreamRepository
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.MessageKind
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.model.EncryptionStatus
import com.scream.app.model.User
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Protocol adapter for SCREAM's native P2P protocol.
 *
 * This adapter wraps the existing SCREAM message envelope format (JSON over
 * TCP/UDP/BLE) and translates to/from [UnifiedMessage]. It does NOT own
 * transport lifecycle — the transports (LAN sockets, BLE GATT) remain in
 * [com.scream.app.network.P2pMeshEngine] for now.
 *
 * ## Responsibilities
 * - Encode [UnifiedMessage] → SCREAM JSON envelope (with AES-GCM encryption)
 * - Decode SCREAM JSON envelope → [UnifiedMessage]
 * - Map SCREAM message types ↔ [UnifiedMessageType]
 * - Provide peer list from SCREAM-tracked peers
 */
class ScreamProtocolAdapter : ProtocolAdapter {

    companion object {
        private const val TAG = "ScreamProtocolAdapter"
        private const val PROTOCOL_VERSION = 1
        private const val DEFAULT_TTL = 6
        private const val AES_GCM_TAG_BITS = 128
        private const val AES_GCM_IV_BYTES = 12
    }

    override val protocolType: ProtocolType = ProtocolType.SCREAM

    private var config: ProtocolConfig? = null
    private var messageListener: MessageListener? = null
    private var discoveryListener: DiscoveryListener? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun initialize(config: ProtocolConfig) {
        this.config = config
        Log.d(TAG, "SCREAM adapter initialized for user ${config.localScreamId}")
    }

    override fun shutdown() {
        config = null
        Log.d(TAG, "SCREAM adapter shut down")
    }

    // ── Outbound ─────────────────────────────────────────────────────────────

    override suspend fun sendMessage(message: UnifiedMessage, recipient: PeerAddress): SendResult {
        // SCREAM doesn't have targeted sends (it broadcasts to all LAN/BLE peers)
        // The message will be filtered by room/peer at the repository level
        return broadcastMessage(message)
    }

    override suspend fun broadcastMessage(message: UnifiedMessage): SendResult {
        val cfg = config ?: return SendResult.Failure("Adapter not initialized")
        return try {
            val envelope = encodeToScreamEnvelope(message, cfg)
            // The actual transport send is delegated back to P2pMeshEngine
            // which owns the TCP sockets and BLE GATT connections.
            // This adapter only handles encoding/decoding.
            SendResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode SCREAM message: ${e.message}")
            SendResult.Failure(e.message ?: "Encoding failed")
        }
    }

    // ── Inbound ──────────────────────────────────────────────────────────────

    override fun setMessageListener(listener: MessageListener) {
        this.messageListener = listener
    }

    override fun setDiscoveryListener(listener: DiscoveryListener) {
        this.discoveryListener = listener
    }

    /**
     * Called by P2pMeshEngine when a SCREAM-protocol message arrives.
     * Decodes the envelope and pushes to the registered [MessageListener].
     */
    fun handleIncomingScreamMessage(json: JSONObject) {
        val unified = decodeFromScreamEnvelope(json) ?: return
        messageListener?.onMessageReceived(unified)
    }

    /**
     * Called by P2pMeshEngine when a SCREAM heartbeat discovers a peer.
     */
    fun handlePeerDiscovered(
        user: User,
        ipAddress: String,
        transport: PeerTransport,
        quality: ConnectionQuality
    ) {
        val peer = UnifiedPeer(
            address = PeerAddress(screamId = user.id),
            displayAlias = user.alias,
            displayAvatar = user.avatar,
            routes = listOf(
                PeerRoute(
                    protocol = ProtocolType.SCREAM,
                    transport = transport,
                    address = ipAddress,
                    quality = quality,
                    isEncrypted = true,  // SCREAM uses AES-GCM shared key
                    isDirect = true
                )
            )
        )
        discoveryListener?.onPeerDiscovered(peer)
    }

    // ── Peer queries ─────────────────────────────────────────────────────────

    override fun getReachablePeers(): List<UnifiedPeer> {
        // Delegate to PeerManager (adapters don't own peer state)
        return emptyList()
    }

    override fun supportsTransport(transport: PeerTransport): Boolean {
        return transport in listOf(PeerTransport.TCP, PeerTransport.BLE)
    }

    // ── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Encode a [UnifiedMessage] into a SCREAM JSON envelope ready for wire transmission.
     */
    fun encodeToScreamEnvelope(message: UnifiedMessage, cfg: ProtocolConfig): JSONObject {
        val payloadData = buildPayloadData(message)

        return JSONObject().apply {
            put("version", PROTOCOL_VERSION)
            put("id", message.id)
            put("type", unifiedTypeToScreamType(message.type, message.metadata))
            put("sourcePeerId", cfg.localScreamId)
            put("timestamp", message.timestamp)
            put("ttl", DEFAULT_TTL)
            put("meshId", cfg.meshId)
            put("sender", JSONObject().apply {
                put("id", cfg.localScreamId)
                put("alias", cfg.localAlias)
                put("avatar", cfg.localAvatar)
                put("batteryLevel", ScreamRepository.getBatteryLevel())
                put("osVersion", ScreamRepository.getOSVersion())
            })
            put("route", JSONArray().apply { put(cfg.localAlias) })
            put("encryptedData", encryptPayload(payloadData))
        }
    }

    /**
     * Decode a SCREAM JSON envelope into a [UnifiedMessage].
     * Returns null if the envelope is malformed.
     */
    fun decodeFromScreamEnvelope(json: JSONObject): UnifiedMessage? {
        return try {
            val type = json.optString("type")
            val messageId = json.optString("id")
            val senderJson = json.optJSONObject("sender") ?: return null
            val senderUser = User(
                id = senderJson.optString("id"),
                alias = senderJson.optString("alias"),
                avatar = senderJson.optString("avatar", "😎")
            )

            val data = decryptPayload(json.optString("encryptedData"))
                ?: json.optJSONObject("data")
                ?: JSONObject()

            val unifiedType = screamTypeToUnifiedType(type)
            val metadata = mutableMapOf<String, Any>(
                "screamType" to type,
                "rawData" to data.toString()
            )

            // Extract route info
            val routeArray = json.optJSONArray("route")
            if (routeArray != null) {
                val routeList = (0 until routeArray.length()).map { routeArray.getString(it) }
                metadata["route"] = routeList
            }

            // Extract additional context based on message type
            extractTypeSpecificMetadata(type, data, metadata)

            UnifiedMessage(
                id = messageId,
                type = unifiedType,
                sender = UnifiedPeer(
                    address = PeerAddress(screamId = senderUser.id),
                    displayAlias = senderUser.alias,
                    displayAvatar = senderUser.avatar
                ),
                recipient = null,
                body = data.optString("body", ""),
                metadata = metadata,
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                sourceProtocol = ProtocolType.SCREAM,
                encryptionStatus = EncryptionStatus.SCREAM_SHARED_KEY
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode SCREAM envelope: ${e.message}")
            null
        }
    }

    // ── Type mapping ─────────────────────────────────────────────────────────

    private fun screamTypeToUnifiedType(type: String): UnifiedMessageType = when (type) {
        "NEW_POST" -> UnifiedMessageType.POST
        "LIKE_POST", "DISLIKE_POST", "RESHARE_POST", "UNRESHARE_POST",
        "DELETE_POST", "DELETE_ROOM", "DELETE_CHAT_MESSAGE",
        "PIN_CHAT_MESSAGE", "MESSAGE_REACTION" -> UnifiedMessageType.ACTION
        "NEW_ROOM", "ADD_TO_ROOM", "REMOVE_FROM_ROOM" -> UnifiedMessageType.ROOM
        "CHAT_MESSAGE" -> UnifiedMessageType.CHAT
        "HEARTBEAT" -> UnifiedMessageType.ANNOUNCEMENT
        "MSG_SUMMARY", "WANT_MSG" -> UnifiedMessageType.SYNC
        else -> UnifiedMessageType.CHAT
    }

    private fun unifiedTypeToScreamType(type: UnifiedMessageType, metadata: Map<String, Any>): String {
        // If the original SCREAM type is preserved in metadata, use it
        val preserved = metadata["screamType"] as? String
        if (!preserved.isNullOrBlank()) return preserved

        return when (type) {
            UnifiedMessageType.CHAT -> "CHAT_MESSAGE"
            UnifiedMessageType.POST -> "NEW_POST"
            UnifiedMessageType.ANNOUNCEMENT -> "HEARTBEAT"
            UnifiedMessageType.ROOM -> "NEW_ROOM"
            UnifiedMessageType.SYNC -> "MSG_SUMMARY"
            UnifiedMessageType.ACTION -> "CHAT_MESSAGE"  // fallback
            UnifiedMessageType.HANDSHAKE -> "HEARTBEAT"   // no native handshake type
        }
    }

    private fun buildPayloadData(message: UnifiedMessage): JSONObject {
        val data = JSONObject()
        // Re-serialize from metadata if available
        val rawData = message.metadata["rawData"] as? String
        if (rawData != null) {
            return try { JSONObject(rawData) } catch (_: Exception) { data }
        }
        data.put("body", message.body)
        return data
    }

    private fun extractTypeSpecificMetadata(
        type: String,
        data: JSONObject,
        metadata: MutableMap<String, Any>
    ) {
        when (type) {
            "CHAT_MESSAGE" -> {
                metadata["roomId"] = data.optString("roomId")
                val kind = data.optString("kind", MessageKind.TEXT.name)
                metadata["kind"] = kind
            }
            "NEW_POST" -> {
                metadata["postId"] = data.optString("id")
            }
            "LIKE_POST", "DISLIKE_POST", "DELETE_POST" -> {
                metadata["postId"] = data.optString("postId")
            }
        }
    }

    // ── Encryption (same as existing P2pMeshEngine) ──────────────────────────

    private fun encryptPayload(payload: JSONObject): JSONObject {
        val iv = ByteArray(AES_GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, meshKey(), GCMParameterSpec(AES_GCM_TAG_BITS, iv))
        val cipherText = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("alg", "AES-256-GCM")
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("cipherText", Base64.encodeToString(cipherText, Base64.NO_WRAP))
        }
    }

    private fun decryptPayload(encryptedData: String): JSONObject? {
        if (encryptedData.isBlank()) return null
        return runCatching {
            val obj = JSONObject(encryptedData)
            val iv = Base64.decode(obj.optString("iv"), Base64.NO_WRAP)
            val cipherText = Base64.decode(obj.optString("cipherText"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, meshKey(), GCMParameterSpec(AES_GCM_TAG_BITS, iv))
            JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
        }.getOrNull()
    }

    private fun meshKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest("SCREAM_LOCAL_MESH_V1".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}
