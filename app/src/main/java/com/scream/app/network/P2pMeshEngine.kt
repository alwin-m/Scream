package com.scream.app.network

import android.util.Log
import com.scream.app.data.ScreamRepository
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.EncryptionStatus
import com.scream.app.model.MessageKind
import com.scream.app.model.PeerConnectionType
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.model.User
import com.scream.app.security.AutoSecurityGuard
import com.scream.app.network.peer.PeerManager
import com.scream.app.network.protocol.PeerAddress
import com.scream.app.network.protocol.PeerRoute
import com.scream.app.network.protocol.ProtocolConfig
import com.scream.app.network.protocol.ScreamProtocolAdapter
import com.scream.app.network.protocol.UnifiedPeer
import com.scream.app.network.routing.MessageRouter
import kotlinx.coroutines.*
import android.util.Base64
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object P2pMeshEngine {
    private const val TAG = "P2pMeshEngine"
    private const val UDP_PORT = 8888
    private const val TCP_PORT = 8889
    private const val PROTOCOL_VERSION = 1
    private const val MAX_MESSAGE_CACHE_SIZE = 2048
    private const val DEFAULT_TTL = 6
    private const val AES_GCM_TAG_BITS = 128
    private const val AES_GCM_IV_BYTES = 12
    private const val PEER_STALE_TIMEOUT_MS = 12000L
    private const val PEER_NEARBY_TIMEOUT_MS = 30000L

    private var isRunning = false
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentUser: User? = null

    // ── Multi-protocol components ────────────────────────────────────────────
    val peerManager = PeerManager()
    val messageRouter = MessageRouter(peerManager)
    val screamAdapter = ScreamProtocolAdapter()
    // BitChatProtocolAdapter is already an object singleton

    private val peerMap = ConcurrentHashMap<String, PeerInfo>()
    private val discoveredButNotConnected = ConcurrentHashMap<String, PeerInfo>()
    @Volatile private var udpSocket: DatagramSocket? = null
    @Volatile private var tcpServerSocket: ServerSocket? = null
    private val seenMessageIds = object : LinkedHashMap<String, Long>(MAX_MESSAGE_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > MAX_MESSAGE_CACHE_SIZE
        }
    }

    data class PeerInfo(
        val user: User,
        val ipAddress: String,
        val lastSeen: Long,
        val transport: PeerTransport = PeerTransport.TCP,
        var quality: ConnectionQuality = ConnectionQuality.GOOD,
        var connectionType: PeerConnectionType = PeerConnectionType.DIRECT,
        var signalStrength: Int = -60,
        var batteryLevel: Int = 85,
        var osVersion: String = "Android",
        var protocol: ProtocolType = ProtocolType.SCREAM,
        var appFingerprint: String? = null,
        var contributorTag: String? = null
    )

    /** Cached local build fingerprint string; set once from Application context. */
    @Volatile var localFingerprint: String? = null
    @Volatile var localContributorTag: String? = null

    fun start(user: User) {
        if (isRunning) return
        this.currentUser = user
        peerMap.clear()
        discoveredButNotConnected.clear()
        isRunning = true
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Initialize multi-protocol adapters
        val protocolConfig = ProtocolConfig(
            localScreamId = user.id,
            localAlias = user.alias,
            localAvatar = user.avatar,
            meshId = ScreamRepository.getMeshId()
        )
        screamAdapter.initialize(protocolConfig)
        BitChatProtocolAdapter.initialize(protocolConfig)
        messageRouter.registerAdapter(screamAdapter)
        messageRouter.registerAdapter(BitChatProtocolAdapter)
        Log.d(TAG, "Multi-protocol engine started with ${messageRouter.getAllAdapters().size} adapters")

        scope.launch { listenUdpBroadcast() }
        scope.launch { sendUdpHeartbeats() }
        scope.launch { listenTcpServer() }
        scope.launch { cleanStalePeers() }
        scope.launch { updatePeerQualityPeriodically() }
        scope.launch { sendGossipSummaries() }
    }

    fun stop() {
        isRunning = false
        udpSocket?.close()
        udpSocket = null
        tcpServerSocket?.close()
        tcpServerSocket = null
        screamAdapter.shutdown()
        BitChatProtocolAdapter.shutdown()
        scope.cancel()
        peerMap.clear()
        discoveredButNotConnected.clear()
        ScreamRepository.updateActivePeers(emptyList())
    }

    fun broadcastPayload(type: String, payload: JSONObject) {
        val user = currentUser ?: return
        val messageObj = JSONObject().apply {
            put("version", PROTOCOL_VERSION)
            put("id", UUID.randomUUID().toString())
            put("type", type)
            put("sourcePeerId", user.id)
            put("timestamp", System.currentTimeMillis())
            put("ttl", DEFAULT_TTL)
            put("meshId", ScreamRepository.getMeshId())
            put("sender", JSONObject().apply {
                put("id", user.id)
                put("alias", user.alias)
                put("avatar", user.avatar)
                user.profileImage?.let { put("profileImage", it) }
                user.publicKey?.let { put("publicKey", it) }
                put("batteryLevel", ScreamRepository.getBatteryLevel())
                put("osVersion", ScreamRepository.getOSVersion())
                localFingerprint?.let { put("appFingerprint", it) }
                localContributorTag?.let { put("contributorTag", it) }
            })
            put("route", org.json.JSONArray().apply { put(user.alias) })
            put("encryptedData", encryptPayload(payload))
        }

        rememberMessage(messageObj.getString("id"))
        // Broadcast over TCP/UDP (LAN) AND BLE simultaneously
        sendToKnownPeers(messageObj)
        broadcastViaBluetooth(messageObj)
    }

    /**
     * Deliver an incoming BLE GATT message through the same processing pipeline
     * that TCP messages use, so the app is completely transport-agnostic.
     */
    fun handleIncomingBleMessage(jsonString: String, endpointAddress: String? = null) {
        try {
            val json = JSONObject(jsonString)
            val endpoint = endpointAddress?.let { "ble:$it" } ?: "ble:unknown"
            if (!BitChatProtocolAdapter.isBitChatPayload(json) &&
                !AutoSecurityGuard.inspect(json, endpoint).accepted
            ) {
                quarantineTransport(endpoint)
                return
            }
            if (!isCompatibleBuild(json, endpoint)) {
                quarantineTransport(endpoint)
                return
            }
            val messageId = json.optString("id")
            val sourcePeerId = json.optString("sourcePeerId", json.optString("senderId"))

            // Deduplicate — same message may arrive via TCP and BLE
            if (messageId.isNotBlank()) {
                if (hasSeenMessage(messageId)) return
                rememberMessage(messageId)
            }
            if (sourcePeerId.isNotBlank() && sourcePeerId == currentUser?.id) return

            // Handle BitChat protocol envelope
            if (BitChatProtocolAdapter.isBitChatPayload(json)) {
                val bitChatPeer = BitChatProtocolAdapter.parseBitChatPeer(json, transport = PeerTransport.BLE)
                if (bitChatPeer.user.id != currentUser?.id) {
                    mergeBlePlaceholder(endpointAddress, bitChatPeer.user.id)
                    val now = System.currentTimeMillis()
                    val existing = peerMap[bitChatPeer.user.id]
                    if (existing != null) {
                        peerMap[bitChatPeer.user.id] = existing.copy(
                            lastSeen = now,
                            transport = PeerTransport.BLE,
                            protocol = ProtocolType.BITCHAT
                        )
                    } else {
                        peerMap[bitChatPeer.user.id] = PeerInfo(
                            user = bitChatPeer.user,
                            ipAddress = "ble://${bitChatPeer.user.id}",
                            lastSeen = now,
                            transport = PeerTransport.BLE,
                            quality = ConnectionQuality.GOOD,
                            connectionType = PeerConnectionType.DIRECT,
                            protocol = ProtocolType.BITCHAT
                        )
                    }
                    updateRepositoryPeers()
                }

                val chatMsg = BitChatProtocolAdapter.parseBitChatMessage(json)
                if (chatMsg != null) {
                    ScreamRepository.addChatMessage("public_room", chatMsg)
                }
                return
            }

            val senderJson = json.optJSONObject("sender")
            val senderUser = if (senderJson != null) {
                User(
                    id = senderJson.optString("id"),
                    alias = senderJson.optString("alias"),
                    avatar = senderJson.optString("avatar", "😎"),
                    profileImage = senderJson.optString("profileImage").takeIf { it.isNotEmpty() },
                    publicKey = senderJson.optString("publicKey").takeIf { it.isNotEmpty() }
                )
            } else null

            val senderBattery = senderJson?.optInt("batteryLevel", 85) ?: 85
            val senderOs = senderJson?.optString("osVersion", "Android") ?: "Android"
            val senderFp = senderJson?.optString("appFingerprint", "")?.takeIf { it.isNotBlank() }
            val senderCtag = senderJson?.optString("contributorTag", "")?.takeIf { it.isNotBlank() }

            // Register the peer as BLE-connected
            if (senderUser != null && senderUser.id != currentUser?.id) {
                val now = System.currentTimeMillis()
                mergeBlePlaceholder(endpointAddress, senderUser.id)
                val existing = peerMap[senderUser.id]
                if (existing != null) {
                    peerMap[senderUser.id] = existing.copy(
                        lastSeen = now,
                        transport = com.scream.app.model.PeerTransport.BLE,
                        batteryLevel = senderBattery,
                        osVersion = senderOs,
                        appFingerprint = senderFp ?: existing.appFingerprint,
                        contributorTag = senderCtag ?: existing.contributorTag
                    )
                } else {
                    peerMap[senderUser.id] = PeerInfo(
                        user = senderUser,
                        ipAddress = "ble://${endpointAddress ?: senderUser.id}",
                        lastSeen = now,
                        transport = com.scream.app.model.PeerTransport.BLE,
                        quality = com.scream.app.model.ConnectionQuality.GOOD,
                        connectionType = com.scream.app.model.PeerConnectionType.DIRECT,
                        signalStrength = -60,
                        batteryLevel = senderBattery,
                        osVersion = senderOs,
                        appFingerprint = senderFp,
                        contributorTag = senderCtag
                    )
                }
                updateRepositoryPeers()
            }

            // Re-use the existing TCP message handler logic
            val type = json.optString("type")
            val data = decryptPayload(json.optString("encryptedData"))
                ?: json.optJSONObject("data")
                ?: JSONObject()

            dispatchIncomingMessage(type, messageId, senderUser, json, data)

            // Forward over BLE mesh if TTL allows (mesh relay)
            forwardMessageViaBluetooth(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling BLE message: ${e.message}")
        }
    }

    private fun sendToKnownPeers(messageObj: JSONObject, exceptIpAddress: String? = null) {
        val jsonStr = messageObj.toString()

        scope.launch {
            peerMap.values.toList().forEach { peer ->
                if (peer.ipAddress == exceptIpAddress) return@forEach
                if (AutoSecurityGuard.isQuarantined(peer.ipAddress)) return@forEach
                // Only use TCP for LAN peers (not BLE-only peers with ble:// addresses)
                if (peer.ipAddress.startsWith("ble://")) return@forEach
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(peer.ipAddress, TCP_PORT), 1500)
                        socket.soTimeout = 5000
                        socket.getOutputStream().use { output ->
                            output.write(jsonStr.toByteArray(Charsets.UTF_8))
                            output.flush()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send payload to ${peer.ipAddress}: ${e.message}")
                }
            }
        }
    }

    /** Send a message to all connected BLE peers via GATT client writes and server notifications. */
    private fun broadcastViaBluetooth(messageObj: JSONObject) {
        val jsonStr = messageObj.toString()
        scope.launch {
            try {
                // Push to already-connected GATT clients (server → client notifications)
                BleGattServer.notifyConnectedDevices(jsonStr)
                // Write to remote GATT servers we are connected to as a client
                BleGattClient.sendMessage(jsonStr)
            } catch (e: Exception) {
                Log.e(TAG, "BLE broadcast error: ${e.message}")
            }
        }
    }

    /** Forward a relayed BLE message to other BLE peers (mesh relay hop). */
    private fun forwardMessageViaBluetooth(messageObj: JSONObject) {
        val ttl = messageObj.optInt("ttl", 0)
        if (ttl <= 1) return
        val forwarded = JSONObject(messageObj.toString())
        val routeArray = forwarded.optJSONArray("route") ?: org.json.JSONArray()
        currentUser?.let { routeArray.put(it.alias) }
        forwarded.put("route", routeArray)
        forwarded.put("ttl", ttl - 1)
        broadcastViaBluetooth(forwarded)
    }

    private suspend fun listenUdpBroadcast() {
        withContext(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val socket = DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(UDP_PORT))
                    }
                    udpSocket = socket
                    socket.broadcast = true
                    val buffer = ByteArray(2048)

                    while (isRunning) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val senderIp = packet.address.hostAddress ?: continue
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)

                        try {
                            val json = JSONObject(text)
                            if (BitChatProtocolAdapter.isBitChatPayload(json)) {
                                val bitChatPeer = BitChatProtocolAdapter.parseBitChatPeer(
                                    json,
                                    transport = PeerTransport.TCP,
                                    rssi = estimateSignalStrength(senderIp)
                                )
                                if (bitChatPeer.user.id != currentUser?.id) {
                                    val now = System.currentTimeMillis()
                                    val existingPeer = peerMap[bitChatPeer.user.id]
                                    if (existingPeer != null) {
                                        peerMap[bitChatPeer.user.id] = existingPeer.copy(
                                            lastSeen = now,
                                            ipAddress = senderIp,
                                            signalStrength = estimateSignalStrength(senderIp),
                                            protocol = ProtocolType.BITCHAT
                                        )
                                    } else {
                                        val rssi = estimateSignalStrength(senderIp)
                                        peerMap[bitChatPeer.user.id] = PeerInfo(
                                            user = bitChatPeer.user,
                                            ipAddress = senderIp,
                                            lastSeen = now,
                                            transport = PeerTransport.TCP,
                                            quality = ConnectionQuality.fromRssi(rssi),
                                            connectionType = PeerConnectionType.DIRECT,
                                            signalStrength = rssi,
                                            protocol = ProtocolType.BITCHAT
                                        )
                                    }
                                    updateRepositoryPeers()
                                }
                            } else if (json.optString("type") == "HEARTBEAT") {
                                val userObj = json.getJSONObject("user")
                                val peerUser = User(
                                    id = userObj.optString("id"),
                                    alias = userObj.optString("alias"),
                                    avatar = userObj.optString("avatar", "😎"),
                                    profileImage = null
                                )

                                if (peerUser.id != currentUser?.id) {
                                    val now = System.currentTimeMillis()
                                    val existingPeer = peerMap[peerUser.id]
                                    val battery = userObj.optInt("batteryLevel", 85)
                                    val os = userObj.optString("osVersion", "Android")
                                    val peerFp = userObj.optString("appFingerprint", "").takeIf { it.isNotBlank() }
                                    val peerCtag = userObj.optString("contributorTag", "").takeIf { it.isNotBlank() }

                                    if (existingPeer != null) {
                                        peerMap[peerUser.id] = existingPeer.copy(
                                            lastSeen = now,
                                            signalStrength = estimateSignalStrength(senderIp),
                                            batteryLevel = battery,
                                            osVersion = os,
                                            appFingerprint = peerFp ?: existingPeer.appFingerprint,
                                            contributorTag = peerCtag ?: existingPeer.contributorTag
                                        )
                                    } else {
                                        val rssi = estimateSignalStrength(senderIp)
                                        peerMap[peerUser.id] = PeerInfo(
                                            user = peerUser,
                                            ipAddress = senderIp,
                                            lastSeen = now,
                                            transport = PeerTransport.TCP,
                                            quality = ConnectionQuality.fromRssi(rssi),
                                            connectionType = PeerConnectionType.DIRECT,
                                            signalStrength = rssi,
                                            batteryLevel = battery,
                                            osVersion = os,
                                            appFingerprint = peerFp,
                                            contributorTag = peerCtag
                                        )
                                    }
                                    updateRepositoryPeers()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing UDP packet: ${e.message}")
                        }
                    }
                    socket.close()
                    if (udpSocket === socket) udpSocket = null
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "UDP listener paused (Wi-Fi offline or socket error): ${e.message}")
                    delay(4000)
                }
            }
        }
    }

    private suspend fun sendUdpHeartbeats() {
        withContext(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val socket = DatagramSocket()
                    socket.broadcast = true

                    val userJson = JSONObject().apply {
                        put("id", currentUser?.id ?: "#0000")
                        put("alias", currentUser?.alias ?: "Anonymous")
                        put("avatar", currentUser?.avatar ?: "😎")
                        put("batteryLevel", ScreamRepository.getBatteryLevel())
                        put("osVersion", ScreamRepository.getOSVersion())
                        localFingerprint?.let { put("appFingerprint", it) }
                        localContributorTag?.let { put("contributorTag", it) }
                    }

                    val heartbeatJson = JSONObject().apply {
                        put("type", "HEARTBEAT")
                        put("user", userJson)
                        put("meshId", ScreamRepository.getMeshId())
                    }

                    val bytes = heartbeatJson.toString().toByteArray(Charsets.UTF_8)
                    broadcastAddresses().forEach { address ->
                        socket.send(DatagramPacket(bytes, bytes.size, address, UDP_PORT))
                    }
                    socket.close()

                    // Broadcast dual-protocol BitChat heartbeat for BitChat nodes
                    currentUser?.let { user ->
                        val bitChatAnnounce = BitChatProtocolAdapter.buildBitChatAnnouncement(user, ScreamRepository.getMeshId())
                        val bitBytes = bitChatAnnounce.toString().toByteArray(Charsets.UTF_8)
                        val socket2 = DatagramSocket()
                        socket2.broadcast = true
                        val bitPacket = DatagramPacket(
                            bitBytes,
                            bitBytes.size,
                            InetAddress.getByName("255.255.255.255"),
                            UDP_PORT
                        )
                        socket2.send(bitPacket)
                        socket2.close()
                    }
                } catch (e: Exception) {
                    // Suppress log when Wi-Fi is off
                }
                delay(4000)
            }
        }
    }

    private suspend fun listenTcpServer() {
        withContext(Dispatchers.IO) {
            while (isRunning) {
                try {
                    val serverSocket = ServerSocket().apply {
                        reuseAddress = true
                        bind(InetSocketAddress(TCP_PORT))
                    }
                    tcpServerSocket = serverSocket
                    while (isRunning) {
                        val clientSocket = serverSocket.accept()
                        scope.launch { handleTcpClient(clientSocket) }
                    }
                    serverSocket.close()
                    if (tcpServerSocket === serverSocket) tcpServerSocket = null
                } catch (e: Exception) {
                    if (isRunning) Log.w(TAG, "TCP server listener paused (Wi-Fi offline or socket error): ${e.message}")
                    delay(4000)
                }
            }
        }
    }

    private fun handleTcpClient(socket: Socket) {
        try {
            val text = socket.getInputStream().bufferedReader(Charsets.UTF_8).readText()
            if (text.isNotBlank()) {
                val json = JSONObject(text)
                val senderIp = socket.inetAddress.hostAddress ?: "tcp:unknown"
                if (!BitChatProtocolAdapter.isBitChatPayload(json) &&
                    !AutoSecurityGuard.inspect(json, senderIp).accepted
                ) return
                if (!isCompatibleBuild(json, senderIp)) return
                val messageId = json.optString("id")
                val sourcePeerId = json.optString("sourcePeerId", json.optString("senderId"))
                if (messageId.isNotBlank()) {
                    if (hasSeenMessage(messageId)) return
                    rememberMessage(messageId)
                }
                if (sourcePeerId.isNotBlank() && sourcePeerId == currentUser?.id) return

                if (BitChatProtocolAdapter.isBitChatPayload(json)) {
                    val bitChatPeer = BitChatProtocolAdapter.parseBitChatPeer(
                        json,
                        transport = PeerTransport.TCP,
                        rssi = estimateSignalStrength(senderIp)
                    )
                    if (bitChatPeer.user.id != currentUser?.id) {
                        val now = System.currentTimeMillis()
                        val existing = peerMap[bitChatPeer.user.id]
                        if (existing != null) {
                            peerMap[bitChatPeer.user.id] = existing.copy(
                                lastSeen = now,
                                ipAddress = senderIp,
                                protocol = ProtocolType.BITCHAT
                            )
                        } else {
                            val rssi = estimateSignalStrength(senderIp)
                            peerMap[bitChatPeer.user.id] = PeerInfo(
                                user = bitChatPeer.user,
                                ipAddress = senderIp,
                                lastSeen = now,
                                transport = PeerTransport.TCP,
                                quality = ConnectionQuality.fromRssi(rssi),
                                connectionType = PeerConnectionType.DIRECT,
                                signalStrength = rssi,
                                protocol = ProtocolType.BITCHAT
                            )
                        }
                        updateRepositoryPeers()
                    }

                    val chatMsg = BitChatProtocolAdapter.parseBitChatMessage(json)
                    if (chatMsg != null) {
                        ScreamRepository.addChatMessage("public_room", chatMsg)
                    }
                    return
                }
                val senderJson = json.optJSONObject("sender")
                val senderUser = if (senderJson != null) {
                    User(
                        id = senderJson.optString("id"),
                        alias = senderJson.optString("alias"),
                        avatar = senderJson.optString("avatar", "😎"),
                        profileImage = senderJson.optString("profileImage").takeIf { it.isNotEmpty() },
                        publicKey = senderJson.optString("publicKey").takeIf { it.isNotEmpty() }
                    )
                } else null

                val senderBattery = senderJson?.optInt("batteryLevel", 85) ?: 85
                val senderOs = senderJson?.optString("osVersion", "Android") ?: "Android"
                val senderFp = senderJson?.optString("appFingerprint", "")?.takeIf { it.isNotBlank() }
                val senderCtag = senderJson?.optString("contributorTag", "")?.takeIf { it.isNotBlank() }

                if (senderUser != null && senderUser.id != currentUser?.id) {
                    val now = System.currentTimeMillis()
                    val existing = peerMap[senderUser.id]
                    if (existing != null) {
                        peerMap[senderUser.id] = existing.copy(
                            lastSeen = now,
                            ipAddress = senderIp,
                            signalStrength = estimateSignalStrength(senderIp),
                            batteryLevel = senderBattery,
                            osVersion = senderOs,
                            appFingerprint = senderFp ?: existing.appFingerprint,
                            contributorTag = senderCtag ?: existing.contributorTag
                        )
                    } else {
                        val rssi = estimateSignalStrength(senderIp)
                        peerMap[senderUser.id] = PeerInfo(
                            user = senderUser,
                            ipAddress = senderIp,
                            lastSeen = now,
                            transport = PeerTransport.TCP,
                            quality = ConnectionQuality.fromRssi(rssi),
                            connectionType = PeerConnectionType.DIRECT,
                            signalStrength = rssi,
                            batteryLevel = senderBattery,
                            osVersion = senderOs,
                            appFingerprint = senderFp,
                            contributorTag = senderCtag
                        )
                    }
                    updateRepositoryPeers()
                }

                val type = json.optString("type")
                val data = decryptPayload(json.optString("encryptedData"))
                    ?: json.optJSONObject("data")
                    ?: JSONObject()

                dispatchIncomingMessage(type, messageId, senderUser, json, data)

                forwardMessageIfAlive(json, senderIp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling TCP client: ${e.message}")
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private suspend fun cleanStalePeers() {
        while (isRunning) {
            delay(5000)
            val now = System.currentTimeMillis()

            val staleIds = peerMap.filter { now - it.value.lastSeen > PEER_STALE_TIMEOUT_MS }.keys
            if (staleIds.isNotEmpty()) {
                staleIds.forEach { peerMap.remove(it) }
                updateRepositoryPeers()
            }

            val staleDiscovered = discoveredButNotConnected.filter {
                now - it.value.lastSeen > PEER_NEARBY_TIMEOUT_MS
            }.keys
            staleDiscovered.forEach { discoveredButNotConnected.remove(it) }
        }
    }

    private suspend fun updatePeerQualityPeriodically() {
        while (isRunning) {
            delay(8000)
            peerMap.keys.forEach { peerId ->
                peerMap[peerId]?.let { peer ->
                    val rssi = if (peer.transport == PeerTransport.BLE) {
                        peer.signalStrength
                    } else {
                        estimateSignalStrength(peer.ipAddress)
                    }
                    val newQuality = ConnectionQuality.fromRssi(rssi)
                    peerMap[peerId] = peer.copy(
                        quality = newQuality,
                        signalStrength = rssi
                    )
                }
            }
            if (peerMap.isNotEmpty()) {
                updateRepositoryPeers()
            }
        }
    }

    private fun updateRepositoryPeers() {
        val connectedPeers = peerMap.values.map { peer ->
            // Also register with PeerManager for multi-protocol tracking
            peerManager.registerPeer(
                address = PeerAddress(screamId = peer.user.id),
                alias = peer.user.alias,
                avatar = peer.user.avatar,
                route = PeerRoute(
                    protocol = peer.protocol,
                    transport = peer.transport,
                    address = peer.ipAddress,
                    quality = peer.quality,
                    isEncrypted = peer.protocol == ProtocolType.SCREAM,
                    isDirect = !peer.ipAddress.startsWith("ble://") || peer.transport == PeerTransport.BLE
                )
            )

            ConnectedPeer(
                user = peer.user,
                transport = peer.transport,
                quality = peer.quality,
                connectionType = peer.connectionType,
                signalStrength = peer.signalStrength,
                lastSeen = peer.lastSeen,
                ipAddress = peer.ipAddress,
                batteryLevel = peer.batteryLevel,
                osVersion = peer.osVersion,
                protocol = peer.protocol,
                encryptionStatus = if (peer.protocol == ProtocolType.SCREAM)
                    EncryptionStatus.SCREAM_SHARED_KEY else EncryptionStatus.NONE,
                appFingerprint = peer.appFingerprint,
                contributorTag = peer.contributorTag
            )
        }
        ScreamRepository.updateActivePeers(connectedPeers)
    }

    fun registerBlePeerDiscovered(address: String, name: String, rssi: Int, isBitChat: Boolean = false) {
        // One physical BLE address is one nearby device, even when it advertises
        // both SCREAM and BitChat service UUIDs and produces two scan callbacks.
        val peerId = "ble_$address"
        val now = System.currentTimeMillis()
        val existing = peerMap[peerId]
        val proto = if (isBitChat) ProtocolType.BITCHAT else ProtocolType.SCREAM
        val peerUser = User(
            id = peerId,
            alias = if (name.isNotBlank() && name != "Unknown") name else (if (isBitChat) "BitChat Peer" else "Nearby Peer"),
            avatar = if (isBitChat) "⚡" else "📱"
        )

        if (existing != null) {
            peerMap[peerId] = existing.copy(
                lastSeen = now,
                signalStrength = rssi,
                transport = PeerTransport.BLE,
                quality = ConnectionQuality.fromRssi(rssi),
                protocol = proto
            )
        } else {
            peerMap[peerId] = PeerInfo(
                user = peerUser,
                ipAddress = "ble://$address",
                lastSeen = now,
                transport = PeerTransport.BLE,
                quality = ConnectionQuality.fromRssi(rssi),
                connectionType = PeerConnectionType.NEARBY_DISCOVERED,
                signalStrength = rssi,
                protocol = proto
            )
            MeshForegroundService.notifyPeerContact(peerUser, rssi, PeerTransport.BLE.displayName)
        }
        updateRepositoryPeers()
    }

    private fun mergeBlePlaceholder(endpointAddress: String?, userId: String) {
        if (endpointAddress.isNullOrBlank()) return
        peerMap.remove("ble_$endpointAddress")
        // Remove the pre-deduplication key too when upgrading an existing store.
        peerMap.remove("bitchat_$endpointAddress")
        peerMap[userId]?.let { peer ->
            peerMap[userId] = peer.copy(ipAddress = "ble://$endpointAddress")
        }
    }

    private fun isCompatibleBuild(json: JSONObject, endpoint: String): Boolean {
        val peerFingerprint = json.optJSONObject("sender")?.optString("appFingerprint")
            ?.takeIf { it.isNotBlank() }
            ?: json.optJSONObject("user")?.optString("appFingerprint")?.takeIf { it.isNotBlank() }
            ?: return true
        val local = localFingerprint ?: return true
        fun signerHash(value: String): String {
            val parts = value.split(":")
            return if (parts.size >= 4) parts.dropLast(3).joinToString(":") else value
        }
        return if (signerHash(peerFingerprint) == signerHash(local)) {
            true
        } else {
            AutoSecurityGuard.flag(endpoint, "peer build signing certificate mismatch")
            false
        }
    }

    private fun quarantineTransport(endpoint: String) {
        if (!endpoint.startsWith("ble:")) return
        val address = endpoint.removePrefix("ble:")
        BleGattClient.disconnect(address)
        BleGattServer.disconnectDevice(address)
    }

    private fun estimateSignalStrength(ipAddress: String): Int {
        return when {
            ipAddress.startsWith("192.168.") -> -50
            ipAddress.startsWith("10.") -> -55
            ipAddress.startsWith("172.") -> -60
            else -> -70
        }
    }

    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = linkedSetOf<InetAddress>()
        runCatching { addresses += InetAddress.getByName("255.255.255.255") }
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { networkInterface ->
                if (!networkInterface.isUp || networkInterface.isLoopback) return@forEach
                networkInterface.interfaceAddresses.mapNotNull { it.broadcast }.forEach(addresses::add)
            }
        }
        return addresses.toList()
    }

    private fun hasSeenMessage(messageId: String): Boolean = synchronized(seenMessageIds) {
        seenMessageIds.containsKey(messageId)
    }

    private fun rememberMessage(messageId: String) = synchronized(seenMessageIds) {
        seenMessageIds[messageId] = System.currentTimeMillis()
    }

    private fun forwardMessageIfAlive(messageObj: JSONObject, incomingIpAddress: String?) {
        val ttl = messageObj.optInt("ttl", 0)
        if (ttl <= 1) return

        // Append current user to route
        val routeArray = messageObj.optJSONArray("route") ?: org.json.JSONArray()
        currentUser?.let { routeArray.put(it.alias) }
        messageObj.put("route", routeArray)

        messageObj.put("ttl", ttl - 1)
        // Forward over both TCP (LAN) and BLE
        sendToKnownPeers(messageObj, exceptIpAddress = incomingIpAddress)
        broadcastViaBluetooth(messageObj)
    }

    /**
     * Shared message dispatch logic used by both the TCP handler and the BLE handler.
     * Keeps both transport paths in sync without code duplication.
     */
    private fun dispatchIncomingMessage(
        type: String,
        messageId: String,
        senderUser: User?,
        json: JSONObject,
        data: JSONObject
    ) {
        when (type) {
            "MSG_SUMMARY" -> {
                val idsArr = data.optJSONArray("ids") ?: org.json.JSONArray()
                val missingIds = mutableListOf<String>()
                for (i in 0 until idsArr.length()) {
                    val id = idsArr.getString(i)
                    if (!hasSeenMessage(id)) {
                        missingIds.add(id)
                    }
                }
                if (missingIds.isNotEmpty()) {
                    val wantPayload = JSONObject().apply {
                        put("ids", org.json.JSONArray(missingIds))
                    }
                    broadcastPayload("WANT_MSG", wantPayload)
                }
            }
            "WANT_MSG" -> {
                val requestedArr = data.optJSONArray("ids") ?: org.json.JSONArray()
                for (i in 0 until requestedArr.length()) {
                    val reqId = requestedArr.getString(i)
                    ScreamRepository.posts.value.find { it.id == reqId }?.let { post ->
                        val payload = JSONObject().apply {
                            put("id", post.id)
                            put("body", post.body)
                            put("mediaBase64", post.mediaBase64 ?: "")
                            put("mediaMimeType", post.mediaMimeType ?: "")
                            put("audioDurationMs", post.audioDurationMs)
                        }
                        broadcastPayload("NEW_POST", payload)
                    }
                }
            }
            "NEW_POST" -> {
                if (senderUser != null) {
                    val postId = data.optString("id", messageId)
                    val body = data.optString("body")
                    val mediaBase64 = data.optString("mediaBase64").takeIf { it.isNotBlank() }
                    val mediaMimeType = data.optString("mediaMimeType").takeIf { it.isNotBlank() }
                    val audioDurationMs = data.optLong("audioDurationMs", 0L)
                    ScreamRepository.receiveRemotePost(
                        postId = postId,
                        sender = senderUser,
                        text = body,
                        mediaBase64 = mediaBase64,
                        mediaMimeType = mediaMimeType,
                        audioDurationMs = audioDurationMs
                    )
                }
            }
            "POST_VIEW" -> {
                val postId = data.optString("postId")
                senderUser?.id?.let { viewerId ->
                    ScreamRepository.registerPostView(viewerId, postId, shouldBroadcast = false)
                }
            }
            "LIKE_POST" -> {
                val postId = data.optString("postId")
                senderUser?.let { actor ->
                    val reaction = if (data.has("reaction")) data.optString("reaction").takeIf { it == "LIKE" } else "LIKE"
                    ScreamRepository.applyPostReaction(
                        postId = postId,
                        actorId = actor.id,
                        actorAlias = data.optString("actorAlias", actor.alias),
                        reaction = reaction,
                        shouldBroadcast = false
                    )
                }
            }
            "DISLIKE_POST" -> {
                val postId = data.optString("postId")
                senderUser?.let { actor ->
                    val reaction = if (data.has("reaction")) data.optString("reaction").takeIf { it == "DISLIKE" } else "DISLIKE"
                    ScreamRepository.applyPostReaction(
                        postId = postId,
                        actorId = actor.id,
                        actorAlias = data.optString("actorAlias", actor.alias),
                        reaction = reaction,
                        shouldBroadcast = false
                    )
                }
            }
            "RESHARE_POST" -> {
                val postId = data.optString("postId")
                if (senderUser != null) {
                    ScreamRepository.resharePost(senderUser, postId, shouldBroadcast = false)
                }
            }
            "UNRESHARE_POST" -> {
                val postId = data.optString("postId")
                ScreamRepository.unresharePost(postId, shouldBroadcast = false)
            }
            "DELETE_POST" -> {
                val postId = data.optString("postId")
                ScreamRepository.deletePost(postId, shouldBroadcast = false)
            }
            "ADD_TO_ROOM" -> {
                val targetUserId = data.optString("targetUserId")
                val myId = currentUser?.id.orEmpty()
                if (targetUserId == myId) {
                    val roomId = data.optString("roomId")
                    val roomName = data.optString("roomName")
                    val roomIcon = data.optString("roomIcon")
                    val adminId = data.optString("adminId")
                    val membersArr = data.optJSONArray("members")
                    val membersList = mutableListOf<String>()
                    if (membersArr != null) {
                        for (i in 0 until membersArr.length()) {
                            membersList.add(membersArr.getString(i))
                        }
                    }
                    ScreamRepository.receivePrivateRoomInvitation(roomId, roomName, roomIcon, adminId, membersList)
                }
            }
            "REMOVE_FROM_ROOM" -> {
                val targetUserId = data.optString("targetUserId")
                val myId = currentUser?.id.orEmpty()
                if (targetUserId == myId) {
                    val roomId = data.optString("roomId")
                    ScreamRepository.receivePrivateRoomRemoval(roomId)
                }
            }
            "NEW_ROOM" -> {
                val roomId = data.optString("id")
                val name = data.optString("name")
                val icon = data.optString("icon", "💬")
                val isPrivate = data.optBoolean("isPrivate")
                val adminId = data.optString("adminId")
                ScreamRepository.receiveRemoteRoom(roomId, name, icon, isPrivate, adminId)
            }
            "DELETE_ROOM" -> {
                val roomId = data.optString("roomId")
                if (senderUser != null) {
                    ScreamRepository.deleteRoom(roomId, senderUser, shouldBroadcast = false)
                }
            }
            "CHAT_MESSAGE" -> {
                if (senderUser != null) {
                    val chatMessageId = data.optString("id", messageId)
                    val roomId = data.optString("roomId")
                    val body = data.optString("body")
                    val kind = runCatching {
                        MessageKind.valueOf(data.optString("kind", MessageKind.TEXT.name))
                    }.getOrDefault(MessageKind.TEXT)
                    val audioBase64 = data.optString("audioBase64").takeIf { it.isNotBlank() }
                    val audioDurationMs = data.optLong("audioDurationMs", 0L)
                    val mediaBase64 = data.optString("mediaBase64").takeIf { it.isNotBlank() }
                    val mediaMimeType = data.optString("mediaMimeType").takeIf { it.isNotBlank() }
                    val replyToId = data.optString("replyToId").takeIf { it.isNotBlank() }
                    val replyToSender = data.optString("replyToSender").takeIf { it.isNotBlank() }
                    val replyToBody = data.optString("replyToBody").takeIf { it.isNotBlank() }
                    val roomIsPrivate = ScreamRepository.rooms.value
                        .firstOrNull { it.id == roomId }
                        ?.isPrivate == true
                    val messageSender = if (roomIsPrivate) {
                        senderUser.copy(
                            profileImage = data.optString("profileImage")
                                .takeIf { it.isNotBlank() }
                        )
                    } else {
                        senderUser.copy(profileImage = null)
                    }
                    val routeList = mutableListOf<String>()
                    val routeArray = json.optJSONArray("route")
                    if (routeArray != null) {
                        for (i in 0 until routeArray.length()) {
                            routeList.add(routeArray.getString(i))
                        }
                    }
                    ScreamRepository.receiveRemoteChatMessage(
                        messageId = chatMessageId,
                        roomId = roomId,
                        sender = messageSender,
                        text = body,
                        kind = kind,
                        audioBase64 = audioBase64,
                        audioDurationMs = audioDurationMs,
                        mediaBase64 = mediaBase64,
                        mediaMimeType = mediaMimeType,
                        replyToId = replyToId,
                        replyToSender = replyToSender,
                        replyToBody = replyToBody,
                        route = routeList
                    )
                }
            }
            "DELETE_CHAT_MESSAGE" -> {
                val roomId = data.optString("roomId")
                val chatMessageId = data.optString("messageId")
                ScreamRepository.deleteChatMessageForEveryone(roomId, chatMessageId, shouldBroadcast = false)
            }
            "PIN_CHAT_MESSAGE" -> {
                val roomId = data.optString("roomId")
                val chatMessageId = data.optString("messageId")
                val pinnedUntil = data.optLong("pinnedUntil", 0L)
                ScreamRepository.receiveChatMessagePin(roomId, chatMessageId, pinnedUntil)
            }
            "MESSAGE_REACTION" -> {
                val msgId = data.optString("messageId")
                val rmId = data.optString("roomId")
                val emoji = data.optString("emoji")
                val userAlias = data.optString("userAlias")
                val isAdded = data.optBoolean("isAdded")
                ScreamRepository.receiveReactionUpdate(rmId, msgId, emoji, userAlias, isAdded)
            }
        }
    }

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
            decryptPayload(JSONObject(encryptedData))
        }.getOrNull()
    }

    private fun decryptPayload(encryptedData: JSONObject): JSONObject? {
        return runCatching {
            val iv = Base64.decode(encryptedData.optString("iv"), Base64.NO_WRAP)
            val cipherText = Base64.decode(encryptedData.optString("cipherText"), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, meshKey(), GCMParameterSpec(AES_GCM_TAG_BITS, iv))
            JSONObject(String(cipher.doFinal(cipherText), Charsets.UTF_8))
        }.getOrNull()
    }

    private suspend fun sendGossipSummaries() {
        while (isRunning) {
            delay(20_000)
            val recentIds = synchronized(seenMessageIds) {
                seenMessageIds.keys.toList().takeLast(30)
            }
            if (recentIds.isNotEmpty()) {
                val summaryPayload = JSONObject().apply {
                    put("ids", org.json.JSONArray(recentIds))
                }
                broadcastPayload("MSG_SUMMARY", summaryPayload)
            }
        }
    }

    fun triggerImmediateSyncWithPeer(address: String) {
        val user = currentUser ?: return
        val recentIds = synchronized(seenMessageIds) {
            seenMessageIds.keys.toList().takeLast(30)
        }
        val syncPayload = JSONObject().apply {
            put("ids", org.json.JSONArray(recentIds))
            put("address", address)
        }
        broadcastPayload("MSG_SUMMARY", syncPayload)
    }

    private fun meshKey(): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest("SCREAM_LOCAL_MESH_V1".toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }
}
