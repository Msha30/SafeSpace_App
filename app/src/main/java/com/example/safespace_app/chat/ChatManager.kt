package com.example.safespace_app.chat

import android.util.Log
import com.google.firebase.database.*
import java.util.Date

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false
)

class ChatManager {

    private val rtdb = FirebaseDatabase.getInstance(
        "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )

    /**
     * Send a message in a session
     * @param sessionId the active session ID
     * @param senderId current user's UID
     * @param senderName current user's display name
     * @param message the message text
     * @param onSuccess callback when message is sent
     * @param onFailure callback on error
     */
    fun sendMessage(
        sessionId: String,
        senderId: String,
        senderName: String,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (message.trim().isEmpty()) {
            onFailure("Cannot send empty message")
            return
        }

        val messagesRef = rtdb.getReference("messages/$sessionId")
        val messageId = messagesRef.push().key

        if (messageId == null) {
            Log.e("ChatManager", "Failed to generate message ID")
            onFailure("Failed to generate message ID")
            return
        }

        val messageData = mapOf(
            "messageId" to messageId,
            "senderId" to senderId,
            "senderName" to senderName,
            "message" to message.trim(),
            "timestamp" to ServerValue.TIMESTAMP,
            "isRead" to false
        )

        messagesRef.child(messageId).setValue(messageData)
            .addOnSuccessListener {
                Log.d("ChatManager", "Message sent successfully: $messageId")
                onSuccess()
            }
            .addOnFailureListener { error ->
                Log.e("ChatManager", "Failed to send message", error)
                onFailure(error.message ?: "Unknown error")
            }
    }

    /**
     * Listen for messages in real-time
     * @param sessionId the session to listen to
     * @param onMessagesUpdated callback with list of messages
     * @return ValueEventListener for cleanup
     */
    fun listenForMessages(
        sessionId: String,
        onMessagesUpdated: (List<ChatMessage>) -> Unit
    ): ValueEventListener {
        val messagesRef = rtdb.getReference("messages/$sessionId")

        Log.d("ChatManager", "Listening for messages in session: $sessionId")

        val listener = messagesRef.orderByChild("timestamp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val messages = mutableListOf<ChatMessage>()

                    for (child in snapshot.children) {
                        try {
                            val messageId = child.child("messageId").getValue(String::class.java) ?: ""
                            val senderId = child.child("senderId").getValue(String::class.java) ?: ""
                            val senderName = child.child("senderName").getValue(String::class.java) ?: ""
                            val message = child.child("message").getValue(String::class.java) ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false

                            messages.add(
                                ChatMessage(
                                    messageId = messageId,
                                    senderId = senderId,
                                    senderName = senderName,
                                    message = message,
                                    timestamp = timestamp,
                                    isRead = isRead
                                )
                            )
                        } catch (e: Exception) {
                            Log.e("ChatManager", "Error parsing message", e)
                        }
                    }

                    Log.d("ChatManager", "Loaded ${messages.size} messages")
                    onMessagesUpdated(messages)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ChatManager", "Message listener error", error.toException())
                    onMessagesUpdated(emptyList())
                }
            })

        return listener
    }

    /**
     * Mark messages as read
     * @param sessionId the session ID
     * @param currentUserId the user marking messages as read
     */
    fun markMessagesAsRead(sessionId: String, currentUserId: String) {
        val messagesRef = rtdb.getReference("messages/$sessionId")

        messagesRef.get().addOnSuccessListener { snapshot ->
            val updates = mutableMapOf<String, Any>()

            for (child in snapshot.children) {
                val senderId = child.child("senderId").getValue(String::class.java)
                val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false

                // Only mark as read if sent by someone else and not already read
                if (senderId != currentUserId && !isRead) {
                    updates["${child.key}/isRead"] = true
                }
            }

            if (updates.isNotEmpty()) {
                messagesRef.updateChildren(updates)
                    .addOnSuccessListener {
                        Log.d("ChatManager", "Marked ${updates.size} messages as read")
                    }
                    .addOnFailureListener { error ->
                        Log.e("ChatManager", "Failed to mark messages as read", error)
                    }
            }
        }
    }

    /**
     * Get unread message count for a session
     * @param sessionId the session ID
     * @param currentUserId the user checking for unread messages
     * @param callback returns the count
     */
    fun getUnreadCount(
        sessionId: String,
        currentUserId: String,
        callback: (Int) -> Unit
    ) {
        val messagesRef = rtdb.getReference("messages/$sessionId")

        messagesRef.orderByChild("isRead").equalTo(false)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var count = 0

                    for (child in snapshot.children) {
                        val senderId = child.child("senderId").getValue(String::class.java)
                        if (senderId != currentUserId) {
                            count++
                        }
                    }

                    callback(count)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(0)
                }
            })
    }

    /**
     * Get the last message for a session (for preview in list)
     * @param sessionId the session ID
     * @param callback returns the last message or null
     */
    fun getLastMessage(
        sessionId: String,
        callback: (ChatMessage?) -> Unit
    ): ValueEventListener {

        val messagesRef = rtdb.getReference("messages/$sessionId")

        val listener = messagesRef
            .orderByChild("timestamp")
            .limitToLast(1)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        try {
                            val messageId = child.child("messageId").getValue(String::class.java) ?: ""
                            val senderId = child.child("senderId").getValue(String::class.java) ?: ""
                            val senderName = child.child("senderName").getValue(String::class.java) ?: ""
                            val message = child.child("message").getValue(String::class.java) ?: ""
                            val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                            val isRead = child.child("isRead").getValue(Boolean::class.java) ?: false

                            callback(
                                ChatMessage(
                                    messageId = messageId,
                                    senderId = senderId,
                                    senderName = senderName,
                                    message = message,
                                    timestamp = timestamp,
                                    isRead = isRead
                                )
                            )
                            return
                        } catch (e: Exception) {
                            Log.e("ChatManager", "Error parsing last message", e)
                        }
                    }
                    callback(null)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(null)
                }
            })

        return listener
    }


    /**
     * Delete all messages in a session (when session ends)
     * @param sessionId the session ID
     */
    fun deleteSessionMessages(sessionId: String, onComplete: () -> Unit) {
        val messagesRef = rtdb.getReference("messages/$sessionId")

        messagesRef.removeValue()
            .addOnSuccessListener {
                Log.d("ChatManager", "Messages deleted for session: $sessionId")
                onComplete()
            }
            .addOnFailureListener { error ->
                Log.e("ChatManager", "Failed to delete messages", error)
                onComplete()
            }
    }

    /**
     * Remove event listener
     */
    fun removeListener(sessionId: String, listener: ValueEventListener) {
        rtdb.getReference("messages/$sessionId").removeEventListener(listener)
        Log.d("ChatManager", "Listener removed for session: $sessionId")
    }
}