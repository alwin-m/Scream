package com.scream.app.security

import android.content.Context
import android.util.Log
import com.scream.app.model.VersionTrust
import org.json.JSONObject

/**
 * UpdateIntegrityEngine — Automated Update & Payload Integrity Verifier.
 *
 * Ensures that any application update payload, sideloaded build metadata, or
 * mesh patch automatically undergoes cryptographic verification.
 *
 * Rules:
 *  - Genuine Updates: Signed with official key -> ACCEPTED & applied.
 *  - Unauthorized / Modified Updates: Signed with different key or tampered -> REJECTED automatically.
 */
object UpdateIntegrityEngine {
    private const val TAG = "UpdateIntegrityEngine"

    sealed class UpdateResult {
        data class Accepted(
            val versionName: String,
            val versionCode: Int,
            val contributorTag: String,
            val message: String
        ) : UpdateResult()

        data class Rejected(
            val reason: String,
            val securityViolation: Boolean = true
        ) : UpdateResult()
    }

    /**
     * Inspect and verify an update payload transmitted over the mesh or sideloaded.
     */
    fun verifyUpdatePayload(updateJson: JSONObject, context: Context): UpdateResult {
        val signingHash = updateJson.optString("signingHash")
        val versionName = updateJson.optString("versionName", "1.0")
        val versionCode = updateJson.optInt("versionCode", 1)
        val contributorTag = updateJson.optString("contributorTag", "A")

        if (signingHash.isBlank()) {
            Log.w(TAG, "Update payload rejected: missing signing hash")
            return UpdateResult.Rejected("Missing cryptographic signing hash", securityViolation = true)
        }

        val trust = BuildIntegrity.verifyPeerFingerprint("$signingHash:$versionName:$versionCode:$contributorTag", context)

        return when (trust) {
            VersionTrust.OFFICIAL -> {
                Log.i(TAG, "Update verified authentic: v$versionName ($versionCode) by contributor $contributorTag")
                UpdateResult.Accepted(
                    versionName = versionName,
                    versionCode = versionCode,
                    contributorTag = contributorTag,
                    message = "Authentic official update verified successfully."
                )
            }
            VersionTrust.UNVERIFIED -> {
                Log.w(TAG, "Update rejected: unverified signature")
                UpdateResult.Rejected("Unverified build signature", securityViolation = false)
            }
            VersionTrust.MODIFIED -> {
                Log.e(TAG, "SECURITY VIOLATION: Unauthorized/modified build update rejected! Hash: $signingHash")
                UpdateResult.Rejected(
                    "⚠️ Security Violation: Unauthorized/modified build update rejected to prevent surveillance or unauthorized code execution.",
                    securityViolation = true
                )
            }
        }
    }

    /**
     * Build an authentic update manifest payload for broadcasting over the mesh.
     */
    fun createUpdateManifest(context: Context): JSONObject {
        val fp = BuildIntegrity.getFingerprint(context)
        return JSONObject().apply {
            put("type", "OTA_UPDATE_MANIFEST")
            put("versionName", fp.versionName)
            put("versionCode", fp.versionCode)
            put("signingHash", fp.signingHash)
            put("contributorTag", fp.contributorTag)
            put("timestamp", System.currentTimeMillis())
        }
    }
}
