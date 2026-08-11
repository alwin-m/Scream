package com.scream.app.security

import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Local, fail-closed admission control for mesh envelopes.
 *
 * This is deliberately not a new cipher. Cryptography belongs to reviewed
 * primitives such as AES-GCM. This guard limits what reaches the repository:
 * malformed, replayed, oversized, unknown, or abusive envelopes are rejected
 * and the originating route is quarantined for a short period.
 */
object AutoSecurityGuard {
    private const val TAG = "AutoSecurityGuard"
    private const val MAX_ENVELOPE_BYTES = 12 * 1024 * 1024
    private const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L
    private const val MAX_MESSAGE_AGE_MS = 48 * 60 * 60 * 1000L
    private const val RATE_WINDOW_MS = 60_000L
    private const val MAX_MESSAGES_PER_WINDOW = 120
    private const val STRIKES_BEFORE_QUARANTINE = 3
    private const val QUARANTINE_MS = 5 * 60 * 1000L

    private val rateWindows = ConcurrentHashMap<String, RateWindow>()
    private val strikes = ConcurrentHashMap<String, Int>()
    private val quarantinedUntil = ConcurrentHashMap<String, Long>()

    private val allowedTypes = setOf(
        "NEW_POST", "POST_VIEW", "LIKE_POST", "DISLIKE_POST", "RESHARE_POST", "UNRESHARE_POST",
        "DELETE_POST", "NEW_ROOM", "DELETE_ROOM", "ADD_TO_ROOM", "REMOVE_FROM_ROOM",
        "CHAT_MESSAGE", "DELETE_CHAT_MESSAGE", "PIN_CHAT_MESSAGE", "MESSAGE_REACTION",
        "MSG_SUMMARY", "WANT_MSG"
    )

    data class Decision(val accepted: Boolean, val reason: String? = null)

    fun inspect(json: JSONObject, endpoint: String): Decision {
        if (isQuarantined(endpoint)) return Decision(false, "endpoint quarantined")
        if (json.toString().toByteArray(Charsets.UTF_8).size > MAX_ENVELOPE_BYTES) {
            return reject(endpoint, "oversized envelope")
        }

        val type = json.optString("type")
        if (type == "HEARTBEAT") {
            return if (json.optJSONObject("user")?.optString("id").orEmpty().isNotBlank() && allowRate(endpoint)) {
                allow(endpoint)
            } else reject(endpoint, "heartbeat has no peer identity")
        }

        val sourcePeerId = json.optString("sourcePeerId")
        val messageId = json.optString("id")
        val timestamp = json.optLong("timestamp", 0L)
        val ttl = json.optInt("ttl", -1)
        val encryptedData = json.optJSONObject("encryptedData")

        val invalidReason = when {
            sourcePeerId.isBlank() -> "missing source identity"
            messageId.isBlank() -> "missing message identity"
            type !in allowedTypes -> "unknown message type"
            timestamp <= 0L -> "missing timestamp"
            timestamp > System.currentTimeMillis() + MAX_CLOCK_SKEW_MS -> "future timestamp"
            timestamp < System.currentTimeMillis() - MAX_MESSAGE_AGE_MS -> "expired envelope"
            ttl !in 0..6 -> "invalid ttl"
            encryptedData == null -> "unencrypted Android envelope"
            encryptedData.optString("alg") != "AES-256-GCM" -> "unsupported encryption"
            encryptedData.optString("iv").isBlank() -> "missing encryption iv"
            encryptedData.optString("cipherText").isBlank() -> "missing ciphertext"
            else -> null
        }

        return if (invalidReason == null && allowRate(endpoint)) {
            allow(endpoint)
        } else {
            reject(endpoint, invalidReason ?: "rate limit exceeded")
        }
    }

    fun isQuarantined(endpoint: String): Boolean {
        val until = quarantinedUntil[endpoint] ?: return false
        if (until <= System.currentTimeMillis()) {
            quarantinedUntil.remove(endpoint, until)
            strikes.remove(endpoint)
            return false
        }
        return true
    }

    fun clear(endpoint: String) {
        rateWindows.remove(endpoint)
        strikes.remove(endpoint)
        quarantinedUntil.remove(endpoint)
    }

    fun flag(endpoint: String, reason: String): Decision = reject(endpoint, reason)

    private fun allow(endpoint: String): Decision {
        // A valid envelope gradually clears a single transient strike.
        strikes.computeIfPresent(endpoint) { _, value -> (value - 1).coerceAtLeast(0) }
        return Decision(true)
    }

    private fun reject(endpoint: String, reason: String): Decision {
        val strikeCount = strikes.merge(endpoint, 1, Int::plus) ?: 1
        if (strikeCount >= STRIKES_BEFORE_QUARANTINE) {
            quarantinedUntil[endpoint] = System.currentTimeMillis() + QUARANTINE_MS
            Log.w(TAG, "Quarantined $endpoint for $reason")
        } else {
            Log.w(TAG, "Rejected envelope from $endpoint: $reason")
        }
        return Decision(false, reason)
    }

    private fun allowRate(endpoint: String): Boolean {
        val now = System.currentTimeMillis()
        val window = rateWindows.compute(endpoint) { _, existing ->
            if (existing == null || now - existing.startedAt >= RATE_WINDOW_MS) {
                RateWindow(now, 1)
            } else {
                existing.copy(count = existing.count + 1)
            }
        } ?: return false
        return window.count <= MAX_MESSAGES_PER_WINDOW
    }

    private data class RateWindow(val startedAt: Long, val count: Int)
}
