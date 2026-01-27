package com.example.safespace_app

import android.util.Log
import com.example.safespace_app.profile.NotificationSettingsManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM_DEBUG"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any ongoing work when the service is destroyed
        serviceScope.coroutineContext.cancel()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "🔥 onMessageReceived called")
        Log.d(TAG, "From: ${message.from}")

        // Log notification payload
        message.notification?.let {
            Log.d(TAG, "Notification title: ${it.title}")
            Log.d(TAG, "Notification body: ${it.body}")
        }

        // Log data payload
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Data payload:")
            for ((key, value) in message.data) {
                Log.d(TAG, "  $key = $value")
            }
        }

        // Route notifications based on type
        val type = message.data["type"] ?: ""

        when (type) {
            "chat_message" -> handleChatMessage(message)
            "group_message" -> handleGroupMessage(message)
            "pairing_request" -> handlePairingRequest(message)
            else -> handleFallback(message)
        }
    }

    /**
     * Handle 1:1 chat message notification
     */
    private fun handleChatMessage(message: RemoteMessage) {
        val otherUserId = message.data["otherUserId"]
        val otherUserName = message.data["otherUserName"]
        val preview = message.data["preview"]

        if (otherUserId == null || otherUserName == null) {
            Log.e(TAG, "❌ Missing required data for chat_message")
            return
        }

        Log.d(TAG, "💬 Routing chat message notification")
        Log.d(TAG, "   From: $otherUserName ($otherUserId)")
        Log.d(TAG, "   Preview: $preview")

        serviceScope.launch {
            try {
                NotificationSettingsManager.showChatNotification(
                    applicationContext,
                    otherUserId,
                    otherUserName,
                    preview
                )
                Log.d(TAG, "✅ Chat notification shown")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing chat notification", e)
            }
        }
    }

    /**
     * Handle group chat message notification
     */
    private fun handleGroupMessage(message: RemoteMessage) {
        val groupId = message.data["groupId"]
        val groupName = message.data["groupName"]
        val preview = message.data["preview"]

        if (groupId == null || groupName == null) {
            Log.e(TAG, "❌ Missing required data for group_message")
            return
        }

        Log.d(TAG, "👥 Routing group message notification")
        Log.d(TAG, "   Group: $groupName ($groupId)")
        Log.d(TAG, "   Preview: $preview")

        serviceScope.launch {
            try {
                NotificationSettingsManager.showGroupChatNotification(
                    applicationContext,
                    groupId,
                    groupName,
                    preview
                )
                Log.d(TAG, "✅ Group notification shown")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing group notification", e)
            }
        }
    }

    /**
     * Handle pairing request notification (for peers)
     */
    private fun handlePairingRequest(message: RemoteMessage) {
        val studentName = message.data["studentName"]
        val requestId = message.data["requestId"]

        if (studentName == null || requestId == null) {
            Log.e(TAG, "❌ Missing required data for pairing_request")
            return
        }

        Log.d(TAG, "🔔 Routing pairing request notification")
        Log.d(TAG, "   Student: $studentName")
        Log.d(TAG, "   RequestId: $requestId")

        serviceScope.launch {
            try {
                NotificationSettingsManager.showPairingRequestNotification(
                    applicationContext,
                    studentName,
                    requestId
                )
                Log.d(TAG, "✅ Pairing request notification shown")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing pairing notification", e)
            }
        }
    }

    /**
     * Handle fallback for unknown notification types
     */
    private fun handleFallback(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "SafeSpace"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "New notification"

        Log.d(TAG, "⚠️ Fallback notification handler")
        Log.d(TAG, "   Title: $title")
        Log.d(TAG, "   Body: $body")

        serviceScope.launch {
            try {
                // Ensure channel exists, then show a generic notification
                NotificationSettingsManager.createNotificationChannel(applicationContext)
                NotificationSettingsManager.showChatNotification(
                    applicationContext,
                    otherUserId = "generic",
                    otherUserName = title,
                    messagePreview = body
                )
                Log.d(TAG, "✅ Fallback notification shown")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error showing fallback notification", e)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔥 NEW FCM TOKEN: $token")

        // Persist the token alongside the user's account so your backend
        // can send targeted notifications.
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "No authenticated user when FCM token refreshed; skipping Firestore save")
            return
        }

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("account_details")
            .document(uid)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d(TAG, "✅ FCM token saved to Firestore for uid=$uid")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save FCM token for uid=$uid", e)
            }
    }
}