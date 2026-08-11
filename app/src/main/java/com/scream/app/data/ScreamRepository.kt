package com.scream.app.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.util.Base64
import com.scream.app.model.ChatMessage
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.MeshStats
import com.scream.app.model.MessageKind
import com.scream.app.model.NetworkStatus
import com.scream.app.model.PeerConnectionType
import com.scream.app.model.Post
import com.scream.app.model.Room
import com.scream.app.model.User
import com.scream.app.data.db.ScreamDbHelper
import com.scream.app.network.P2pMeshEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object ScreamRepository {
    private const val PREFS_NAME = "scream_local_store"
    private const val POSTS_KEY = "posts"
    private const val ROOMS_KEY = "rooms"
    private const val CONTENT_TTL_MS = 48L * 60L * 60L * 1000L
    private const val PINNED_CONTENT_TTL_MS = 30L * 24L * 60L * 60L * 1000L

    private val defaultRooms = emptyList<Room>()

    private val _posts = MutableStateFlow<List<Post>>(emptyList())
    val posts: StateFlow<List<Post>> = _posts.asStateFlow()

    private val _rooms = MutableStateFlow<List<Room>>(defaultRooms)
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    private val _activePeers = MutableStateFlow<List<ConnectedPeer>>(emptyList())
    val activePeers: StateFlow<List<ConnectedPeer>> = _activePeers.asStateFlow()

    private val _peerCount = MutableStateFlow(1)
    val peerCount: StateFlow<Int> = _peerCount.asStateFlow()

    private val _meshStats = MutableStateFlow(MeshStats())
    val meshStats: StateFlow<MeshStats> = _meshStats.asStateFlow()

    private val _networkStatus = MutableStateFlow(NetworkStatus.OFFLINE)
    val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private var appContext: Context? = null
    private var localMeshId: String = ""
    private var dbHelper: ScreamDbHelper? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        dbHelper = ScreamDbHelper(context.applicationContext)
        localMeshId = generateMeshId(context)
        _meshStats.value = _meshStats.value.copy(meshId = localMeshId)
        restoreLocalData()
        purgeExpiredContent()
    }

    fun getMeshId(): String = localMeshId

    private var currentUserAlias: String = "Anonymous"
    private var currentUserId: String = ""

    fun setCurrentUser(userId: String, alias: String) {
        currentUserId = userId
        currentUserAlias = alias
    }

    fun updateActivePeers(peers: List<ConnectedPeer>) {
        _activePeers.value = peers
        val livePublicCount = peers.size + 1
        _rooms.value = _rooms.value.map { room ->
            if (!room.isPrivate && !room.name.startsWith("Private:")) {
                room.copy(memberCount = livePublicCount)
            } else room
        }
        val directCount = peers.count { it.connectionType == PeerConnectionType.DIRECT }
        val nearbyCount = peers.count { it.connectionType == PeerConnectionType.NEARBY_DISCOVERED }
        val reachableCount = peers.count { it.connectionType == PeerConnectionType.MESH_REACHABLE }
        val totalParticipants = peers.size + 1

        val bestQuality = peers.minByOrNull { it.quality.ordinal }?.quality
            ?: ConnectionQuality.DISCONNECTED

        val status = when {
            directCount > 0 && bestQuality != ConnectionQuality.WEAK -> NetworkStatus.ACTIVE
            nearbyCount > 0 || directCount > 0 -> NetworkStatus.LIMITED
            else -> NetworkStatus.OFFLINE
        }

        _peerCount.value = totalParticipants
        _networkStatus.value = status
        _meshStats.value = MeshStats(
            meshId = localMeshId,
            totalParticipants = totalParticipants,
            directConnections = directCount,
            nearbyDiscovered = nearbyCount,
            meshReachable = reachableCount,
            networkStatus = status,
            connectionQuality = bestQuality
        )
    }

    fun updateNetworkStatus(status: NetworkStatus) {
        _networkStatus.value = status
        _meshStats.value = _meshStats.value.copy(networkStatus = status)
    }

    fun createPost(
        currentUser: User,
        text: String,
        mediaBase64: String? = null,
        mediaMimeType: String? = null,
        audioDurationMs: Long = 0L
    ) {
        val newPost = Post(
            id = UUID.randomUUID().toString(),
            user = currentUser,
            body = text,
            timestamp = "Just now",
            mediaBase64 = mediaBase64,
            mediaMimeType = mediaMimeType,
            audioDurationMs = audioDurationMs,
            views = 0
        )
        _posts.value = listOf(newPost) + _posts.value
        persistLocalData()

        val payload = JSONObject().apply {
            put("id", newPost.id)
            put("body", text)
            put("mediaBase64", mediaBase64 ?: "")
            put("mediaMimeType", mediaMimeType ?: "")
            put("audioDurationMs", audioDurationMs)
        }
        P2pMeshEngine.broadcastPayload("NEW_POST", payload)
    }

    fun receiveRemotePost(
        postId: String,
        sender: User,
        text: String,
        mediaBase64: String? = null,
        mediaMimeType: String? = null,
        audioDurationMs: Long = 0L
    ) {
        if (_posts.value.any { it.id == postId }) return
        val newPost = Post(
            id = postId,
            user = sender,
            body = text,
            timestamp = "Just now",
            mediaBase64 = mediaBase64,
            mediaMimeType = mediaMimeType,
            audioDurationMs = audioDurationMs,
            views = 0
        )
        _posts.value = listOf(newPost) + _posts.value
        persistLocalData()
    }

    /** Records one view per logical SCREAM identity and relays only new receipts. */
    fun registerPostView(userId: String, postId: String, shouldBroadcast: Boolean = true) {
        if (userId.isBlank() || postId.isBlank()) return
        if (_posts.value.firstOrNull { it.id == postId }?.user?.id == userId) return
        val dbHelper = dbHelper ?: return
        try {
            val db = dbHelper.writableDatabase
            val contentValues = android.content.ContentValues().apply {
                put("user_id", userId)
                put("post_id", postId)
                put("viewed_at", System.currentTimeMillis())
            }
            val inserted = db.insertWithOnConflict(
                "post_views",
                null,
                contentValues,
                android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
            )
            if (inserted != -1L) {
                // First unique view from this logical user ID
                _posts.value = _posts.value.map { post ->
                    if (post.id == postId) {
                        post.copy(views = post.views + 1)
                    } else post
                }
                persistLocalData()
                if (shouldBroadcast) {
                    P2pMeshEngine.broadcastPayload("POST_VIEW", JSONObject().apply { put("postId", postId) })
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreamRepository", "Error recording unique post view: ${e.message}")
        }
    }

    fun likePost(postId: String, shouldBroadcast: Boolean = true) {
        val post = _posts.value.firstOrNull { it.id == postId } ?: return
        if (post.isLiked) dbHelper?.setPostReaction(currentUserId, postId, "LIKE")
        val next = if (post.isLiked) null else "LIKE"
        applyPostReaction(postId, currentUserId, currentUserAlias, next, shouldBroadcast)
    }

    fun dislikePost(postId: String, shouldBroadcast: Boolean = true) {
        val post = _posts.value.firstOrNull { it.id == postId } ?: return
        if (post.isDisliked) dbHelper?.setPostReaction(currentUserId, postId, "DISLIKE")
        val next = if (post.isDisliked) null else "DISLIKE"
        applyPostReaction(postId, currentUserId, currentUserAlias, next, shouldBroadcast)
    }

    /** Applies a sender-specific desired reaction state. Duplicate receipts are no-ops. */
    fun applyPostReaction(
        postId: String,
        actorId: String,
        actorAlias: String,
        reaction: String?,
        shouldBroadcast: Boolean = true
    ) {
        if (postId.isBlank() || actorId.isBlank()) return
        val change = dbHelper?.setPostReaction(actorId, postId, reaction) ?: return
        if (change.previous == change.current) return

        _posts.value = _posts.value.map { post ->
            if (post.id != postId) return@map post
            val likesAfterRemoval = if (change.previous == "LIKE") (post.likes - 1).coerceAtLeast(0) else post.likes
            val dislikesAfterRemoval = if (change.previous == "DISLIKE") (post.dislikes - 1).coerceAtLeast(0) else post.dislikes
            val updatedLikedBy = when {
                change.previous == "LIKE" -> post.likedBy - actorAlias
                else -> post.likedBy
            }
            post.copy(
                likes = if (reaction == "LIKE") likesAfterRemoval + 1 else likesAfterRemoval,
                dislikes = if (reaction == "DISLIKE") dislikesAfterRemoval + 1 else dislikesAfterRemoval,
                likedBy = if (reaction == "LIKE") (updatedLikedBy + actorAlias).distinct() else updatedLikedBy,
                isLiked = if (actorId == currentUserId) reaction == "LIKE" else post.isLiked,
                isDisliked = if (actorId == currentUserId) reaction == "DISLIKE" else post.isDisliked
            )
        }
        persistLocalData()

        if (shouldBroadcast) {
            val type = if (reaction == "DISLIKE") "DISLIKE_POST" else "LIKE_POST"
            P2pMeshEngine.broadcastPayload(type, JSONObject().apply {
                put("postId", postId)
                put("reaction", reaction ?: "")
                put("actorAlias", actorAlias)
            })
        }
    }

    fun resharePost(currentUser: User, postId: String, shouldBroadcast: Boolean = true) {
        val target = _posts.value.find { it.id == postId } ?: return
        if (target.isReshared) {
            unresharePost(postId, shouldBroadcast)
            return
        }
        _posts.value = _posts.value.map { post ->
            if (post.id == postId) {
                post.copy(reshares = post.reshares + 1, isReshared = true)
            } else post
        }
        val resharePost = target.copy(
            id = UUID.randomUUID().toString(),
            user = currentUser,
            timestamp = "Just now (Reshared)",
            createdAt = System.currentTimeMillis(),
            likes = 0, dislikes = 0, reshares = 0,
            isLiked = false, isDisliked = false, isReshared = false,
            isResharePost = true,
            originalPostId = postId
        )
        _posts.value = listOf(resharePost) + _posts.value
        persistLocalData()

        if (shouldBroadcast) {
            val payload = JSONObject().apply { put("postId", postId) }
            P2pMeshEngine.broadcastPayload("RESHARE_POST", payload)
        }
    }

    fun unresharePost(postId: String, shouldBroadcast: Boolean = true) {
        _posts.value = _posts.value
            .filterNot { it.isResharePost && it.originalPostId == postId }
            .map { post ->
                if (post.id == postId && post.isReshared) {
                    post.copy(reshares = (post.reshares - 1).coerceAtLeast(0), isReshared = false)
                } else post
            }
        persistLocalData()

        if (shouldBroadcast) {
            val payload = JSONObject().apply { put("postId", postId) }
            P2pMeshEngine.broadcastPayload("UNRESHARE_POST", payload)
        }
    }

    fun deletePost(postId: String, shouldBroadcast: Boolean = true) {
        _posts.value = _posts.value.mapNotNull { post ->
            if (post.id == postId) {
                // If it's the original post, mark or remove it
                null
            } else if (post.originalPostId == postId) {
                // If it's a reshare referencing the deleted post, update text
                post.copy(
                    body = "This post is no longer available.",
                    isDeletedByAuthor = true,
                    mediaBase64 = null
                )
            } else post
        }
        persistLocalData()

        if (shouldBroadcast) {
            val payload = JSONObject().apply { put("postId", postId) }
            P2pMeshEngine.broadcastPayload("DELETE_POST", payload)
        }
    }

    fun hidePostFromMyFeed(postId: String) {
        _posts.value = _posts.value.filterNot { it.id == postId }
        persistLocalData()
    }

    fun createRoom(name: String, icon: String = "💬", isPrivate: Boolean = false, admin: User? = null): Room {
        val newRoom = Room(
            id = UUID.randomUUID().toString(),
            name = name,
            icon = icon,
            preview = "Room created",
            memberCount = if (isPrivate) 1 else _peerCount.value,
            isPrivate = isPrivate,
            adminId = admin?.id.orEmpty()
        )
        _rooms.value = listOf(newRoom) + _rooms.value
        persistLocalData()

        val payload = JSONObject().apply {
            put("id", newRoom.id)
            put("name", name)
            put("icon", icon)
            put("isPrivate", isPrivate)
            put("adminId", newRoom.adminId)
        }
        P2pMeshEngine.broadcastPayload("NEW_ROOM", payload)

        return newRoom
    }

    fun deleteRoom(roomId: String, currentUser: User, shouldBroadcast: Boolean = true) {
        val room = _rooms.value.find { it.id == roomId } ?: return
        if (room.adminId.isNotBlank() && room.adminId != currentUser.id) return
        _rooms.value = _rooms.value.filterNot { it.id == roomId }
        persistLocalData()

        if (shouldBroadcast) {
            val payload = JSONObject().apply { put("roomId", roomId) }
            P2pMeshEngine.broadcastPayload("DELETE_ROOM", payload)
        }
    }

    fun receiveRemoteRoom(roomId: String, name: String, icon: String = "💬", isPrivate: Boolean = false, adminId: String = "") {
        if (roomId.isBlank() || name.isBlank()) return
        if (isPrivate) return // Private rooms are invisible and only added via direct invitation!
        if (_rooms.value.any { it.id == roomId }) return

        val newRoom = Room(
            id = roomId,
            name = name,
            icon = icon,
            preview = "Room created nearby",
            memberCount = _peerCount.value,
            isPrivate = isPrivate,
            adminId = adminId
        )
        _rooms.value = listOf(newRoom) + _rooms.value
        persistLocalData()
    }

    fun inviteUserToPrivateRoom(roomId: String, targetUserId: String) {
        val room = _rooms.value.find { it.id == roomId } ?: return
        if (room.members.contains(targetUserId)) return
        val updatedMembers = room.members + targetUserId
        _rooms.value = _rooms.value.map { r ->
            if (r.id == roomId) {
                r.copy(members = updatedMembers, memberCount = updatedMembers.size.coerceAtLeast(1) + 1)
            } else r
        }
        persistLocalData()

        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("roomName", room.name)
            put("roomIcon", room.icon)
            put("adminId", room.adminId)
            put("targetUserId", targetUserId)
            put("members", JSONArray(updatedMembers))
        }
        P2pMeshEngine.broadcastPayload("ADD_TO_ROOM", payload)
    }

    fun removeUserFromPrivateRoom(roomId: String, targetUserId: String) {
        val room = _rooms.value.find { it.id == roomId } ?: return
        val updatedMembers = room.members - targetUserId
        _rooms.value = _rooms.value.map { r ->
            if (r.id == roomId) {
                r.copy(members = updatedMembers, memberCount = updatedMembers.size.coerceAtLeast(1) + 1)
            } else r
        }
        persistLocalData()

        val payload = JSONObject().apply {
            put("roomId", roomId)
            put("targetUserId", targetUserId)
        }
        P2pMeshEngine.broadcastPayload("REMOVE_FROM_ROOM", payload)
    }

    fun receivePrivateRoomInvitation(roomId: String, name: String, icon: String, adminId: String, members: List<String> = emptyList()) {
        if (_rooms.value.any { it.id == roomId }) return
        val newRoom = Room(
            id = roomId,
            name = name,
            icon = icon,
            preview = "You were added to this private room",
            memberCount = members.size.coerceAtLeast(1),
            isPrivate = true,
            adminId = adminId,
            members = members
        )
        _rooms.value = listOf(newRoom) + _rooms.value
        persistLocalData()
    }

    fun receivePrivateRoomRemoval(roomId: String) {
        _rooms.value = _rooms.value.filterNot { it.id == roomId }
        persistLocalData()
    }


    fun sendChatMessage(
        roomId: String,
        sender: User,
        text: String,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToBody: String? = null
    ) {
        if (text.isBlank()) return
        val newMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = sender,
            body = text,
            timestamp = "Just now",
            isMine = true,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToBody = replyToBody,
            route = listOf(sender.alias),
            deliveryStatus = outgoingDeliveryStatus()
        )
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages + newMsg
                room.copy(
                    preview = "${sender.alias}: $text",
                    messages = updatedMessages
                )
            } else room
        }
        persistLocalData()

        val payload = JSONObject().apply {
            put("id", newMsg.id)
            put("roomId", roomId)
            put("body", text)
            addPrivateProfileImage(this, roomId, sender)
            if (replyToId != null) {
                put("replyToId", replyToId)
                put("replyToSender", replyToSender)
                put("replyToBody", replyToBody)
            }
        }
        P2pMeshEngine.broadcastPayload("CHAT_MESSAGE", payload)
    }

    fun sendVoiceMessage(
        roomId: String,
        sender: User,
        audioFile: File,
        durationMs: Long,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToBody: String? = null
    ) {
        if (!audioFile.exists() || durationMs <= 0L || durationMs > MAX_VOICE_DURATION_MS) return
        val audioBase64 = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
        val newMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = sender,
            body = "Voice message",
            timestamp = "Just now",
            isMine = true,
            kind = MessageKind.VOICE,
            audioBase64 = audioBase64,
            audioDurationMs = durationMs,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToBody = replyToBody,
            route = listOf(sender.alias),
            deliveryStatus = outgoingDeliveryStatus()
        )
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages + newMsg
                room.copy(
                    preview = "${sender.alias}: Voice message",
                    messages = updatedMessages
                )
            } else room
        }
        persistLocalData()

        val payload = JSONObject().apply {
            put("id", newMsg.id)
            put("roomId", roomId)
            put("body", newMsg.body)
            put("kind", newMsg.kind.name)
            put("audioBase64", audioBase64)
            put("audioDurationMs", durationMs)
            addPrivateProfileImage(this, roomId, sender)
            if (replyToId != null) {
                put("replyToId", replyToId)
                put("replyToSender", replyToSender)
                put("replyToBody", replyToBody)
            }
        }
        P2pMeshEngine.broadcastPayload("CHAT_MESSAGE", payload)
    }

    fun sendImageMessage(
        roomId: String,
        sender: User,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToBody: String? = null
    ) {
        if (imageBytes.isEmpty()) return
        val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        val newMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            sender = sender,
            body = "Photo",
            timestamp = "Just now",
            isMine = true,
            kind = MessageKind.IMAGE,
            mediaBase64 = imageBase64,
            mediaMimeType = mimeType,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToBody = replyToBody,
            route = listOf(sender.alias),
            deliveryStatus = outgoingDeliveryStatus()
        )
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages + newMsg
                room.copy(
                    preview = "${sender.alias}: Photo",
                    messages = updatedMessages
                )
            } else room
        }
        persistLocalData()

        val payload = JSONObject().apply {
            put("id", newMsg.id)
            put("roomId", roomId)
            put("body", newMsg.body)
            put("kind", newMsg.kind.name)
            put("mediaBase64", imageBase64)
            put("mediaMimeType", mimeType)
            addPrivateProfileImage(this, roomId, sender)
            if (replyToId != null) {
                put("replyToId", replyToId)
                put("replyToSender", replyToSender)
                put("replyToBody", replyToBody)
            }
        }
        P2pMeshEngine.broadcastPayload("CHAT_MESSAGE", payload)
    }

    private fun addPrivateProfileImage(payload: JSONObject, roomId: String, sender: User) {
        val room = _rooms.value.firstOrNull { it.id == roomId }
        if (room?.isPrivate == true) {
            sender.profileImage?.let { payload.put("profileImage", it) }
        }
    }

    /** A mesh send has no recipient ACK yet, so it must never be labelled Delivered. */
    private fun outgoingDeliveryStatus(): String = when (_networkStatus.value) {
        NetworkStatus.OFFLINE -> "Queued"
        else -> "Relayed"
    }

    fun receiveRemoteChatMessage(
        messageId: String,
        roomId: String,
        sender: User,
        text: String,
        kind: MessageKind = MessageKind.TEXT,
        audioBase64: String? = null,
        audioDurationMs: Long = 0L,
        mediaBase64: String? = null,
        mediaMimeType: String? = null,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToBody: String? = null,
        route: List<String> = emptyList()
    ) {
        if (_rooms.value.any { room -> room.messages.any { it.id == messageId } }) return
        val newMsg = ChatMessage(
            id = messageId,
            sender = sender,
            body = text,
            timestamp = "Just now",
            isMine = false,
            kind = kind,
            audioBase64 = audioBase64,
            audioDurationMs = audioDurationMs,
            mediaBase64 = mediaBase64,
            mediaMimeType = mediaMimeType,
            replyToId = replyToId,
            replyToSender = replyToSender,
            replyToBody = replyToBody,
            route = route,
            deliveryStatus = "Relayed"
        )
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages + newMsg
                room.copy(
                    preview = "${sender.alias}: $text",
                    messages = updatedMessages
                )
            } else room
        }
        persistLocalData()
        if (sender.id != currentUserId) {
            showIncomingMessageNotification(sender.alias, text)
        }
    }

    fun addChatMessage(roomId: String, message: ChatMessage) {
        if (_rooms.value.any { room -> room.messages.any { it.id == message.id } }) return
        val targetRoomId = if (_rooms.value.any { it.id == roomId }) roomId else "public_room"
        _rooms.value = _rooms.value.map { room ->
            if (room.id == targetRoomId) {
                val updatedMessages = room.messages + message
                room.copy(
                    preview = "${message.sender.alias}: ${message.body}",
                    messages = updatedMessages
                )
            } else room
        }
        persistLocalData()
        if (!message.isMine && message.sender.id != currentUserId) {
            showIncomingMessageNotification(message.sender.alias, message.body)
        }
    }

    fun toggleChatMessageReaction(roomId: String, messageId: String, emoji: String, userAlias: String) {
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages.map { msg ->
                    if (msg.id == messageId) {
                        val currentList = msg.reactions[emoji] ?: emptyList()
                        val newList = if (currentList.contains(userAlias)) {
                            currentList - userAlias
                        } else {
                            currentList + userAlias
                        }
                        val newReactions = if (newList.isEmpty()) {
                            msg.reactions - emoji
                        } else {
                            msg.reactions + (emoji to newList)
                        }
                        msg.copy(reactions = newReactions)
                    } else msg
                }
                room.copy(messages = updatedMessages)
            } else room
        }
        persistLocalData()

        val isAdded = _rooms.value
            .find { it.id == roomId }
            ?.messages
            ?.find { it.id == messageId }
            ?.reactions
            ?.get(emoji)
            ?.contains(userAlias) == true

        val payload = JSONObject().apply {
            put("messageId", messageId)
            put("roomId", roomId)
            put("emoji", emoji)
            put("userAlias", userAlias)
            put("isAdded", isAdded)
        }
        P2pMeshEngine.broadcastPayload("MESSAGE_REACTION", payload)
    }

    fun receiveReactionUpdate(roomId: String, messageId: String, emoji: String, userAlias: String, isAdded: Boolean) {
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages.map { msg ->
                    if (msg.id == messageId) {
                        val currentList = msg.reactions[emoji] ?: emptyList()
                        val newList = if (isAdded) {
                            if (currentList.contains(userAlias)) currentList else currentList + userAlias
                        } else {
                            currentList - userAlias
                        }
                        val newReactions = if (newList.isEmpty()) {
                            msg.reactions - emoji
                        } else {
                            msg.reactions + (emoji to newList)
                        }
                        msg.copy(reactions = newReactions)
                    } else msg
                }
                room.copy(messages = updatedMessages)
            } else room
        }
        persistLocalData()
    }

    fun toggleChatMessageBookmark(roomId: String, messageId: String) {
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(isBookmarked = !msg.isBookmarked)
                    } else msg
                }
                room.copy(messages = updatedMessages)
            } else room
        }
        persistLocalData()
    }

    fun getOrCreatePrivateRoom(currentUser: User, peer: User): Room {
        val roomId = "private_${peer.id}"
        val existing = _rooms.value.find { it.id == roomId }
        if (existing != null) return existing

        val privateRoom = Room(
            id = roomId,
            name = "Private: ${peer.alias}",
            icon = peer.profileImage ?: peer.avatar,
            preview = "Private connection started",
            memberCount = 2,
            isPrivate = true,
            adminId = currentUser.id
        )
        _rooms.value = listOf(privateRoom) + _rooms.value
        persistLocalData()
        return privateRoom
    }

    fun deleteChatMessageForMe(roomId: String, messageId: String) {
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages.filterNot { it.id == messageId }
                room.copy(messages = updatedMessages)
            } else room
        }
        persistLocalData()
    }

    fun deleteChatMessageForEveryone(roomId: String, messageId: String, shouldBroadcast: Boolean = true) {
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                val updatedMessages = room.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(
                            body = "This message was deleted.",
                            isDeletedForEveryone = true,
                            mediaBase64 = null,
                            audioBase64 = null,
                            reactions = emptyMap()
                        )
                    } else msg
                }
                room.copy(messages = updatedMessages)
            } else room
        }
        persistLocalData()

        if (shouldBroadcast) {
            val payload = JSONObject().apply {
                put("roomId", roomId)
                put("messageId", messageId)
            }
            P2pMeshEngine.broadcastPayload("DELETE_CHAT_MESSAGE", payload)
        }
    }

    fun setChatMessagePinned(roomId: String, messageId: String, pinned: Boolean, shouldBroadcast: Boolean = true) {
        val pinnedUntil = if (pinned) System.currentTimeMillis() + PINNED_CONTENT_TTL_MS else null
        applyChatMessagePin(roomId, messageId, pinnedUntil)

        if (shouldBroadcast) {
            val payload = JSONObject().apply {
                put("roomId", roomId)
                put("messageId", messageId)
                put("pinnedUntil", pinnedUntil ?: 0L)
            }
            P2pMeshEngine.broadcastPayload("PIN_CHAT_MESSAGE", payload)
        }
    }

    fun receiveChatMessagePin(roomId: String, messageId: String, pinnedUntil: Long) {
        applyChatMessagePin(roomId, messageId, pinnedUntil.takeIf { it > System.currentTimeMillis() })
    }

    fun maxVoiceDurationMs(): Long = MAX_VOICE_DURATION_MS

    private fun applyChatMessagePin(roomId: String, messageId: String, pinnedUntil: Long?) {
        _rooms.value = _rooms.value.map { room ->
            if (room.id == roomId) {
                room.copy(
                    messages = room.messages.map { message ->
                        if (message.id == messageId) {
                            message.copy(pinnedUntil = pinnedUntil)
                        } else message
                    }
                )
            } else room
        }
        persistLocalData()
    }

    fun purgeExpiredContent() {
        val cutoff = System.currentTimeMillis() - CONTENT_TTL_MS
        val privateCutoff = System.currentTimeMillis() - (4L * 60L * 60L * 1000L) // 4 hours
        val now = System.currentTimeMillis()
        
        dbHelper?.purgeExpiredContent(cutoff, privateCutoff, now)
        _posts.value = _posts.value.filter { it.createdAt >= cutoff }
        _rooms.value = _rooms.value.map { room ->
            val limit = if (room.isPrivate) privateCutoff else cutoff
            val freshMessages = room.messages.filter { it.expiresAt(CONTENT_TTL_MS) > now && it.createdAt >= limit }
            room.copy(
                messages = freshMessages,
                preview = freshMessages.lastOrNull()?.let { "${it.sender.alias}: ${it.body}" } ?: room.preview
            )
        }
        persistLocalData()
    }

    private fun generateMeshId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var storedId = prefs.getString("mesh_id", null)
        if (storedId != null) return storedId

        val raw = UUID.randomUUID().toString().take(4).uppercase()
        storedId = "SCREAM-$raw"
        prefs.edit().putString("mesh_id", storedId).apply()
        return storedId
    }

    private fun persistLocalData() {
        val db = dbHelper ?: return
        _posts.value.forEach { db.insertOrUpdatePost(it) }
        _rooms.value.forEach { room ->
            db.insertOrUpdateRoom(room)
            room.messages.forEach { msg ->
                db.insertOrUpdateMessage(room.id, msg)
            }
        }
    }

    private fun restoreLocalData() {
        val db = dbHelper ?: return
        val restoredPosts = db.getAllPosts()
        if (restoredPosts.isNotEmpty()) {
            _posts.value = restoredPosts
        }

        val restoredRooms = db.getAllRooms()
        if (restoredRooms.isNotEmpty()) {
            _rooms.value = (restoredRooms + defaultRooms).distinctBy { it.id }
        }
    }

    private fun User.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("alias", alias)
        put("avatar", avatar)
        profileImage?.let { put("profileImage", it) }
        put("age", age)
        put("gender", gender)
    }

    private fun Post.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("user", user.toJson())
        put("body", body)
        put("timestamp", timestamp)
        put("createdAt", createdAt)
        put("likes", likes)
        put("dislikes", dislikes)
        put("reshares", reshares)
        put("isLiked", isLiked)
        put("isDisliked", isDisliked)
        put("isReshared", isReshared)
        put("isResharePost", isResharePost)
        put("originalPostId", originalPostId ?: "")
        put("mediaBase64", mediaBase64 ?: "")
        put("mediaMimeType", mediaMimeType ?: "")
        put("audioDurationMs", audioDurationMs)
        put("views", views)
        put("likedBy", JSONArray(likedBy))
    }

    private fun ChatMessage.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sender", sender.toJson())
        put("body", body)
        put("timestamp", timestamp)
        put("createdAt", createdAt)
        put("pinnedUntil", pinnedUntil ?: 0L)
        put("isMine", isMine)
        put("kind", kind.name)
        put("audioBase64", audioBase64 ?: "")
        put("audioDurationMs", audioDurationMs)
        put("mediaBase64", mediaBase64 ?: "")
        put("mediaMimeType", mediaMimeType ?: "")
        put("replyToId", replyToId ?: "")
        put("replyToSender", replyToSender ?: "")
        put("replyToBody", replyToBody ?: "")
        put("deliveryStatus", deliveryStatus)
        put("isBookmarked", isBookmarked)
        
        val rxObj = JSONObject()
        reactions.forEach { (emoji, list) ->
            rxObj.put(emoji, JSONArray(list))
        }
        put("reactions", rxObj)
        put("route", JSONArray(route))
    }

    private fun Room.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("icon", icon)
        put("preview", preview)
        put("memberCount", memberCount)
        put("isPrivate", isPrivate)
        put("adminId", adminId)
        put("messages", JSONArray(messages.map { it.toJson() }))
        put("members", JSONArray(members))
    }

    private fun JSONObject.toUser(): User = User(
        id = optString("id"),
        alias = optString("alias"),
        avatar = optString("avatar", "😎"),
        profileImage = optString("profileImage").takeIf { it.isNotBlank() },
        age = optString("age", ""),
        gender = optString("gender", "")
    )

    private fun postFromJson(json: JSONObject): Post {
        val likedByList = mutableListOf<String>()
        val likedByArr = json.optJSONArray("likedBy")
        if (likedByArr != null) {
            for (i in 0 until likedByArr.length()) {
                likedByList.add(likedByArr.getString(i))
            }
        }
        return Post(
            id = json.optString("id"),
            user = json.getJSONObject("user").toUser(),
            body = json.optString("body"),
            timestamp = json.optString("timestamp", "Just now"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            likes = json.optInt("likes"),
            dislikes = json.optInt("dislikes"),
            reshares = json.optInt("reshares"),
            isLiked = json.optBoolean("isLiked"),
            isDisliked = json.optBoolean("isDisliked"),
            isReshared = json.optBoolean("isReshared"),
            isResharePost = json.optBoolean("isResharePost"),
            originalPostId = json.optString("originalPostId").takeIf { it.isNotBlank() },
            mediaBase64 = json.optString("mediaBase64").takeIf { it.isNotBlank() },
            mediaMimeType = json.optString("mediaMimeType").takeIf { it.isNotBlank() },
            audioDurationMs = json.optLong("audioDurationMs", 0L),
            views = json.optInt("views", 0),
            likedBy = likedByList
        )
    }

    private fun chatMessageFromJson(json: JSONObject): ChatMessage {
        val rxMap = mutableMapOf<String, List<String>>()
        val rxObj = json.optJSONObject("reactions")
        if (rxObj != null) {
            rxObj.keys().forEach { emoji ->
                val arr = rxObj.optJSONArray(emoji)
                if (arr != null) {
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getString(i))
                    }
                    rxMap[emoji] = list
                }
            }
        }

        val routeList = mutableListOf<String>()
        val routeArr = json.optJSONArray("route")
        if (routeArr != null) {
            for (i in 0 until routeArr.length()) {
                routeList.add(routeArr.getString(i))
            }
        }

        return ChatMessage(
            id = json.optString("id"),
            sender = json.getJSONObject("sender").toUser(),
            body = json.optString("body"),
            timestamp = json.optString("timestamp", "Just now"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            pinnedUntil = json.optLong("pinnedUntil", 0L).takeIf { it > 0L },
            isMine = json.optBoolean("isMine"),
            kind = runCatching { MessageKind.valueOf(json.optString("kind", MessageKind.TEXT.name)) }.getOrDefault(MessageKind.TEXT),
            audioBase64 = json.optString("audioBase64").takeIf { it.isNotBlank() },
            audioDurationMs = json.optLong("audioDurationMs", 0L),
            mediaBase64 = json.optString("mediaBase64").takeIf { it.isNotBlank() },
            mediaMimeType = json.optString("mediaMimeType").takeIf { it.isNotBlank() },
            replyToId = json.optString("replyToId").takeIf { it.isNotBlank() },
            replyToSender = json.optString("replyToSender").takeIf { it.isNotBlank() },
            replyToBody = json.optString("replyToBody").takeIf { it.isNotBlank() },
            reactions = rxMap,
            route = routeList,
            deliveryStatus = json.optString("deliveryStatus", "Delivered"),
            isBookmarked = json.optBoolean("isBookmarked", false)
        )
    }

    private fun roomFromJson(json: JSONObject): Room {
        val memberList = mutableListOf<String>()
        val memberArr = json.optJSONArray("members")
        if (memberArr != null) {
            for (i in 0 until memberArr.length()) {
                memberList.add(memberArr.getString(i))
            }
        }
        return Room(
            id = json.optString("id"),
            name = json.optString("name"),
            icon = json.optString("icon", "💬"),
            preview = json.optString("preview", ""),
            memberCount = json.optInt("memberCount", 1),
            isPrivate = json.optBoolean("isPrivate"),
            adminId = json.optString("adminId", ""),
            messages = json.optJSONArray("messages")?.toObjectList { chatMessageFromJson(it) } ?: emptyList(),
            members = memberList
        )
    }


    private fun <T> JSONArray.toObjectList(mapper: (JSONObject) -> T): List<T> {
        return (0 until length()).mapNotNull { index ->
            optJSONObject(index)?.let(mapper)
        }
    }

    private const val MAX_VOICE_DURATION_MS = 2L * 60L * 1000L

    fun getBatteryLevel(): Int {
        val ctx = appContext ?: return 85
        val batteryStatus: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt()
        } else {
            85
        }
    }

    fun getOSVersion(): String {
        return "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    /**
     * Retrieves exact device model name (e.g. "Samsung Galaxy A70", "Google Pixel 7a").
     */
    fun getFormattedDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "$manufacturer $model"
        }
    }

    /**
     * GrapheneOS-style hardware security profile.
     */
    data class DeviceHardwareProfile(
        val deviceName: String,
        val manufacturer: String,
        val model: String,
        val androidVersion: String,
        val sdkInt: Int,
        val securityPatch: String
    )

    fun getDeviceHardwareProfile(): DeviceHardwareProfile {
        val patch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Build.VERSION.SECURITY_PATCH
        } else {
            "Standard"
        }
        return DeviceHardwareProfile(
            deviceName = getFormattedDeviceName(),
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            androidVersion = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = patch
        )
    }

    fun showIncomingMessageNotification(senderName: String, messageText: String) {
        val ctx = appContext ?: return
        val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channelId = "scream_new_messages"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "New Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Offline private and room messages alerts"
                enableLights(true)
                lightColor = android.graphics.Color.BLUE
                enableVibration(true)
                val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                setSound(soundUri, android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(ctx, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(ctx)
        }

        val notification = builder
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
