package com.scream.app.identity

import java.security.MessageDigest

object SecurityUtils {

    fun calculateSafetyNumber(myPubKeyHex: String?, peerPubKeyHex: String?): String? {
        if (myPubKeyHex.isNullOrBlank() || peerPubKeyHex.isNullOrBlank()) return null
        
        // Lexicographically sort so the safety number is the same for both parties
        val sortedKeys = listOf(myPubKeyHex, peerPubKeyHex).sorted()
        val combined = sortedKeys.joinToString("")
        
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray(Charsets.UTF_8))
        
        // Take first 6 bytes (12 hex chars) and format as XXXX-XXXX-XXXX
        return hash.take(6)
            .toByteArray()
            .toHex()
            .uppercase()
            .chunked(4)
            .joinToString("-")
    }

    fun deriveEcdhSharedSecret(myPrivateKey: ByteArray, peerPublicKey: ByteArray): ByteArray? {
        return try {
            val kf = java.security.KeyFactory.getInstance("XDH")
            val privateKeySpec = java.security.spec.PKCS8EncodedKeySpec(myPrivateKey)
            val privateKey = kf.generatePrivate(privateKeySpec)

            val publicKeySpec = java.security.spec.X509EncodedKeySpec(peerPublicKey)
            val publicKey = kf.generatePublic(publicKeySpec)

            val keyAgreement = javax.crypto.KeyAgreement.getInstance("XDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(publicKey, true)

            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(keyAgreement.generateSecret())
        } catch (e: Exception) {
            android.util.Log.e("SecurityUtils", "ECDH failed (API < 33 or invalid key bytes), falling back to DEV key: ${e.message}")
            null
        }
    }

    fun getPublicKeyFingerprint(pubKeyHex: String?): String? {
        if (pubKeyHex.isNullOrBlank()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pubKeyHex.toByteArray(Charsets.UTF_8))
        return hash.take(6).toByteArray().toHex().uppercase().chunked(4).joinToString("-")
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
