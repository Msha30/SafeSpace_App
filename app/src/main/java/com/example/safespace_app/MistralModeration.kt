package com.example.safespace_app.moderation

import com.example.safespace_app.ModerationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object MistralModeration {

    private const val BACKEND_URL =
        "https://safe-space-backend.vercel.app/api/moderate"

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun moderateMessage(text: String): ModerationResponse =
        withContext(Dispatchers.IO) {
            println("Moderating text with Mistral: $text")

            val json = JSONObject().put("text", text).toString()
            val request = Request.Builder()
                .url(BACKEND_URL)
                .post(json.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                println("Moderation response: $body")

                if (!response.isSuccessful) {
                    println("Moderation failed: $body")
                    return@use ModerationResponse(
                        flagged = false,
                        categories = emptyMap<String, Boolean>(),
                        categoryScores = emptyMap<String, Double>(),
                        patternBased = false,
                        mistralUsed = false,
                        cached = false,
                        error = "HTTP ${response.code}"
                    )
                }

                val obj = JSONObject(body)

                // Check if it was pattern-based flagging
                val patternBased = obj.optBoolean("patternBased", false)
                val flagged = obj.optBoolean("flagged", false)

                val categories: Map<String, Boolean> = obj.optJSONObject("categories")?.let { o ->
                    o.keys().asSequence().associateWith { key ->
                        o.optBoolean(key, false)
                    }
                } ?: emptyMap()

                val categoryScores: Map<String, Double> = obj.optJSONObject("categoryScores")?.let { o ->
                    o.keys().asSequence().associateWith { key ->
                        o.optDouble(key, 0.0)
                    }
                } ?: emptyMap()

                ModerationResponse(
                    flagged = flagged,
                    categories = categories,
                    categoryScores = categoryScores,
                    patternBased = patternBased,
                    mistralUsed = obj.optBoolean("mistralUsed", false),
                    cached = obj.optBoolean("cached", false),
                    error = null
                )
            }
        }
}