package com.example.safespace_app.peers

import com.example.safespace_app.cache.UserCache
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

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
        val peers = UserCache.peersLiveData.value ?: emptyList()

        // Only choose peers who are online and not the student
        val onlinePeers = peers.filter { it.isOnline && it.uid != studentUid }

        if (onlinePeers.isEmpty()) {
            onFailure()
            return
        }

        // Random peer selection
        val selectedPeer = onlinePeers.random()
        val peerUid = selectedPeer.uid

        // Create unique session ID
        val sessionId = sessionsRef.push().key ?: return onFailure()

        val sessionData = mapOf(
            "student" to studentUid,
            "peer" to peerUid,
            "createdAt" to ServerValue.TIMESTAMP
        )

        sessionsRef.child(sessionId).setValue(sessionData)
            .addOnSuccessListener {
                onPaired(sessionId, peerUid)
            }
            .addOnFailureListener {
                onFailure()
            }
    }
}
