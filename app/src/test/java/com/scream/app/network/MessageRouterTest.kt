package com.scream.app.network

import com.scream.app.model.ConnectionQuality
import com.scream.app.model.EncryptionStatus
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.network.peer.PeerManager
import com.scream.app.network.protocol.PeerAddress
import com.scream.app.network.protocol.PeerRoute
import com.scream.app.network.protocol.ScreamProtocolAdapter
import com.scream.app.network.protocol.UnifiedMessage
import com.scream.app.network.protocol.UnifiedMessageType
import com.scream.app.network.protocol.UnifiedPeer
import com.scream.app.network.routing.MessageRouter
import com.scream.app.network.routing.RoutingDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageRouterTest {

    private lateinit var peerManager: PeerManager
    private lateinit var router: MessageRouter

    @Before
    fun setUp() {
        peerManager = PeerManager()
        router = MessageRouter(peerManager)
        router.registerAdapter(ScreamProtocolAdapter())
        router.registerAdapter(BitChatProtocolAdapter)
    }

    @Test
    fun testBroadcastRouting_routesToAllAdapters() {
        val msg = UnifiedMessage(
            id = "msg-1",
            type = UnifiedMessageType.POST,
            sender = UnifiedPeer(
                address = PeerAddress(screamId = "#1234"),
                displayAlias = "Alice"
            ),
            recipient = null,
            body = "Hello world!",
            sourceProtocol = ProtocolType.SCREAM
        )

        val decision = router.route(msg, target = null)
        assertTrue(decision is RoutingDecision.Broadcast)
        val broadcast = decision as RoutingDecision.Broadcast
        assertEquals(2, broadcast.adapters.size)
    }

    @Test
    fun testDirectRouting_prefersKnownPeerRoute() {
        val targetAddr = PeerAddress(screamId = "#5678")
        peerManager.registerPeer(
            address = targetAddr,
            alias = "Bob",
            route = PeerRoute(
                protocol = ProtocolType.SCREAM,
                transport = PeerTransport.TCP,
                address = "192.168.1.50",
                quality = ConnectionQuality.EXCELLENT,
                isDirect = true
            )
        )

        val msg = UnifiedMessage(
            id = "msg-2",
            type = UnifiedMessageType.CHAT,
            sender = UnifiedPeer(address = PeerAddress(screamId = "#1234"), displayAlias = "Alice"),
            recipient = targetAddr,
            body = "Direct message",
            sourceProtocol = ProtocolType.SCREAM
        )

        val decision = router.route(msg, target = targetAddr)
        assertTrue(decision is RoutingDecision.Direct)
        val direct = decision as RoutingDecision.Direct
        assertEquals(ProtocolType.SCREAM, direct.adapter.protocolType)
        assertEquals(PeerTransport.TCP, direct.route.transport)
    }

    @Test
    fun testUnreachableRouting_whenPeerUnknownAndNoRelay() {
        val unknownAddr = PeerAddress(screamId = "#UNKNOWN")
        val msg = UnifiedMessage(
            id = "msg-3",
            type = UnifiedMessageType.CHAT,
            sender = UnifiedPeer(address = PeerAddress(screamId = "#1234"), displayAlias = "Alice"),
            recipient = unknownAddr,
            body = "Private chat",
            sourceProtocol = ProtocolType.SCREAM
        )

        val decision = router.route(msg, target = unknownAddr)
        assertTrue(decision is RoutingDecision.Unreachable)
    }
}
