package com.scream.app.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.scream.app.model.BuildFingerprint
import com.scream.app.model.VersionTrust
import java.security.MessageDigest

/**
 * Build integrity verifier for SCREAM.
 *
 * Each official APK is signed with a known key. When peers exchange heartbeats,
 * they include a compact fingerprint derived from their signing certificate.
 * This lets the app distinguish official builds from modified forks that may
 * contain surveillance code or altered behaviour.
 *
 * The approach is deliberately simple and local — no server, no certificate
 * pinning infrastructure. It provides a strong signal when a peer is running
 * a build signed with a different key.
 */
object BuildIntegrity {
    private const val TAG = "BuildIntegrity"

    /**
     * SHA-256 of the official SCREAM signing certificate.
     * Replace this value with the actual hash once you run [logSigningHash].
     * For debug builds, extract with:
     *   `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
     * then SHA-256 hex-encode it.
     */
    // Deliberately blank until the release signing certificate is pinned by the
    // project owner. A placeholder must never make an arbitrary APK official.
    private const val OFFICIAL_SIGNING_HASH = ""

    /** Current contributor tag embedded in every build. Change per contributor. */
    const val CONTRIBUTOR_TAG = "A"

    /** Cached fingerprint so we only compute once per process. */
    @Volatile
    private var cachedFingerprint: BuildFingerprint? = null

    /**
     * Compute the signing certificate SHA-256 for this APK.
     * Returns a hex string like "AB:CD:12:...".
     */
    fun getSigningHash(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures.isNullOrEmpty()) return "UNKNOWN"

            val certBytes = signatures[0].toByteArray()
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(certBytes)
            hash.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read signing certificate: ${e.message}")
            "UNKNOWN"
        }
    }

    /**
     * Build the full fingerprint for this installation.
     * Includes version info, signing hash, and contributor tag.
     */
    fun getFingerprint(context: Context): BuildFingerprint {
        cachedFingerprint?.let { return it }

        val versionName = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) { "1.0" }

        val versionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, 0)
                    .longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (_: Exception) { 1 }

        val fp = BuildFingerprint(
            versionName = versionName,
            versionCode = versionCode,
            signingHash = getSigningHash(context),
            contributorTag = CONTRIBUTOR_TAG
        )
        cachedFingerprint = fp
        return fp
    }

    /**
     * Get the compact fingerprint string suitable for mesh transmission.
     */
    fun getFingerprintString(context: Context): String {
        return getFingerprint(context).toCompactString()
    }

    /**
     * Determine trust level for a peer's fingerprint.
     *
     * - If the peer didn't send one (null/blank), they're [VersionTrust.UNVERIFIED].
     * - If the signing hash portion matches our official hash, they're [VersionTrust.OFFICIAL].
     * - Otherwise they're [VersionTrust.MODIFIED].
     */
    fun verifyPeerFingerprint(peerFingerprint: String?, context: Context): VersionTrust {
        if (peerFingerprint.isNullOrBlank()) return VersionTrust.UNVERIFIED

        val parts = peerFingerprint.split(":")
        // The fingerprint format is "HASH:versionName:versionCode:contributorTag"
        // The hash itself can contain ":" separators (SHA-256 hex), so we need
        // to reconstruct it. The last 3 segments are versionName, versionCode, contributorTag.
        if (parts.size < 4) return VersionTrust.UNVERIFIED

        val contributorTag = parts.last()
        val versionCode = parts[parts.size - 2]
        val versionName = parts[parts.size - 3]
        val peerHash = parts.subList(0, parts.size - 3).joinToString(":")

        val myHash = getSigningHash(context)

        return when {
            peerHash == myHash -> VersionTrust.OFFICIAL
            OFFICIAL_SIGNING_HASH.isNotBlank() && peerHash == OFFICIAL_SIGNING_HASH -> VersionTrust.OFFICIAL
            else -> VersionTrust.MODIFIED
        }
    }

    /**
     * Extract just the contributor tag from a peer fingerprint string.
     * Returns null if the fingerprint is missing or malformed.
     */
    fun extractContributorTag(peerFingerprint: String?): String? {
        if (peerFingerprint.isNullOrBlank()) return null
        val parts = peerFingerprint.split(":")
        return if (parts.size >= 4) parts.last() else null
    }

    /**
     * Log the current signing hash. Call this once during development to obtain
     * the value you should hardcode into [OFFICIAL_SIGNING_HASH].
     */
    fun logSigningHash(context: Context) {
        val hash = getSigningHash(context)
        Log.i(TAG, "Current APK signing SHA-256: $hash")
        Log.i(TAG, "Set OFFICIAL_SIGNING_HASH to this value for release builds.")
    }
}
