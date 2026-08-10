package com.scream.app.identity

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.scream.app.model.User
import com.scream.app.network.protocol.PeerAddress
import com.scream.app.network.protocol.UnifiedPeer
import kotlinx.coroutines.flow.first
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages both the SCREAM identity (UUID-based, existing system) and the
 * BitChat identity (Ed25519 keypair) for the local user.
 *
 * The two identities are independent but linked: a SCREAM user who enables
 * BitChat interop gets an Ed25519 keypair generated and stored alongside
 * their existing SCREAM profile.
 *
 * ## Key storage
 *
 * For the MVP, the private key is stored as a hex-encoded string in the
 * encrypted DataStore alongside the SCREAM identity. A future upgrade should
 * move this to Android Keystore for hardware-backed protection.
 */
class IdentityManager(private val context: Context) {

    companion object {
        private const val TAG = "IdentityManager"

        // DataStore keys for BitChat identity (stored alongside SCREAM identity)
        val BITCHAT_PUBLIC_KEY = stringPreferencesKey("bitchat_public_key_hex")
        val BITCHAT_PRIVATE_KEY = stringPreferencesKey("bitchat_private_key_hex")
        val BITCHAT_SENDER_ID = stringPreferencesKey("bitchat_sender_id_hex")
    }

    // ── SCREAM identity (delegate to existing system) ────────────────────────

    private val prefsRepo = UserPreferencesRepository(context.dataStore)

    /** Read the existing SCREAM profile from DataStore. */
    suspend fun getScreamProfile(): UserProfile = prefsRepo.userProfileFlow.first()

    /**
     * Build a SCREAM [User] model from the stored profile.
     * Returns null if the user hasn't completed onboarding.
     */
    suspend fun getScreamUser(): User? {
        val profile = getScreamProfile()
        if (!profile.isRegistered) return null
        val shortId = "#${profile.uuid.take(4).uppercase()}"
        return User(
            id = shortId,
            alias = profile.alias.ifBlank { "Anonymous" },
            avatar = profile.emojiAvatar.ifBlank { "😎" },
            age = profile.age,
            gender = profile.gender
        )
    }

    // ── BitChat identity ─────────────────────────────────────────────────────

    /**
     * Get the existing BitChat identity or create a new one.
     *
     * The keypair is generated using a CSPRNG. The 8-byte sender ID is the
     * first 8 bytes of SHA-256(publicKey), matching the BitChat spec.
     */
    suspend fun getOrCreateBitChatIdentity(): BitChatIdentity {
        val existing = loadBitChatIdentity()
        if (existing != null) return existing

        Log.d(TAG, "Generating new BitChat Ed25519 identity")
        val keypair = generateEd25519Keypair()
        val publicKeyBytes = keypair.first
        val privateKeyBytes = keypair.second
        val senderId = deriveSenderId(publicKeyBytes)

        val identity = BitChatIdentity(
            publicKey = publicKeyBytes,
            senderId = senderId
        )

        // Persist
        context.dataStore.edit { prefs ->
            prefs[BITCHAT_PUBLIC_KEY] = publicKeyBytes.toHex()
            prefs[BITCHAT_PRIVATE_KEY] = privateKeyBytes.toHex()
            prefs[BITCHAT_SENDER_ID] = senderId.toHex()
        }

        Log.d(TAG, "BitChat identity created: ${identity.displayId}")
        return identity
    }

    /** Load previously stored BitChat identity, or null if none exists. */
    suspend fun loadBitChatIdentity(): BitChatIdentity? {
        val prefs = context.dataStore.data.first()
        val pubHex = prefs[BITCHAT_PUBLIC_KEY] ?: return null
        val senderHex = prefs[BITCHAT_SENDER_ID] ?: return null

        return try {
            BitChatIdentity(
                publicKey = pubHex.hexToBytes(),
                senderId = senderHex.hexToBytes()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load BitChat identity: ${e.message}")
            null
        }
    }

    /** Load the BitChat private key bytes. Only call when needed for signing/encryption. */
    suspend fun getBitChatPrivateKey(): ByteArray? {
        val prefs = context.dataStore.data.first()
        return prefs[BITCHAT_PRIVATE_KEY]?.hexToBytes()
    }

    // ── Cross-protocol identity ──────────────────────────────────────────────

    /**
     * Build a [PeerAddress] for the local user that includes both SCREAM and
     * BitChat identifiers.
     */
    suspend fun getLocalPeerAddress(): PeerAddress {
        val screamUser = getScreamUser()
        val bcIdentity = loadBitChatIdentity()
        return PeerAddress(
            screamId = screamUser?.id,
            bitchatSenderId = bcIdentity?.senderId,
            nostrPubkey = bcIdentity?.nostrPubkeyHex
        )
    }

    // ── Key generation helpers ───────────────────────────────────────────────

    /**
     * Generate an Ed25519 keypair.
     *
     * Returns (publicKey: 32 bytes, privateKey: 32 bytes seed).
     *
     * Note: Uses Java's standard `KeyPairGenerator` which is available on
     * Android API 33+. For API 26-32 we fall back to a CSPRNG-generated
     * 32-byte seed that can be used with a pure-Java Ed25519 library later.
     */
    private fun generateEd25519Keypair(): Pair<ByteArray, ByteArray> {
        return try {
            // Android API 33+ supports Ed25519 natively
            val kpg = KeyPairGenerator.getInstance("Ed25519")
            val keyPair: KeyPair = kpg.generateKeyPair()
            val pub = keyPair.public.encoded.takeLast(32).toByteArray()
            val priv = keyPair.private.encoded.takeLast(32).toByteArray()
            Pair(pub, priv)
        } catch (e: Exception) {
            // Fallback: generate 32 random bytes as seed
            // A proper Ed25519 library (e.g. BouncyCastle) will derive the
            // full keypair from this seed in Phase 2.
            Log.w(TAG, "Ed25519 KeyPairGenerator unavailable, using seed fallback: ${e.message}")
            val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val digest = MessageDigest.getInstance("SHA-512")
            val hash = digest.digest(seed)
            // Use first 32 bytes as "public key placeholder" and seed as private
            Pair(hash.take(32).toByteArray(), seed)
        }
    }

    /**
     * Derive the 8-byte sender ID from a public key.
     * Per BitChat spec: first 8 bytes of SHA-256(publicKey).
     */
    private fun deriveSenderId(publicKey: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(publicKey).take(8).toByteArray()
    }

    // ── Hex encoding utilities ───────────────────────────────────────────────

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Hex string must have even length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
