package com.example.safespace_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object NotificationSettingsManager {

    private const val CHANNEL_ID = "safespace_messages"
    private const val CHANNEL_NAME = "Messages"
    private const val TAG = "NotificationSettings"

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

    // Create notification channel (call this in Application onCreate)
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
        }

        Log.d(TAG, "   Type '$type' enabled: $typeEnabled")
        Log.d(TAG, if (typeEnabled) "✅ Notifications ENABLED" else "❌ Notifications DISABLED")

        return typeEnabled
    }

    // Show message notification
    suspend fun showMessageNotification(
        context: Context,
        otherUserId: String,
        otherUserName: String,
    ) {
        Log.d(TAG, "=================================================")
        Log.d(TAG, "🔔 showMessageNotification() called")
        Log.d(TAG, "   Context: ${context.javaClass.simpleName}")
        Log.d(TAG, "   OtherUserId: $otherUserId")
        Log.d(TAG, "   OtherUserName: $otherUserName")
        Log.d(TAG, "=================================================")

        // Check if notifications are enabled
        val enabled = areNotificationsEnabled(context, NotificationType.NEW_MESSAGE)
        if (!enabled) {
            Log.w(TAG, "⚠️ Notifications disabled - skipping notification")
            return
        }

        Log.d(TAG, "✅ Notifications enabled - proceeding to show notification")

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            if (notificationManager == null) {
                Log.e(TAG, "❌ NotificationManager is null!")
                return
            }

            Log.d(TAG, "✅ Got NotificationManager")

            // Create intent (open chat when tapped)
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                otherUserId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            Log.d(TAG, "✅ Created PendingIntent")

            // Build notification (without large icon)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.logo_notif)
                .setContentTitle(otherUserName)
                .setContentText("New Message")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            Log.d(TAG, "✅ Built notification")

            val notificationId = otherUserId.hashCode()
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
        SESSION_REMINDER
    }
}