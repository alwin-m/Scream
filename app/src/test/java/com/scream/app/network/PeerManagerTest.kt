package com.scream.app.network

import com.scream.app.model.ConnectionQuality
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.network.peer.PeerManager
import com.scream.app.network.protocol.PeerAddress
import com.scream.app.network.protocol.PeerRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeerManagerTest {

    private lateinit var peerManager: PeerManager

    @Before
    fun setUp() {
        peerManager = PeerManager()
    }

    @Test
    fun testRegisterPeer_registersAndRetrievesPeer() {
        val addr = PeerAddress(screamId = "#A1B2")
        val route = PeerRoute(
            protocol = ProtocolType.SCREAM,
            transport = PeerTransport.BLE,
            address = "ble://A1B2",
            quality = ConnectionQuality.GOOD
        )

        peerManager.registerPeer(addr, alias = "Alice", avatar = "😎", route = route)

        val resolved = peerManager.resolve(addr)
        assertNotNull(resolved)
        assertEquals("Alice", resolved?.displayAlias)
        assertEquals(1, resolved?.routes?.size)
        assertEquals(ProtocolType.SCREAM, resolved?.bestProtocol)
    }

    @Test
    fun testLinkIdentities_mergesSCREAMAndBitChatRoutes() {
        val screamAddr = PeerAddress(screamId = "#A1B2")
        val bcSenderId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val bitChatAddr = PeerAddress(bitchatSenderId = bcSenderId)

        peerManager.registerPeer(
            screamAddr,
            alias = "Alice (SCREAM)",
            route = PeerRoute(ProtocolType.SCREAM, PeerTransport.TCP, "192.168.1.10")
        )
        peerManager.registerPeer(
            bitChatAddr,
            alias = "Alice (BitChat)",
            route = PeerRoute(ProtocolType.BITCHAT, PeerTransport.BLE, "ble://BC-1234")
        )

        peerManager.linkIdentities(screamAddr, bitChatAddr)

        val mergedPeer = peerManager.resolve(screamAddr)
        assertNotNull(mergedPeer)
        assertEquals(2, mergedPeer?.routes?.size)
        assertTrue(mergedPeer?.routes?.any { it.protocol == ProtocolType.SCREAM } == true)
        assertTrue(mergedPeer?.routes?.any { it.protocol == ProtocolType.BITCHAT } == true)
    }

    @Test
    fun testRemovePeer_purgesPeerFromRegistry() {
        val addr = PeerAddress(screamId = "#REMOVE")
        peerManager.registerPeer(
            addr,
            alias = "Transient",
            route = PeerRoute(ProtocolType.SCREAM, PeerTransport.TCP, "10.0.0.2")
        )

        assertNotNull(peerManager.resolve(addr))
        peerManager.removePeer(addr)
        assertNull(peerManager.resolve(addr))
    }
}
