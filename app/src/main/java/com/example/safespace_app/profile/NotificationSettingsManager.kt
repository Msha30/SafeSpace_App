package com.example.safespace_app.profile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.safespace_app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object NotificationSettingsManager {

    private const val CHANNEL_ID = "safespace_messages"
    private const val CHANNEL_NAME = "Messages"
    private const val TAG = "NotificationSettings"

    // Notification IDs
    private const val NOTIFICATION_ID_CHAT = 1001
    private const val NOTIFICATION_ID_GROUP = 1002
    private const val NOTIFICATION_ID_PAIRING = 1003

    data class NotificationSettings(
        val allNotifications: Boolean = true,
        val newGroupMessages: Boolean = true,
        val events: Boolean = true,
        val newMessages: Boolean = true,
        val announcements: Boolean = true,
        val counselingSessionConfirmation: Boolean = true,
        val peerSupportSessionConfirmation: Boolean = true,
        val scheduledSessionReminder: Boolean = true
    )

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    // Create notification channel (call this in Application onCreate or MainActivity)
    fun createNotificationChannel(context: Context) {
        Log.d(TAG, "📢 Creating notification channel...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "New message notifications"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)

            Log.d(TAG, "✅ Notification channel created: $CHANNEL_ID")
        } else {
            Log.d(TAG, "✅ Android version < O, no channel needed")
        }
    }

    // Save settings to Firebase
    suspend fun saveSettings(settings: NotificationSettings) {
        val userId = currentUserId

        if (userId == null) {
            Log.e(TAG, "❌ Cannot save settings - no user logged in")
            return
        }

        try {
            Log.d(TAG, "💾 Saving notification settings for user: $userId")
            Log.d(TAG, "   Settings: $settings")

            firestore.collection("notification_settings")
                .document(userId)
                .set(settings)
                .await()

            Log.d(TAG, "✅ Settings saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving settings", e)
        }
    }

    // Load settings from Firebase
    suspend fun loadSettings(): NotificationSettings {
        val userId = currentUserId

        if (userId == null) {
            Log.e(TAG, "❌ Cannot load settings - no user logged in, returning defaults")
            return NotificationSettings()
        }

        return try {
            Log.d(TAG, "📖 Loading notification settings for user: $userId")

            val doc = firestore.collection("notification_settings")
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                val settings = NotificationSettings(
                    allNotifications = doc.getBoolean("allNotifications") ?: true,
                    newGroupMessages = doc.getBoolean("newGroupMessages") ?: true,
                    events = doc.getBoolean("events") ?: true,
                    newMessages = doc.getBoolean("newMessages") ?: true,
                    announcements = doc.getBoolean("announcements") ?: true,
                    counselingSessionConfirmation = doc.getBoolean("counselingSessionConfirmation") ?: true,
                    peerSupportSessionConfirmation = doc.getBoolean("peerSupportSessionConfirmation") ?: true,
                    scheduledSessionReminder = doc.getBoolean("scheduledSessionReminder") ?: true
                )
                Log.d(TAG, "✅ Settings loaded: $settings")
                settings
            } else {
                Log.d(TAG, "⚠️ No settings found, creating defaults")
                // First time - create default settings
                val defaultSettings = NotificationSettings()
                saveSettings(defaultSettings)
                defaultSettings
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error loading settings, returning defaults", e)
            NotificationSettings()
        }
    }

    // Check if notifications are enabled for a specific type
    suspend fun areNotificationsEnabled(context: Context, type: NotificationType): Boolean {
        Log.d(TAG, "🔍 Checking if notifications enabled for type: $type")

        // First check system permission
        val hasSystemPermission = NotificationPermissionHelper.isNotificationPermissionGranted(context)
        Log.d(TAG, "   System permission granted: $hasSystemPermission")

        if (!hasSystemPermission) {
            Log.d(TAG, "❌ System permission denied - notifications disabled")
            return false
        }

        val settings = loadSettings()
        Log.d(TAG, "   All notifications enabled: ${settings.allNotifications}")

        if (!settings.allNotifications) {
            Log.d(TAG, "❌ All notifications disabled in app settings")
            return false
        }

        val typeEnabled = when (type) {
            NotificationType.NEW_MESSAGE -> settings.newMessages
            NotificationType.NEW_GROUP_MESSAGE -> settings.newGroupMessages
            NotificationType.EVENT -> settings.events
            NotificationType.ANNOUNCEMENT -> settings.announcements
            NotificationType.COUNSELING_CONFIRMATION -> settings.counselingSessionConfirmation
            NotificationType.PEER_SUPPORT_CONFIRMATION -> settings.peerSupportSessionConfirmation
            NotificationType.SESSION_REMINDER -> settings.scheduledSessionReminder
            NotificationType.PAIRING_REQUEST -> settings.newMessages // Use newMessages setting for pairing
        }

        Log.d(TAG, "   Type '$type' enabled: $typeEnabled")
        Log.d(TAG, if (typeEnabled) "✅ Notifications ENABLED" else "❌ Notifications DISABLED")

        return typeEnabled
    }

    /**
     * Show notification for 1:1 chat message
     * Works in both foreground and background
     */
    suspend fun showChatNotification(
        context: Context,
        otherUserId: String,
        otherUserName: String,
        messagePreview: String? = null
    ) {
        Log.d(TAG, "=================================================")
        Log.d(TAG, "💬 showChatNotification() called")
        Log.d(TAG, "   OtherUserId: $otherUserId")
        Log.d(TAG, "   OtherUserName: $otherUserName")
        Log.d(TAG, "   Preview: $messagePreview")
        Log.d(TAG, "=================================================")

        // Check if notifications are enabled
        val enabled = areNotificationsEnabled(context, NotificationType.NEW_MESSAGE)
        if (!enabled) {
            Log.w(TAG, "⚠️ Chat notifications disabled - skipping")
            return
        }

        showNotificationInternal(
            context = context,
            notificationId = otherUserId.hashCode(),
            title = otherUserName,
            content = messagePreview ?: "New Message",
            channelId = CHANNEL_ID
        )
    }

    /**
     * Show notification for group chat message
     * Works in both foreground and background
     */
    suspend fun showGroupChatNotification(
        context: Context,
        groupId: String,
        groupName: String,
        messagePreview: String? = null
    ) {
        Log.d(TAG, "=================================================")
        Log.d(TAG, "👥 showGroupChatNotification() called")
        Log.d(TAG, "   GroupId: $groupId")
        Log.d(TAG, "   GroupName: $groupName")
        Log.d(TAG, "   Preview: $messagePreview")
        Log.d(TAG, "=================================================")

        // Check if notifications are enabled
        val enabled = areNotificationsEnabled(context, NotificationType.NEW_GROUP_MESSAGE)
        if (!enabled) {
            Log.w(TAG, "⚠️ Group chat notifications disabled - skipping")
            return
        }

        showNotificationInternal(
            context = context,
            notificationId = groupId.hashCode(),
            title = groupName,
            content = messagePreview ?: "New Message",
            channelId = CHANNEL_ID
        )
    }

    /**
     * Show notification for pairing request (peer side)
     * Works in both foreground and background
     */
    suspend fun showPairingRequestNotification(
        context: Context,
        studentName: String,
        requestId: String
    ) {
        Log.d(TAG, "=================================================")
        Log.d(TAG, "🔔 showPairingRequestNotification() called")
        Log.d(TAG, "   StudentName: $studentName")
        Log.d(TAG, "   RequestId: $requestId")
        Log.d(TAG, "=================================================")

        // Check if notifications are enabled (using NEW_MESSAGE setting)
        val enabled = areNotificationsEnabled(context, NotificationType.PAIRING_REQUEST)
        if (!enabled) {
            Log.w(TAG, "⚠️ Pairing notifications disabled - skipping")
            return
        }

        showNotificationInternal(
            context = context,
            notificationId = NOTIFICATION_ID_PAIRING,
            title = "New Pairing Request",
            content = "$studentName wants to connect with you",
            channelId = CHANNEL_ID
        )
    }

    /**
     * Generic method to show any notification (DEPRECATED - use specific methods above)
     */
    @Deprecated("Use showChatNotification or showGroupChatNotification instead")
    suspend fun showMessageNotification(
        context: Context,
        otherUserId: String,
        otherUserName: String,
        type: NotificationType = NotificationType.NEW_MESSAGE
    ) {
        when (type) {
            NotificationType.NEW_MESSAGE -> showChatNotification(context, otherUserId, otherUserName)
            NotificationType.NEW_GROUP_MESSAGE -> showGroupChatNotification(context, otherUserId, otherUserName)
            else -> {
                Log.w(TAG, "⚠️ Unsupported notification type: $type")
            }
        }
    }

    /**
     * Internal method to actually show the notification
     */
    private fun showNotificationInternal(
        context: Context,
        notificationId: Int,
        title: String,
        content: String,
        channelId: String
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (notificationManager == null) {
                Log.e(TAG, "❌ NotificationManager is null!")
                return
            }

            Log.d(TAG, "✅ Got NotificationManager")

            // Create intent (open app when tapped)
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            Log.d(TAG, "✅ Created PendingIntent")

            // Build notification
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.logo_notif)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            Log.d(TAG, "✅ Built notification")
            Log.d(TAG, "🚀 Showing notification with ID: $notificationId")

            notificationManager.notify(notificationId, notification)

            Log.d(TAG, "✅✅✅ NOTIFICATION SHOWN SUCCESSFULLY! ✅✅✅")

        } catch (e: Exception) {
            Log.e(TAG, "❌❌❌ EXCEPTION SHOWING NOTIFICATION ❌❌❌", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
        }
    }

    enum class NotificationType {
        NEW_MESSAGE,
        NEW_GROUP_MESSAGE,
        EVENT,
        ANNOUNCEMENT,
        COUNSELING_CONFIRMATION,
        PEER_SUPPORT_CONFIRMATION,
        SESSION_REMINDER,
        PAIRING_REQUEST
    }
}