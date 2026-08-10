package com.scream.app.network.transport

import com.scream.app.model.PeerTransport

/**
 * Small fan-out coordinator for transport implementations.
 *
 * It intentionally does not implement routing policy: callers decide which
 * peers should receive a message, while this class provides one place to
 * register transports and observe their inbound bytes.
 */
class TransportCoordinator {
    private val transports = linkedMapOf<PeerTransport, Transport>()
    private val listeners = mutableListOf<TransportReceiveListener>()

    fun register(transport: Transport) {
        transports[transport.type] = transport
        transport.onReceive(TransportReceiveListener { peer, payload ->
            listeners.toList().forEach { it.onReceive(peer, payload) }
        })
    }

    fun unregister(type: PeerTransport) {
        transports.remove(type)?.close()
    }

    suspend fun discoverPeers(): List<TransportPeer> = transports.values
        .flatMap { it.discoverPeers() }
        .distinctBy { it.id }

    fun onReceive(listener: TransportReceiveListener) {
        listeners += listener
    }

    fun close() {
        transports.values.forEach(Transport::close)
        transports.clear()
        listeners.clear()
    }
}
