package com.scream.app.identity

/**
 * Represents a BitChat-compatible cryptographic identity.
 *
 * BitChat uses Ed25519 keypairs for peer identity. The 8-byte [senderId]
 * is a truncated SHA-256 hash of the public key, used in the compact binary
 * packet header to identify the sender without transmitting the full 32-byte key.
 *
 * The same Ed25519 keypair can derive a Nostr-compatible identity (Nostr uses
 * secp256k1 by default, but BitChat's Nostr envelope uses its own key format).
 */
data class BitChatIdentity(
    /** 32-byte Ed25519 public key. */
    val publicKey: ByteArray,

    /**
     * 8-byte truncated sender ID derived from SHA-256(publicKey).
     * Used in the BitChat binary packet header.
     */
    val senderId: ByteArray,

    /**
     * Hex-encoded public key for Nostr relay operations.
     * May be derived from the Ed25519 keypair or a separate secp256k1 key.
     */
    val nostrPubkeyHex: String? = null,

    /** When this identity was first created. */
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(publicKey.size == 32) { "Ed25519 public key must be 32 bytes" }
        require(senderId.size == 8) { "Sender ID must be 8 bytes" }
    }

    /** Hex representation of the sender ID for display / logging. */
    val senderIdHex: String
        get() = senderId.joinToString("") { "%02x".format(it) }

    /** Short display-friendly ID. */
    val displayId: String
        get() = "BC-${senderIdHex.take(8).uppercase()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitChatIdentity) return false
        return publicKey.contentEquals(other.publicKey)
    }

    override fun hashCode(): Int = publicKey.contentHashCode()

    override fun toString(): String = "BitChatIdentity(${displayId})"
}
