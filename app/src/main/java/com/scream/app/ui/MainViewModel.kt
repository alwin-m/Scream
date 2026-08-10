package com.scream.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scream.app.data.ScreamRepository
import com.scream.app.identity.UserPreferencesRepository
import com.scream.app.identity.dataStore
import com.scream.app.model.ConnectedPeer
import com.scream.app.model.MeshStats
import com.scream.app.model.NetworkStatus
import com.scream.app.model.Post
import com.scream.app.model.Room
import com.scream.app.model.User
import com.scream.app.network.MeshNetworkManager
import com.scream.app.network.P2pMeshEngine
import com.scream.app.network.TransportManager
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val userPrefs = UserPreferencesRepository(application.dataStore)

    init {
        ScreamRepository.init(application)
        TransportManager.init(application)
        // MeshNetworkManager and BleGattServer are started by MeshForegroundService.
        // We only need to initialise P2pMeshEngine here once the user profile is loaded.

        viewModelScope.launch {
            while (true) {
                ScreamRepository.purgeExpiredContent()
                delay(60 * 60 * 1000L)
            }
        }
    }

    val currentUser: StateFlow<User?> = userPrefs.userProfileFlow
        .map { profile ->
            if (profile.isRegistered) {
                val u = User(
                    id = if (profile.uuid.length >= 4) "#" + profile.uuid.take(4).uppercase() else "#0000",
                    alias = profile.alias,
                    avatar = profile.emojiAvatar,
                    age = profile.age,
                    gender = profile.gender
                )
                P2pMeshEngine.start(u)
                ScreamRepository.setCurrentUser(u.id, u.alias)
                u
            } else null
        }

        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val posts: StateFlow<List<Post>> = ScreamRepository.posts
    val rooms: StateFlow<List<Room>> = ScreamRepository.rooms
    val activePeers: StateFlow<List<ConnectedPeer>> = ScreamRepository.activePeers
    val peerCount: StateFlow<Int> = ScreamRepository.peerCount
    val meshStats: StateFlow<MeshStats> = ScreamRepository.meshStats
    val networkStatus: StateFlow<NetworkStatus> = ScreamRepository.networkStatus

    fun createPost(
        text: String,
        mediaBase64: String? = null,
        mediaMimeType: String? = null,
        audioDurationMs: Long = 0L
    ) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.createPost(user, text, mediaBase64, mediaMimeType, audioDurationMs)
    }

    fun incrementPostViews(postId: String) {
        ScreamRepository.incrementPostViews(postId)
    }

    fun likePost(postId: String) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.likePost(postId, userAlias = user.alias)
    }

    fun dislikePost(postId: String) {
        ScreamRepository.dislikePost(postId)
    }

    fun resharePost(postId: String) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.resharePost(user, postId)
    }

    fun deletePost(postId: String) {
        ScreamRepository.deletePost(postId)
    }

    fun createRoom(name: String, icon: String = "💬", isPrivate: Boolean = false): Room {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        return ScreamRepository.createRoom(name, icon, isPrivate, user)
    }

    fun inviteUserToPrivateRoom(roomId: String, targetUserId: String) {
        ScreamRepository.inviteUserToPrivateRoom(roomId, targetUserId)
    }

    fun removeUserFromPrivateRoom(roomId: String, targetUserId: String) {
        ScreamRepository.removeUserFromPrivateRoom(roomId, targetUserId)
    }

    fun updateProfile(alias: String, age: String, gender: String, avatar: String) {
        viewModelScope.launch {
            userPrefs.updateUser(alias, age, gender, avatar)
        }
    }

    fun deleteRoom(roomId: String) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.deleteRoom(roomId, user)
    }


    fun sendChatMessage(
        roomId: String,
        text: String,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToBody: String? = null
    ) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.sendChatMessage(roomId, user, text, replyToId, replyToSender, replyToBody)
    }

    fun hidePostFromMyFeed(postId: String) {
        ScreamRepository.hidePostFromMyFeed(postId)
    }

    fun deleteChatMessageForMe(roomId: String, messageId: String) {
        ScreamRepository.deleteChatMessageForMe(roomId, messageId)
    }

    fun deleteChatMessageForEveryone(roomId: String, messageId: String) {
        ScreamRepository.deleteChatMessageForEveryone(roomId, messageId)
    }

    fun setChatMessagePinned(roomId: String, messageId: String, pinned: Boolean) {
        ScreamRepository.setChatMessagePinned(roomId, messageId, pinned)
    }

    fun sendVoiceMessage(
        roomId: String,
        audioFile: File,
        durationMs: Long,
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToBody: String? = null
    ) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.sendVoiceMessage(roomId, user, audioFile, durationMs, replyToId, replyToSender, replyToBody)
    }

    fun sendImageMessage(
        roomId: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        replyToId: String? = null,
        replyToSender: String? = null,
        replyToBody: String? = null
    ) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.sendImageMessage(roomId, user, imageBytes, mimeType, replyToId, replyToSender, replyToBody)
    }

    fun toggleChatMessageReaction(roomId: String, messageId: String, emoji: String) {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        ScreamRepository.toggleChatMessageReaction(roomId, messageId, emoji, user.alias)
    }

    fun toggleChatMessageBookmark(roomId: String, messageId: String) {
        ScreamRepository.toggleChatMessageBookmark(roomId, messageId)
    }

    fun maxVoiceDurationMs(): Long = ScreamRepository.maxVoiceDurationMs()

    fun getOrCreatePrivateRoom(peer: User): Room {
        val user = currentUser.value ?: User(id = "#0000", alias = "Anonymous", avatar = "😎")
        return ScreamRepository.getOrCreatePrivateRoom(user, peer)
    }

    override fun onCleared() {
        super.onCleared()
        // Do NOT stop P2pMeshEngine or MeshNetworkManager here.
        // The MeshForegroundService owns those lifecycles and keeps them running
        // even after the ViewModel is cleared (i.e. app is closed).
    }
}
