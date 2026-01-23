package com.example.safespace_app

import android.util.Log
import com.example.safespace_app.ModerationResponse
import com.example.safespace_app.moderation.MistralModeration
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages message moderation with Mistral AI
 * - Uses pattern matching for instant flagging
 * - Queues messages to backend for Mistral moderation
 * - No rate limiting needed (handled by backend queue)
 */
object ModerationManager {

    private const val TAG = "ModerationManager"

    // Cache moderation results for 10 minutes
    private const val CACHE_DURATION_MS = 10 * 60 * 1000L

    private val mutex = Mutex()

    // Cache for moderation results: text hash -> (result, timestamp)
    private val moderationCache = ConcurrentHashMap<Int, Pair<ModerationResponse, Long>>()

    /**
     * Client-side pattern matching for instant flagging
     * Returns true if message should be flagged immediately
     */
    private fun quickPatternCheck(text: String): Boolean {
        val lowerText = text.lowercase()

        val patterns = listOf(
            // Self-harm
            Regex("\\bk\\s*y\\s*s\\b"),
            Regex("\\bkill\\s+yourself\\b"),
            Regex("\\bsuicide\\b"),
            Regex("\\bself\\s*harm\\b"),
            Regex("\\bend\\s+it\\s+all\\b"),

            // Violence
            Regex("\\bi\\s+will\\s+kill\\b"),
            Regex("\\bgonna\\s+kill\\b"),
            Regex("\\bmurder\\b"),
            Regex("\\bstab\\b"),

            // Hate speech
            Regex("\\bnigger\\b"),
            Regex("\\bfaggot\\b"),
            Regex("\\btranny\\b"),
            Regex("\\bchink\\b"),
            Regex("\\bkike\\b"),

            // Severe harassment
            Regex("\\bfuck\\s+you\\b"),
            Regex("\\bpiece\\s+of\\s+shit\\b"),
            Regex("\\bretard(ed)?\\b"),
        )

        return patterns.any { it.containsMatchIn(lowerText) }
    }

    /**
     * Get cached moderation result if available and not expired
     */
    private fun getCachedResult(text: String): ModerationResponse? {
        val hash = text.hashCode()
        val cached = moderationCache[hash] ?: return null

        val (result, timestamp) = cached
        val age = System.currentTimeMillis() - timestamp

        if (age > CACHE_DURATION_MS) {
            moderationCache.remove(hash)
            return null
        }

        Log.d(TAG, "Using cached moderation result (age: ${age}ms)")
        return result
    }

    /**
     * Cache a moderation result
     */
    private fun cacheResult(text: String, result: ModerationResponse) {
        val hash = text.hashCode()
        moderationCache[hash] = Pair(result, System.currentTimeMillis())

        // Clean up old cache entries
        if (moderationCache.size > 200) {
            val now = System.currentTimeMillis()
            moderationCache.entries.removeIf { (_, pair) ->
                now - pair.second > CACHE_DURATION_MS
            }
        }
    }

    /**
     * Moderate a message before sending
     *
     * Flow:
     * 1. Check cache
     * 2. Run quick pattern check (instant flag)
     * 3. Send to backend for Mistral moderation (queued)
     *
     * @param text The text to moderate
     * @return ModerationResponse with flagging info
     */
    suspend fun moderateMessage(text: String): ModerationResponse {
        // Quick validation
        if (text.isBlank()) {
            return ModerationResponse(
                flagged = false,
                categories = emptyMap(),
                categoryScores = emptyMap()
            )
        }

        // Check cache first
        getCachedResult(text)?.let {
            Log.d(TAG, "Cache hit for moderation")
            return it
        }

        // Quick pattern check (client-side)
        if (quickPatternCheck(text)) {
            Log.w(TAG, "Message flagged by client-side pattern matching")
            val result = ModerationResponse(
                flagged = true,
                categories = mapOf("pattern_detected" to true),
                categoryScores = mapOf("pattern_detected" to 0.95),
                patternBased = true
            )
            cacheResult(text, result)
            return result
        }

        // Send to backend for Mistral moderation (queued)
        return try {
            Log.d(TAG, "Sending to backend for Mistral moderation")
            val result = MistralModeration.moderateMessage(text)

            // Cache the result
            cacheResult(text, result)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Moderation failed", e)

            // Fail-open: return not flagged on error
            ModerationResponse(
                flagged = false,
                categories = emptyMap(),
                categoryScores = emptyMap(),
                error = e.message
            )
        }
    }

    /**
     * Clear all caches
     * Use sparingly - mainly for testing
     */
    suspend fun reset() {
        mutex.withLock {
            moderationCache.clear()
        }
        Log.d(TAG, "ModerationManager reset")
    }
}