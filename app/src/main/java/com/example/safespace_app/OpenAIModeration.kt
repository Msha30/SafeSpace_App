// OpenAIModeration.kt
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

object OpenAIModeration {

    private const val OPENAI_MODERATIONS_URL =
        "https://safe-space-backend.vercel.app/api/moderate"

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun moderateMessage(text: String): ModerationResponse =
        withContext(Dispatchers.IO) {
            println("Moderating text: $text")

            val json = JSONObject().put("text", text).toString()
            val request = Request.Builder()
                .url(OPENAI_MODERATIONS_URL)
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
                        categories = emptyMap(),
                        categoryScores = emptyMap()
                    )
                }

                val obj = JSONObject(body)
                val flagged = obj.optBoolean("flagged", false)
                val categories = obj.optJSONObject("categories")?.let { o ->
                    o.keys().asSequence().associateWith { o.getBoolean(it) }
                } ?: emptyMap()

                val categoryScores = (obj.optJSONObject("categoryScores") ?: obj.optJSONObject("category_scores"))?.let { o ->
                    o.keys().asSequence().associateWith { o.getDouble(it) }
                } ?: emptyMap()

                ModerationResponse(
                    flagged = flagged,
                    categories = categories,
                    categoryScores = categoryScores
                )
            }
        }
}
