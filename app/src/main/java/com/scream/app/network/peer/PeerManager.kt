package com.scream.app.network.peer

import android.util.Log
import com.scream.app.model.ConnectionQuality
import com.scream.app.model.PeerTransport
import com.scream.app.model.ProtocolType
import com.scream.app.network.protocol.DiscoveryListener
import com.scream.app.network.protocol.PeerAddress
import com.scream.app.network.protocol.PeerRoute
import com.scream.app.network.protocol.UnifiedPeer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unified peer registry that tracks all peers across all protocols and transports.
 *
 * Each peer can have multiple [PeerRoute]s — for example, a BitChat user might
 * be reachable via BLE mesh AND via Nostr relay simultaneously.
 *
 * The PeerManager also handles cross-protocol identity linking: if we discover
 * that a SCREAM user and a BitChat user are the same person (e.g. they
 * advertise both identities), we merge them into a single [UnifiedPeer].
 */
class PeerManager : DiscoveryListener {

    companion object {
        private const val TAG = "PeerManager"
        /** Peers not seen for this long are considered stale. */
        private const val STALE_TIMEOUT_MS = 15_000L
    }

    private val peers = mutableMapOf<String, UnifiedPeer>()   // key = canonical ID
    private val _peersFlow = MutableStateFlow<List<UnifiedPeer>>(emptyList())

    /** Observable list of all known peers. */
    val peersFlow: StateFlow<List<UnifiedPeer>> = _peersFlow.asStateFlow()

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Look up a peer by any of its addresses. */
    fun resolve(address: PeerAddress): UnifiedPeer? {
        return peers.values.find { it.address == address }
            ?: address.screamId?.let { id -> peers[id] }
            ?: address.nostrPubkey?.let { np -> peers.values.find { it.address.nostrPubkey == np } }
    }

    /** All peers currently tracked. */
    fun allPeers(): List<UnifiedPeer> = peers.values.toList()

    /** Only SCREAM-protocol peers. */
    fun screamPeers(): List<UnifiedPeer> =
        peers.values.filter { p -> p.routes.any { it.protocol == ProtocolType.SCREAM } }

    /** Only BitChat-protocol peers. */
    fun bitchatPeers(): List<UnifiedPeer> =
        peers.values.filter { p -> p.routes.any { it.protocol == ProtocolType.BITCHAT } }

    /** Nearby (BLE or LAN) peers. */
    fun nearbyPeers(): List<UnifiedPeer> =
        peers.values.filter { it.isNearby }

    /** Peers reachable only through relays (Nostr). */
    fun relayOnlyPeers(): List<UnifiedPeer> =
        peers.values.filter { p ->
            p.routes.all { it.transport == PeerTransport.NOSTR }
        }

    // ── Mutations ────────────────────────────────────────────────────────────

    /**
     * Register or update a peer with a given route.
     * If the peer already exists, the route is added/updated; other routes are kept.
     */
    fun registerPeer(
        address: PeerAddress,
        alias: String,
        avatar: String = "😎",
        route: PeerRoute
    ) {
        val key = canonicalKey(address)
        val existing = peers[key]
        if (existing != null) {
            val updatedRoutes = existing.routes.toMutableList()
            val idx = updatedRoutes.indexOfFirst {
                it.protocol == route.protocol && it.transport == route.transport
            }
            if (idx >= 0) updatedRoutes[idx] = route else updatedRoutes.add(route)

            peers[key] = existing.copy(
                displayAlias = alias,
                displayAvatar = avatar,
                routes = updatedRoutes,
                lastSeen = System.currentTimeMillis()
            )
        } else {
            peers[key] = UnifiedPeer(
                address = address,
                displayAlias = alias,
                displayAvatar = avatar,
                routes = listOf(route),
                lastSeen = System.currentTimeMillis()
            )
            Log.d(TAG, "New peer: $alias ($key) via ${route.protocol}/${route.transport}")
        }
        emitSnapshot()
    }

    /** Remove a peer entirely. */
    fun removePeer(address: PeerAddress) {
        val key = canonicalKey(address)
        if (peers.remove(key) != null) {
            Log.d(TAG, "Removed peer: $key")
            emitSnapshot()
        }
    }

    /**
     * Link two addresses as being the same person.
     * Routes from both entries are merged into one [UnifiedPeer].
     */
    fun linkIdentities(screamAddress: PeerAddress, bitchatAddress: PeerAddress) {
        val sKey = canonicalKey(screamAddress)
        val bKey = canonicalKey(bitchatAddress)
        val sPeer = peers[sKey]
        val bPeer = peers[bKey]

        if (sPeer == null || bPeer == null) return
        if (sKey == bKey) return   // already the same

        val merged = UnifiedPeer(
            address = PeerAddress(
                screamId = screamAddress.screamId ?: bitchatAddress.screamId,
                bitchatSenderId = bitchatAddress.bitchatSenderId ?: screamAddress.bitchatSenderId,
                nostrPubkey = screamAddress.nostrPubkey ?: bitchatAddress.nostrPubkey
            ),
            displayAlias = sPeer.displayAlias,
            displayAvatar = sPeer.displayAvatar,
            routes = (sPeer.routes + bPeer.routes).distinctBy { it.protocol to it.transport },
            lastSeen = maxOf(sPeer.lastSeen, bPeer.lastSeen)
        )

        peers.remove(bKey)
        peers[sKey] = merged
        Log.d(TAG, "Linked identities: $sKey ↔ $bKey")
        emitSnapshot()
    }

    /** Evict peers whose lastSeen timestamp is older than [STALE_TIMEOUT_MS]. */
    fun purgeStale() {
        val cutoff = System.currentTimeMillis() - STALE_TIMEOUT_MS
        val staleKeys = peers.entries.filter { it.value.lastSeen < cutoff }.map { it.key }
        if (staleKeys.isNotEmpty()) {
            staleKeys.forEach { peers.remove(it) }
            Log.d(TAG, "Purged ${staleKeys.size} stale peers")
            emitSnapshot()
        }
    }

    // ── DiscoveryListener (called by adapters) ───────────────────────────────

    override fun onPeerDiscovered(peer: UnifiedPeer) {
        val key = canonicalKey(peer.address)
        val existing = peers[key]
        if (existing != null) {
            peers[key] = existing.copy(
                displayAlias = peer.displayAlias,
                displayAvatar = peer.displayAvatar,
                routes = mergeRoutes(existing.routes, peer.routes),
                lastSeen = System.currentTimeMillis()
            )
        } else {
            peers[key] = peer.copy(lastSeen = System.currentTimeMillis())
        }
        emitSnapshot()
    }

    override fun onPeerUpdated(peer: UnifiedPeer) {
        onPeerDiscovered(peer)   // same merge logic
    }

    override fun onPeerLost(address: PeerAddress) {
        removePeer(address)
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun canonicalKey(addr: PeerAddress): String {
        return addr.screamId ?: addr.nostrPubkey ?: addr.bitchatSenderId?.let {
            "bc:${it.joinToString("") { b -> "%02x".format(b) }}"
        } ?: "unknown-${addr.hashCode()}"
    }

    private fun mergeRoutes(
        existing: List<PeerRoute>,
        incoming: List<PeerRoute>
    ): List<PeerRoute> {
        val merged = existing.toMutableList()
        for (route in incoming) {
            val idx = merged.indexOfFirst {
                it.protocol == route.protocol && it.transport == route.transport
            }
            if (idx >= 0) merged[idx] = route else merged.add(route)
        }
        return merged
    }

    private fun emitSnapshot() {
        _peersFlow.value = peers.values.toList()
    }
}
