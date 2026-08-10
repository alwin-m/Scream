package com.scream.app.network.transport

import com.scream.app.model.PeerTransport

/**
 * A device-to-device link used by the mesh.
 *
 * Transport implementations only move opaque bytes. Message envelopes,
 * encryption, deduplication, routing, and persistence belong above this
 * boundary so Android and future desktop transports can share those rules.
 */
interface Transport {
    /** The physical or logical link exposed by this implementation. */
    val type: PeerTransport

    /** Start or continue peer discovery and return the peers currently known. */
    suspend fun discoverPeers(): List<TransportPeer>

    /** Establish a direct link to [peer], when the transport requires one. */
    suspend fun connect(peer: TransportPeer): Boolean

    /** Send an opaque, already-encoded mesh envelope to [peer]. */
    suspend fun send(peer: TransportPeer, payload: ByteArray): Boolean

    /** Register a callback for an opaque payload received from a peer. */
    fun onReceive(listener: TransportReceiveListener)

    /** Release sockets, callbacks, and platform resources owned by the transport. */
    fun close()
}

/** A peer address understood by one transport implementation. */
data class TransportPeer(
    val id: String,
    val address: String,
    val displayName: String? = null,
    val signalStrength: Int? = null
)

fun interface TransportReceiveListener {
    fun onReceive(peer: TransportPeer, payload: ByteArray)
}
