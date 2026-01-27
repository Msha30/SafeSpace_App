package com.example.safespace_app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper class to send notifications via Vercel backend
 * This triggers FCM notifications on recipient devices
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    // TODO: Replace with your actual Vercel endpoint
    private const val VERCEL_ENDPOINT = "https://safe-space-backend.vercel.app/api/sendChatNotification.js"

    /**
     * Send 1:1 chat notification
     */
    suspend fun sendChatNotification(
        recipientUid: String,
        senderUid: String,
        senderName: String,
        messagePreview: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("type", "chat_message")
                    put("recipientUid", recipientUid)
                    put("senderUid", senderUid)
                    put("senderName", senderName)
                    put("preview", messagePreview)
                }

                Log.d(TAG, "🔔 Sending chat notification: $payload")

                val result = sendRequest(payload)

                if (result) {
                    Log.d(TAG, "✅ Chat notification sent successfully")
                } else {
                    Log.e(TAG, "❌ Failed to send chat notification")
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sending chat notification", e)
                false
            }
        }
    }

    /**
     * Send group chat notification to all members
     */
    suspend fun sendGroupNotification(
        groupId: String,
        groupName: String,
        senderUid: String,
        senderName: String,
        messagePreview: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("type", "group_message")
                    put("groupId", groupId)
                    put("groupName", groupName)
                    put("senderUid", senderUid)
                    put("senderName", senderName)
                    put("preview", messagePreview)
                }

                Log.d(TAG, "🔔 Sending group notification: $payload")

                val result = sendRequest(payload)

                if (result) {
                    Log.d(TAG, "✅ Group notification sent successfully")
                } else {
                    Log.e(TAG, "❌ Failed to send group notification")
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sending group notification", e)
                false
            }
        }
    }

    /**
     * Send pairing request notification to peer
     */
    suspend fun sendPairingRequestNotification(
        peerUid: String,
        studentName: String,
        requestId: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val payload = JSONObject().apply {
                    put("type", "pairing_request")
                    put("recipientUid", peerUid)
                    put("senderName", studentName)
                    put("requestId", requestId)
                }

                Log.d(TAG, "🔔 Sending pairing request notification: $payload")

                val result = sendRequest(payload)

                if (result) {
                    Log.d(TAG, "✅ Pairing notification sent successfully")
                } else {
                    Log.e(TAG, "❌ Failed to send pairing notification")
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception sending pairing notification", e)
                false
            }
        }
    }

    /**
     * Internal method to send HTTP POST request to Vercel
     */
    private fun sendRequest(payload: JSONObject): Boolean {
        var connection: HttpURLConnection? = null

        try {
            val url = URL(VERCEL_ENDPOINT)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            // Write payload
            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            // Check response
            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "✅ Response: $response")
                return true
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "❌ Error response ($responseCode): $errorResponse")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ HTTP request failed", e)
            return false
        } finally {
            connection?.disconnect()
        }
    }
}