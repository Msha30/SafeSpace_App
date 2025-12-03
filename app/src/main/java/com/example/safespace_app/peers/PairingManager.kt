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

    /**
     * Attempts to pair student with a random online peer from UserCache.
     * UI must be handled externally through callbacks.
     *
     * @param studentUid current student user ID
     * @param onPaired callback when session is successfully created
     * @param onFailure callback when pairing fails (no peers / network error)
     */
    fun pairStudent(
        studentUid: String,
        onPaired: (sessionId: String, peerUid: String) -> Unit,
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

        // Create unique session ID
        val sessionId = sessionsRef.push().key
        if (sessionId == null) {
            Log.e("PairingManager", "Failed to generate session ID")
            onFailure()
            return
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
                // Start watching this session
                UserCache.watchSession(studentUid)
                onPaired(sessionId, peerUid)
            }
            .addOnFailureListener { error ->
                Log.e("PairingManager", "Failed to create session", error)
                onFailure()
            }
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
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onComplete() }
    }
}