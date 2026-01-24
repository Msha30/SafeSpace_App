package com.example.safespace_app.chat

import android.util.Log
import com.google.firebase.database.*

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = 0L,
    val isRead: Boolean = false,
    val sessionNo: Int = 1
)

class ChatManager {

    private val rtdb = FirebaseDatabase.getInstance(
        "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )

    private fun getConversationId(studentId: String, peerId: String): String {
        return "${studentId}_${peerId}"
    }

    /**
     * Initialize or create a new session
     */
    fun initializeSession(
        studentId: String,
        peerId: String,
        onComplete: (Int) -> Unit
    ) {
        val conversationId = getConversationId(studentId, peerId)
        val ref = rtdb.getReference("messages/$conversationId")

        ref.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                // First ever conversation
                val data = mapOf(
                    "studentId" to studentId,
                    "peerId" to peerId,
                    "inSession" to true,
                    "currentSessionNo" to 1,
                    "messages" to mapOf(
                        "1" to true
                    )
                )

                ref.setValue(data).addOnSuccessListener {
                    Log.d("ChatManager", "Conversation created – Session 1")
                    onComplete(1)
                }.addOnFailureListener {
                    Log.e("ChatManager", "Failed to create conversation", it)
                    onComplete(1)
                }
            } else {
                val currentSession =
                    snapshot.child("currentSessionNo").getValue(Int::class.java) ?: 0

                val newSessionNo = currentSession + 1

                val updates = mapOf(
                    "inSession" to true,
                    "currentSessionNo" to newSessionNo,
                    "messages/$newSessionNo" to true
                )

                ref.updateChildren(updates).addOnSuccessListener {
                    Log.d("ChatManager", "New session created – Session $newSessionNo")
                    onComplete(newSessionNo)
                }.addOnFailureListener {
                    Log.e("ChatManager", "Failed to create session", it)
                    onComplete(newSessionNo)
                }
            }
        }.addOnFailureListener {
            Log.e("ChatManager", "Failed to initialize session", it)
            onComplete(1)
        }
    }

    /**
     * Send message to the ACTIVE session
     */
    fun sendMessage(
        studentId: String,
        peerId: String,
        senderId: String,
        senderName: String,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (message.trim().isEmpty()) {
            onFailure("Empty message")
            return
        }

        val conversationId = getConversationId(studentId, peerId)
        val ref = rtdb.getReference("messages/$conversationId")

        ref.get().addOnSuccessListener { snapshot ->
            val inSession = snapshot.child("inSession")
                .getValue(Boolean::class.java) ?: false

            if (!inSession) {
                onFailure("Session not active")
                return@addOnSuccessListener
            }

            val currentSessionNo =
                snapshot.child("currentSessionNo").getValue(Int::class.java)
                    ?: run {
                        onFailure("No active session")
                        return@addOnSuccessListener
                    }

            val sessionRef = ref.child("messages/$currentSessionNo")
            val messageId = sessionRef.push().key

            if (messageId == null) {
                onFailure("Failed to generate message ID")
                return@addOnSuccessListener
            }

            val data = mapOf(
                "messageId" to messageId,
                "senderId" to senderId,
                "senderName" to senderName,
                "message" to message.trim(),
                "timestamp" to ServerValue.TIMESTAMP,
                "isRead" to false
            )

            sessionRef.child(messageId).setValue(data)
                .addOnSuccessListener {
                    Log.d("ChatManager", "Message sent to session $currentSessionNo")
                    onSuccess()
                }
                .addOnFailureListener {
                    Log.e("ChatManager", "Failed to send message", it)
                    onFailure(it.message ?: "Unknown error")
                }
        }.addOnFailureListener {
            Log.e("ChatManager", "Failed to send message", it)
            onFailure(it.message ?: "Unknown error")
        }
    }

    /**
     * Listen for messages
     * Students → current session only
     * Peers → all sessions
     */
    fun listenForMessages(
        studentId: String,
        peerId: String,
        currentUserId: String,
        userType: String,
        onMessagesUpdated: (List<ChatMessage>) -> Unit
    ): ValueEventListener {

        val conversationId = getConversationId(studentId, peerId)
        val ref = rtdb.getReference("messages/$conversationId")

        val listener = ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val messages = mutableListOf<ChatMessage>()

                val currentSessionNo =
                    snapshot.child("currentSessionNo").getValue(Int::class.java) ?: 1

                val messagesSnapshot = snapshot.child("messages")

                for (sessionChild in messagesSnapshot.children) {
                    val sessionNo = sessionChild.key?.toIntOrNull() ?: continue

                    if (userType == "student" && sessionNo != currentSessionNo) {
                        continue
                    }

                    for (msgChild in sessionChild.children) {
                        val senderId =
                            msgChild.child("senderId").getValue(String::class.java) ?: ""
                        val senderName =
                            msgChild.child("senderName").getValue(String::class.java) ?: ""
                        val message =
                            msgChild.child("message").getValue(String::class.java) ?: ""
                        val timestamp =
                            msgChild.child("timestamp").getValue(Long::class.java) ?: 0L
                        val isRead =
                            msgChild.child("isRead").getValue(Boolean::class.java) ?: false
                        val messageId =
                            msgChild.child("messageId").getValue(String::class.java) ?: ""

                        messages.add(
                            ChatMessage(
                                messageId = messageId,
                                senderId = senderId,
                                senderName = senderName,
                                message = message,
                                timestamp = timestamp,
                                isRead = isRead,
                                sessionNo = sessionNo
                            )
                        )
                    }
                }

                messages.sortBy { it.timestamp }
                onMessagesUpdated(messages)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("ChatManager", "Listener cancelled", error.toException())
                onMessagesUpdated(emptyList())
            }
        })

        return listener
    }

    /**
     * Mark all messages as read (across all sessions)
     */
    fun markMessagesAsRead(studentId: String, peerId: String, currentUserId: String) {
        val conversationId = getConversationId(studentId, peerId)
        val ref = rtdb.getReference("messages/$conversationId")

        ref.get().addOnSuccessListener { snapshot ->
            val updates = mutableMapOf<String, Any>()
            val messagesSnapshot = snapshot.child("messages")

            for (sessionChild in messagesSnapshot.children) {
                val sessionNo = sessionChild.key ?: continue

                for (msgChild in sessionChild.children) {
                    val senderId =
                        msgChild.child("senderId").getValue(String::class.java)
                    val isRead =
                        msgChild.child("isRead").getValue(Boolean::class.java) ?: false

                    if (senderId != currentUserId && !isRead) {
                        updates["messages/$sessionNo/${msgChild.key}/isRead"] = true
                    }
                }
            }

            if (updates.isNotEmpty()) {
                ref.updateChildren(updates)
            }
        }
    }

    /**
     * End current session
     */
    fun endSession(studentId: String, peerId: String, onComplete: () -> Unit) {
        val conversationId = getConversationId(studentId, peerId)
        val ref = rtdb.getReference("messages/$conversationId")

        ref.updateChildren(mapOf("inSession" to false))
            .addOnSuccessListener {
                Log.d("ChatManager", "Session ended")
                onComplete()
            }
            .addOnFailureListener {
                Log.e("ChatManager", "Failed to end session", it)
                onComplete()
            }
    }

    /**
     * Remove listener
     */
    fun removeListener(studentId: String, peerId: String, listener: ValueEventListener) {
        val conversationId = getConversationId(studentId, peerId)
        rtdb.getReference("messages/$conversationId")
            .removeEventListener(listener)
    }

    /**
     * Get unread message count
     */
    fun getUnreadCount(
        studentId: String,
        peerId: String,
        currentUserId: String,
        callback: (Int) -> Unit
    ) {
        val conversationId = getConversationId(studentId, peerId)
        val ref = rtdb.getReference("messages/$conversationId")

        ref.get().addOnSuccessListener { snapshot ->
            var count = 0
            val messagesSnapshot = snapshot.child("messages")

            for (sessionChild in messagesSnapshot.children) {
                for (msgChild in sessionChild.children) {
                    val senderId =
                        msgChild.child("senderId").getValue(String::class.java)
                    val isRead =
                        msgChild.child("isRead").getValue(Boolean::class.java) ?: false

                    if (senderId != currentUserId && !isRead) {
                        count++
                    }
                }
            }
            callback(count)
        }.addOnFailureListener {
            callback(0)
        }
    }
}

