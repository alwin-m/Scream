package com.scream.app.network

import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.model.User
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitChatProtocolAdapterTest {

    @Test
    fun testIsBitChatPayload_detectsBitChatMarkers() {
        val json = JSONObject().apply {
            put("protocol", "BITCHAT")
            put("bitchat_version", "1.0")
            put("nickname", "BitChatNode")
        }

        assertTrue(BitChatProtocolAdapter.isBitChatPayload(json))
    }

    @Test
    fun testParseBitChatPeer_extractsPeerInfo() {
        val json = JSONObject().apply {
            put("protocol", "BITCHAT")
            put("nickname", "Lightning")
            put("senderId", "BC-9988")
            put("avatar", "⚡")
        }

        val peer = BitChatProtocolAdapter.parseBitChatPeer(json, transport = PeerTransport.BLE, rssi = -55)
        assertNotNull(peer)
        assertEquals("Lightning", peer.user.alias)
        assertEquals("⚡", peer.user.avatar)
        assertEquals(ProtocolType.BITCHAT, peer.protocol)
        assertEquals(PeerTransport.BLE, peer.transport)
    }

    @Test
    fun testParseBitChatMessage_extractsChatMessage() {
        val json = JSONObject().apply {
            put("protocol", "BITCHAT")
            put("type", "BITCHAT_CHAT")
            put("id", "bc-msg-100")
            put("nickname", "Storm")
            put("text", "Decentralized mesh message")
            put("timestamp", 1700000000000L)
        }

        val msg = BitChatProtocolAdapter.parseBitChatMessage(json)
        assertNotNull(msg)
        assertEquals("bc-msg-100", msg?.id)
        assertEquals("Decentralized mesh message", msg?.body)
        assertEquals("Storm", msg?.sender?.alias)
        assertEquals(ProtocolType.BITCHAT, msg?.protocol)
    }

    @Test
    fun testBuildBitChatAnnouncement_generatesValidJSON() {
        val user = User(id = "#1234", alias = "Sender", avatar = "🚀")
        val announcement = BitChatProtocolAdapter.buildBitChatAnnouncement(user, meshId = "SCREAM-TEST")

        assertEquals("BITCHAT", announcement.getString("protocol"))
        assertEquals("BITCHAT_HEARTBEAT", announcement.getString("type"))
        assertEquals("Sender", announcement.getString("nickname"))
        assertEquals("SCREAM-TEST", announcement.getString("meshId"))
    }
}
