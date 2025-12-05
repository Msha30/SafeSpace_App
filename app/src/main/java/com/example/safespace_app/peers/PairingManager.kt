package com.example.safespace_app.peers

import com.example.safespace_app.cache.UserCache
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import android.util.Log

class PairingManager {

    private val rtdb = FirebaseDatabase.getInstance(
        "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
    private val sessionsRef = rtdb.getReference("sessions")
    private val requestsRef = rtdb.getReference("pairing_requests")

    /**
     * Send a pairing request to a random online peer
     * @param studentUid current student user ID
     * @param onRequestSent callback when request is successfully sent with requestId and peerUid
     * @param onFailure callback when request fails (no peers / network error)
     */
    fun sendPairingRequest(
        studentUid: String,
        onRequestSent: (requestId: String, peerUid: String) -> Unit,
        onFailure: () -> Unit
    ) {
        // Get current peers from cache
        val peers = UserCache.peersLiveData.value ?: emptyList()

        Log.d("PairingManager", "Total peers in cache: ${peers.size}")

        // Only choose peers who are online and not the student
        val onlinePeers = peers.filter { it.isOnline && it.uid != studentUid }

        Log.d("PairingManager", "Online peers available: ${onlinePeers.size}")

        if (onlinePeers.isEmpty()) {
            Log.d("PairingManager", "No online peers available")
            onFailure()
            return
        }

        // Random peer selection
        val selectedPeer = onlinePeers.random()
        val peerUid = selectedPeer.uid

        Log.d("PairingManager", "Selected peer: ${selectedPeer.name} ($peerUid)")

        // Create unique request ID
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
                onRequestSent(requestId, peerUid)
            }
            .addOnFailureListener { error ->
                Log.e("PairingManager", "Failed to send request", error)
                onFailure()
            }
    }

    /**
     * Wait for a pairing request to be accepted or declined
     * Automatically times out after 30 seconds
     * @param requestId the request ID to monitor
     * @param timeoutSeconds how long to wait before timing out (default 30)
     * @param onAccepted callback when peer accepts with sessionId and peerUid
     * @param onDeclined callback when peer declines
     * @param onTimeout callback when request times out
     */
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

                    // Check if request still exists
                    if (!snapshot.exists()) {
                        Log.d("PairingManager", "Request $requestId no longer exists")
                        return
                    }

                    // Check for timeout
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed > timeoutSeconds * 1000) {
                        hasResponded = true
                        Log.d("PairingManager", "Request timeout after ${timeoutSeconds}s")

                        // Delete the expired request
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

                                // Delete the request after acceptance
                                requestsRef.child(requestId).removeValue()
                                    .addOnSuccessListener {
                                        Log.d("PairingManager", "Accepted request removed")
                                    }

                                // Find the created session
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

                            // Delete the declined request
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

        // Set up automatic timeout
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!hasResponded) {
                hasResponded = true
                Log.d("PairingManager", "Request timeout triggered")

                // Delete the expired request
                requestsRef.child(requestId).removeValue()
                    .addOnSuccessListener {
                        Log.d("PairingManager", "Timed out request removed")
                    }

                // Remove listener
                requestsRef.child(requestId).removeEventListener(listener)
                onTimeout()
            }
        }, timeoutSeconds * 1000)

        return listener
    }

    /**
     * Listen for incoming pairing requests for a peer
     * @param peerUid the peer's user ID
     * @param onRequestReceived callback when a request is received with requestId and studentUid
     */
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

    /**
     * Check for pending requests (one-time check)
     * @param peerUid the peer's user ID
     * @param onRequestFound callback when a pending request is found
     */
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

    /**
     * Accept a pairing request and create a session
     * @param requestId the request to accept
     * @param studentUid the student who sent the request
     * @param peerUid the peer accepting the request
     * @param onSuccess callback with sessionId when session is created
     * @param onFailure callback when acceptance fails
     */
    fun acceptRequest(
        requestId: String,
        studentUid: String,
        peerUid: String,
        onSuccess: (sessionId: String) -> Unit,
        onFailure: () -> Unit
    ) {
        Log.d("PairingManager", "Accepting request: $requestId")

        // Update request status first
        requestsRef.child(requestId).child("status").setValue("accepted")
            .addOnSuccessListener {
                // Create session
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
                        UserCache.watchSession(peerUid)

                        // Delete request after session creation
                        requestsRef.child(requestId).removeValue()
                            .addOnSuccessListener {
                                Log.d("PairingManager", "Request removed after session creation")
                            }

                        onSuccess(sessionId)
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

    /**
     * Decline a pairing request
     * @param requestId the request to decline
     * @param onComplete callback when decline is complete
     */
    fun declineRequest(requestId: String, onComplete: () -> Unit) {
        Log.d("PairingManager", "Declining request: $requestId")

        // Update status first, then delete
        requestsRef.child(requestId).child("status").setValue("declined")
            .addOnSuccessListener {
                // Now delete the entire request
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

    /**
     * Remove event listener
     */
    fun removeListener(listener: ValueEventListener) {
        requestsRef.removeEventListener(listener)
        Log.d("PairingManager", "Listener removed")
    }

    /**
     * Verifies if a peer is actually online by checking RTDB directly
     * Use this before attempting to pair for extra reliability
     */
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

    /**
     * Gets the active session for a student if one exists
     */
    fun getActiveSession(studentUid: String, callback: (String?, String?) -> Unit) {
        // Check cache first
        val cachedSession = UserCache.getActiveSession()
        if (cachedSession != null) {
            Log.d("PairingManager", "Returning cached session")
            callback(cachedSession.first, cachedSession.second)
            return
        }

        // Query database if not in cache
        sessionsRef.orderByChild("student").equalTo(studentUid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "active") {
                            val sessionId = child.key
                            val peerUid = child.child("peer").getValue(String::class.java)

                            // Start watching this session
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

    /**
     * Ends a session
     */
    fun endSession(sessionId: String, onComplete: () -> Unit) {
        sessionsRef.child(sessionId).child("status").setValue("ended")
            .addOnSuccessListener {
                Log.d("PairingManager", "Session ended: $sessionId")
                onComplete()
            }
            .addOnFailureListener {
                Log.e("PairingManager", "Failed to end session")
                onComplete()
            }
    }
}