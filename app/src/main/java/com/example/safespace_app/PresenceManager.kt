package com.example.safespace_app

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.lang.IllegalStateException

class PresenceManager(private val auth: FirebaseAuth) {

    private val rtdb = FirebaseDatabase.getInstance(
        "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
    )
    private val uid: String = auth.currentUser?.uid
        ?: throw IllegalStateException("User must be logged in")
    private val userStatusRef = rtdb.getReference("status/$uid")
    private val connectedRef = rtdb.getReference(".info/connected")

    fun startTracking() {
        // Initialize the node if it doesn't exist
        userStatusRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    userStatusRef.setValue(
                        mapOf(
                            "state" to "offline",
                            "last_changed" to ServerValue.TIMESTAMP
                        )
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    val onlineStatus = mapOf(
                        "state" to "online",
                        "last_changed" to ServerValue.TIMESTAMP
                    )
                    userStatusRef.onDisconnect().setValue(
                        mapOf(
                            "state" to "offline",
                            "last_changed" to ServerValue.TIMESTAMP
                        )
                    )
                    userStatusRef.setValue(onlineStatus)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }


    fun setOfflineManually() {
        // Call this when user logs out
        val offlineStatus = mapOf(
            "state" to "offline",
            "last_changed" to ServerValue.TIMESTAMP
        )
        userStatusRef.setValue(offlineStatus)
    }
}
