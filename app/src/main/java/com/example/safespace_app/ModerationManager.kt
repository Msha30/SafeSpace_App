package com.example.safespace_app.moderation

import android.util.Log
import com.example.safespace_app.ModerationResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages message moderation with rate limiting, caching, and batching
 * to prevent hitting OpenAI rate limits
 */
object ModerationManager {

    private const val TAG = "ModerationManager"

    // Rate limiting: max 3 requests per minute (conservative limit)
    private const val MAX_REQUESTS_PER_MINUTE = 3
    private const val MINUTE_IN_MILLIS = 60_000L

    // Cache moderation results for 5 minutes
    private const val CACHE_DURATION_MS = 5 * 60 * 1000L

    // Debounce delay: wait 2 seconds after user stops typing
    private const val DEBOUNCE_DELAY_MS = 2000L

    // Track request timestamps for rate limiting
    private val requestTimestamps = mutableListOf<Long>()
    private val mutex = Mutex()

    // Cache for moderation results: text hash -> (result, timestamp)
    private val moderationCache = ConcurrentHashMap<Int, Pair<ModerationResponse, Long>>()

    // Track pending debounced checks
    private val pendingChecks = ConcurrentHashMap<Int, Long>()

    /**
     * Check if we can make a request now (rate limiting)
     */
    private suspend fun canMakeRequest(): Boolean = mutex.withLock {
        val now = System.currentTimeMillis()

        // Remove timestamps older than 1 minute
        requestTimestamps.removeAll { now - it > MINUTE_IN_MILLIS }

        // Check if we're under the limit
        if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
            val oldestTimestamp = requestTimestamps.minOrNull() ?: now
            val waitTime = MINUTE_IN_MILLIS - (now - oldestTimestamp)

            Log.w(TAG, "Rate limit reached. Need to wait ${waitTime}ms")
            return@withLock false
        }

        return@withLock true
    }

    /**
     * Record that we made a request
     */
    private suspend fun recordRequest() = mutex.withLock {
        requestTimestamps.add(System.currentTimeMillis())
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

        Log.d(TAG, "Using cached moderation result for text (age: ${age}ms)")
        return result
    }

    /**
     * Cache a moderation result
     */
    private fun cacheResult(text: String, result: ModerationResponse) {
        val hash = text.hashCode()
        moderationCache[hash] = Pair(result, System.currentTimeMillis())

        // Clean up old cache entries
        if (moderationCache.size > 100) {
            val now = System.currentTimeMillis()
            moderationCache.entries.removeIf { (_, pair) ->
                now - pair.second > CACHE_DURATION_MS
            }
        }
    }

    /**
     * Moderate a message with debouncing, rate limiting, and caching
     *
     * @param text The text to moderate
     * @param skipDebounce If true, skip debouncing (for final send)
     * @return ModerationResponse or null if rate limited
     */
    suspend fun moderateMessage(
        text: String,
        skipDebounce: Boolean = false
    ): ModerationResponse? {
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

        // Apply debouncing unless skipped
        if (!skipDebounce) {
            val hash = text.hashCode()
            val now = System.currentTimeMillis()
            pendingChecks[hash] = now

            delay(DEBOUNCE_DELAY_MS)

            // Check if this is still the latest request
            if (pendingChecks[hash] != now) {
                Log.d(TAG, "Debounced check superseded")
                return null
            }

            pendingChecks.remove(hash)
        }

        // Check rate limit
        if (!canMakeRequest()) {
            Log.w(TAG, "Rate limit exceeded, skipping moderation")

            // Return safe default (not flagged) when rate limited
            return ModerationResponse(
                flagged = false,
                categories = emptyMap(),
                categoryScores = emptyMap()
            )
        }

        return try {
            recordRequest()

            Log.d(TAG, "Making moderation API call")
            val result = OpenAIModeration.moderateMessage(text)

            // Cache the result
            cacheResult(text, result)

            result
        } catch (e: Exception) {
            Log.e(TAG, "Moderation failed", e)

            // Fail-open: return not flagged on error
            ModerationResponse(
                flagged = false,
                categories = emptyMap(),
                categoryScores = emptyMap()
            )
        }
    }

    /**
     * Perform a lightweight check without hitting the API
     * Useful for real-time feedback while typing
     */
    fun quickCheck(text: String): QuickCheckResult {
        // Check cache
        getCachedResult(text)?.let {
            return QuickCheckResult(
                hasResult = true,
                isFlagged = it.flagged,
                source = "cache"
            )
        }

        // Simple client-side checks (no API call)
        val lowerText = text.lowercase()

        // Check for common inappropriate patterns
        val suspiciousPatterns = listOf(
            "kill yourself",
            "kys",
            "die",
            "suicide",
            "self harm",
            // Add more patterns as needed
        )

        val containsSuspiciousPattern = suspiciousPatterns.any {
            lowerText.contains(it)
        }

        return QuickCheckResult(
            hasResult = false,
            isFlagged = containsSuspiciousPattern,
            source = "pattern"
        )
    }

    /**
     * Get current rate limit status
     */
    suspend fun getRateLimitStatus(): RateLimitStatus = mutex.withLock {
        val now = System.currentTimeMillis()
        requestTimestamps.removeAll { now - it > MINUTE_IN_MILLIS }

        val remaining = MAX_REQUESTS_PER_MINUTE - requestTimestamps.size
        val resetTime = if (requestTimestamps.isNotEmpty()) {
            requestTimestamps.minOrNull()?.plus(MINUTE_IN_MILLIS)
        } else {
            now
        }

        RateLimitStatus(
            remaining = remaining,
            total = MAX_REQUESTS_PER_MINUTE,
            resetTimeMs = resetTime ?: now
        )
    }

    /**
     * Clear all caches and reset rate limiting
     * Use sparingly - mainly for testing
     */
    suspend fun reset() {
        mutex.withLock {
            requestTimestamps.clear()
        }
        moderationCache.clear()
        pendingChecks.clear()
        Log.d(TAG, "ModerationManager reset")
    }

    data class QuickCheckResult(
        val hasResult: Boolean,
        val isFlagged: Boolean,
        val source: String
    )

    data class RateLimitStatus(
        val remaining: Int,
        val total: Int,
        val resetTimeMs: Long
    )
}