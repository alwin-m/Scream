package com.scream.app.data

import com.scream.app.model.ChatMessage
import com.scream.app.model.Post
import com.scream.app.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScreamRepositoryTest {

    private val userAlice = User(id = "#A1B2", alias = "Alice", avatar = "😎")
    private val userBob = User(id = "#B3C4", alias = "Bob", avatar = "⚡")

    @Before
    fun setUp() {
        ScreamRepository.setCurrentUser(userAlice.id, userAlice.alias)
    }

    @Test
    fun testLikePost_togglesLikeCountAndStatus() {
        val post = Post(
            id = "post-100",
            user = userBob,
            body = "Emergency mesh notification test",
            timestamp = "Just now",
            likes = 0
        )

        ScreamRepository.receiveRemotePost(post.id, post.user, post.body)

        // Initial state: not liked
        var currentPost = ScreamRepository.posts.value.find { it.id == post.id }
        assertNotNull(currentPost)
        assertFalse(currentPost?.isLiked == true)

        // First tap: like
        ScreamRepository.likePost(post.id, userAlias = userAlice.alias, shouldBroadcast = false)
        currentPost = ScreamRepository.posts.value.find { it.id == post.id }
        assertTrue(currentPost?.isLiked == true)
        assertEquals(1, currentPost?.likes)

        // Second tap: unlike (toggle)
        ScreamRepository.likePost(post.id, userAlias = userAlice.alias, shouldBroadcast = false)
        currentPost = ScreamRepository.posts.value.find { it.id == post.id }
        assertFalse(currentPost?.isLiked == true)
        assertEquals(0, currentPost?.likes)
    }

    @Test
    fun testLikeAndDislike_areMutuallyExclusive() {
        val post = Post(
            id = "post-200",
            user = userBob,
            body = "Disaster safety tip",
            timestamp = "Just now"
        )
        ScreamRepository.receiveRemotePost(post.id, post.user, post.body)

        // Apply Like
        ScreamRepository.likePost(post.id, userAlias = userAlice.alias, shouldBroadcast = false)
        var currentPost = ScreamRepository.posts.value.find { it.id == post.id }
        assertTrue(currentPost?.isLiked == true)
        assertFalse(currentPost?.isDisliked == true)

        // Apply Dislike -> Should remove Like and apply Dislike
        ScreamRepository.dislikePost(post.id, shouldBroadcast = false)
        currentPost = ScreamRepository.posts.value.find { it.id == post.id }
        assertFalse(currentPost?.isLiked == true)
        assertTrue(currentPost?.isDisliked == true)
        assertEquals(1, currentPost?.dislikes)
        assertEquals(0, currentPost?.likes)
    }

    @Test
    fun testHidePostFromMyFeed_removesPostLocally() {
        val post = Post(
            id = "post-300",
            user = userBob,
            body = "Spam or irrelevant message",
            timestamp = "Just now"
        )
        ScreamRepository.receiveRemotePost(post.id, post.user, post.body)

        assertNotNull(ScreamRepository.posts.value.find { it.id == post.id })

        ScreamRepository.hidePostFromMyFeed(post.id)

        assertNull(ScreamRepository.posts.value.find { it.id == post.id })
    }

    @Test
    fun testDeleteChatMessageForEveryone_replacesBodyWithDeletedPlaceholder() {
        val room = ScreamRepository.createRoom(name = "Emergency Room", isPrivate = false)
        ScreamRepository.sendChatMessage(room.id, userAlice, text = "Sensitive emergency key")

        val message = ScreamRepository.rooms.value.find { it.id == room.id }?.messages?.lastOrNull()
        assertNotNull(message)
        val msgId = message!!.id

        ScreamRepository.deleteChatMessageForEveryone(room.id, msgId, shouldBroadcast = false)

        val updatedMsg = ScreamRepository.rooms.value.find { it.id == room.id }?.messages?.find { it.id == msgId }
        assertNotNull(updatedMsg)
        assertEquals("This message was deleted.", updatedMsg?.body)
        assertTrue(updatedMsg?.isDeletedForEveryone == true)
    }
}
