package com.example.safespace_app.peers

import com.example.safespace_app.NotificationHelper
import com.example.safespace_app.UserCache
import com.example.safespace_app.chat.ChatManager
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PairingManager {

    private val rtdb = FirebaseDatabase.getInstance(
        "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
    private val sessionsRef = rtdb.getReference("sessions")
    private val requestsRef = rtdb.getReference("pairing_requests")
    private val chatManager = ChatManager()
    private val firestore = FirebaseFirestore.getInstance()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun sendPairingRequest(
        studentUid: String,
        onRequestSent: (requestId: String, peerUid: String) -> Unit,
        onFailure: () -> Unit
    ) {
        val peers = UserCache.peersLiveData.value ?: emptyList()
        Log.d("PairingManager", "Total peers in cache: ${peers.size}")

        val onlinePeers = peers.filter { it.isOnline && it.uid != studentUid }
        Log.d("PairingManager", "Online peers available: ${onlinePeers.size}")

        if (onlinePeers.isEmpty()) {
            Log.d("PairingManager", "No online peers available")
            onFailure()
            return
        }

        val selectedPeer = onlinePeers.random()
        val peerUid = selectedPeer.uid
        Log.d("PairingManager", "Selected peer: ${selectedPeer.name} ($peerUid)")

        val requestId = requestsRef.push().key
        if (requestId == null) {
            Log.e("PairingManager", "Failed to generate request ID")
            onFailure()
            return
        }

        val requestData = mapOf(
            "student" to studentUid,
            "peer" to peerUid,
            "status" to "pending",
            "createdAt" to ServerValue.TIMESTAMP
        )

        requestsRef.child(requestId).setValue(requestData)
            .addOnSuccessListener {
                Log.d("PairingManager", "Request sent successfully: $requestId to $peerUid")

                // Send notification to peer via backend
                scope.launch {
                    try {
                        // Get student name from Firestore
                        val studentDoc = firestore.collection("account_details")
                            .document(studentUid)
                            .get()
                            .await()

                        val studentName = studentDoc.getString("username") ?: "A student"

                        // Send notification
                        NotificationHelper.sendPairingRequestNotification(
                            peerUid = peerUid,
                            studentName = studentName,
                            requestId = requestId
                        )

                        Log.d("PairingManager", "✅ Pairing notification sent to peer")
                    } catch (e: Exception) {
                        Log.e("PairingManager", "❌ Failed to send pairing notification", e)
                        // Don't fail the pairing request if notification fails
                    }
                }

                onRequestSent(requestId, peerUid)
            }
            .addOnFailureListener { error ->
                Log.e("PairingManager", "Failed to send request", error)
                onFailure()
            }
    }

    fun waitForRequestResponse(
        requestId: String,
        timeoutSeconds: Long = 30,
        onAccepted: (sessionId: String, peerUid: String) -> Unit,
        onDeclined: () -> Unit,
        onTimeout: () -> Unit
    ): ValueEventListener {
        val startTime = System.currentTimeMillis()
        var hasResponded = false

        val listener = requestsRef.child(requestId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (hasResponded) return

                    if (!snapshot.exists()) {
                        Log.d("PairingManager", "Request $requestId no longer exists")
                        return
                    }

                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > timeoutSeconds * 1000) {
                        hasResponded = true
                        Log.d("PairingManager", "Request timeout after ${timeoutSeconds}s")

                        requestsRef.child(requestId).removeValue()
                            .addOnSuccessListener {
                                Log.d("PairingManager", "Expired request removed")
                            }
                        onTimeout()
                        return
                    }

                    val status = snapshot.child("status").getValue(String::class.java)

                    when (status) {
                        "accepted" -> {
                            hasResponded = true
                            val studentUid = snapshot.child("student").getValue(String::class.java)
                            val peerUid = snapshot.child("peer").getValue(String::class.java)

                            if (studentUid != null && peerUid != null) {
                                Log.d("PairingManager", "Request accepted by peer")

                                requestsRef.child(requestId).removeValue()
                                    .addOnSuccessListener {
                                        Log.d("PairingManager", "Accepted request removed")
                                    }

                                sessionsRef.orderByChild("student").equalTo(studentUid)
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(sessionSnapshot: DataSnapshot) {
                                            for (child in sessionSnapshot.children) {
                                                val sessionStatus = child.child("status").getValue(String::class.java)
                                                if (sessionStatus == "active") {
                                                    val sessionId = child.key
                                                    if (sessionId != null) {
                                                        UserCache.watchSession(studentUid)
                                                        onAccepted(sessionId, peerUid)
                                                        return
                                                    }
                                                }
                                            }
                                        }

                                        override fun onCancelled(error: DatabaseError) {
                                            Log.e("PairingManager", "Session query error", error.toException())
                                        }
                                    })
                            }
                        }
                        "declined" -> {
                            hasResponded = true
                            Log.d("PairingManager", "Request declined by peer")

                            requestsRef.child(requestId).removeValue()
                                .addOnSuccessListener {
                                    Log.d("PairingManager", "Declined request removed")
                                }
                            onDeclined()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    if (!hasResponded) {
                        hasResponded = true
                        Log.e("PairingManager", "Request listener error", error.toException())
                        onTimeout()
                    }
                }
            })

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!hasResponded) {
                hasResponded = true
                Log.d("PairingManager", "Request timeout triggered")

                requestsRef.child(requestId).removeValue()
                    .addOnSuccessListener {
                        Log.d("PairingManager", "Timed out request removed")
                    }

                requestsRef.child(requestId).removeEventListener(listener)
                onTimeout()
            }
        }, timeoutSeconds * 1000)

        return listener
    }

    fun listenForRequests(
        peerUid: String,
        onRequestReceived: (requestId: String, studentUid: String) -> Unit
    ): ValueEventListener {
        Log.d("PairingManager", "Starting to listen for requests for peer: $peerUid")

        val listener = requestsRef.orderByChild("peer").equalTo(peerUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "pending") {
                            val requestId = child.key
                            val studentUid = child.child("student").getValue(String::class.java)

                            if (requestId != null && studentUid != null) {
                                Log.d("PairingManager", "Pending request found: $requestId from $studentUid")
                                onRequestReceived(requestId, studentUid)
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("PairingManager", "Request listener error", error.toException())
                }
            })

        return listener
    }

    fun checkPendingRequests(
        peerUid: String,
        onRequestFound: (requestId: String, studentUid: String) -> Unit
    ) {
        Log.d("PairingManager", "Checking for pending requests for peer: $peerUid")

        requestsRef.orderByChild("peer").equalTo(peerUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "pending") {
                            val requestId = child.key
                            val studentUid = child.child("student").getValue(String::class.java)

                            if (requestId != null && studentUid != null) {
                                Log.d("PairingManager", "Found pending request: $requestId from $studentUid")
                                onRequestFound(requestId, studentUid)
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("PairingManager", "Error checking pending requests", error.toException())
                }
            })
    }

    fun acceptRequest(
        requestId: String,
        studentUid: String,
        peerUid: String,
        onSuccess: (sessionId: String) -> Unit,
        onFailure: () -> Unit
    ) {
        Log.d("PairingManager", "Accepting request: $requestId")

        requestsRef.child(requestId).child("status").setValue("accepted")
            .addOnSuccessListener {
                val sessionId = sessionsRef.push().key
                if (sessionId == null) {
                    Log.e("PairingManager", "Failed to generate session ID")
                    onFailure()
                    return@addOnSuccessListener
                }

                val sessionData = mapOf(
                    "student" to studentUid,
                    "peer" to peerUid,
                    "createdAt" to ServerValue.TIMESTAMP,
                    "status" to "active"
                )

                sessionsRef.child(sessionId).setValue(sessionData)
                    .addOnSuccessListener {
                        Log.d("PairingManager", "Session created successfully: $sessionId")

                        // Check if conversation exists
                        val conversationId = "${studentUid}_${peerUid}"
                        val messagesRef = rtdb.getReference("messages/$conversationId")

                        messagesRef.get().addOnSuccessListener { snapshot ->
                            if (snapshot.exists()) {
                                // Conversation exists - create new session number
                                Log.d("PairingManager", "Existing conversation found - creating new session")
                                chatManager.initializeSession(studentUid, peerUid) { sessionNo ->
                                    Log.d("PairingManager", "New session created: Session #$sessionNo")
                                    UserCache.watchSession(peerUid)

                                    requestsRef.child(requestId).removeValue()
                                        .addOnSuccessListener {
                                            Log.d("PairingManager", "Request removed after session creation")
                                        }

                                    onSuccess(sessionId)
                                }
                            } else {
                                // First time pairing - create initial conversation
                                Log.d("PairingManager", "First time pairing - creating new conversation")
                                chatManager.initializeSession(studentUid, peerUid) { sessionNo ->
                                    Log.d("PairingManager", "Initial conversation created: Session #$sessionNo")
                                    UserCache.watchSession(peerUid)

                                    requestsRef.child(requestId).removeValue()
                                        .addOnSuccessListener {
                                            Log.d("PairingManager", "Request removed after session creation")
                                        }

                                    onSuccess(sessionId)
                                }
                            }
                        }.addOnFailureListener { error ->
                            Log.e("PairingManager", "Failed to check conversation", error)
                            onFailure()
                        }
                    }
                    .addOnFailureListener { error ->
                        Log.e("PairingManager", "Failed to create session", error)
                        onFailure()
                    }
            }
            .addOnFailureListener { error ->
                Log.e("PairingManager", "Failed to accept request", error)
                onFailure()
            }
    }

    fun declineRequest(requestId: String, onComplete: () -> Unit) {
        Log.d("PairingManager", "Declining request: $requestId")

        requestsRef.child(requestId).child("status").setValue("declined")
            .addOnSuccessListener {
                requestsRef.child(requestId).removeValue()
                    .addOnSuccessListener {
                        Log.d("PairingManager", "Request declined and removed successfully")
                        onComplete()
                    }
                    .addOnFailureListener { error ->
                        Log.e("PairingManager", "Failed to remove declined request", error)
                        onComplete()
                    }
            }
            .addOnFailureListener { error ->
                Log.e("PairingManager", "Failed to decline request", error)
                onComplete()
            }
    }

    fun removeListener(listener: ValueEventListener) {
        requestsRef.removeEventListener(listener)
        Log.d("PairingManager", "Listener removed")
    }

    fun verifyPeerOnline(peerUid: String, callback: (Boolean) -> Unit) {
        rtdb.getReference("status/$peerUid/state")
            .get()
            .addOnSuccessListener { snapshot ->
                val state = snapshot.getValue(String::class.java)
                callback(state == "online")
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    fun getActiveSession(studentUid: String, callback: (String?, String?) -> Unit) {
        val cachedSession = UserCache.getActiveSession()
        if (cachedSession != null) {
            Log.d("PairingManager", "Returning cached session")
            callback(cachedSession.first, cachedSession.second)
            return
        }

        sessionsRef.orderByChild("student").equalTo(studentUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "active") {
                            val sessionId = child.key
                            val peerUid = child.child("peer").getValue(String::class.java)

                            if (sessionId != null) {
                                UserCache.watchSession(studentUid)
                            }

                            callback(sessionId, peerUid)
                            return
                        }
                    }
                    callback(null, null)
                }

                override fun onCancelled(error: DatabaseError) {
                    callback(null, null)
                }
            })
    }

    fun endSession(sessionId: String, studentId: String, peerId: String, onComplete: () -> Unit) {
        // End the Firestore session
        sessionsRef.child(sessionId).child("status").setValue("ended")
            .addOnSuccessListener {
                Log.d("PairingManager", "Firestore session ended: $sessionId")

                // End the chat session (sets inSession = false, keeps history)
                chatManager.endSession(studentId, peerId) {
                    Log.d("PairingManager", "Chat session ended, history preserved")
                    onComplete()
                }
            }
            .addOnFailureListener { error ->
                Log.e("PairingManager", "Failed to end session", error)
                // Still try to end chat session even if Firestore fails
                chatManager.endSession(studentId, peerId) {
                    onComplete()
                }
            }
    }
}