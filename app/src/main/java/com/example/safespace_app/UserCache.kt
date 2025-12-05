package com.example.safespace_app.cache

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.safespace_app.Peer
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

object UserCache {

    private val peersMap = mutableMapOf<String, Peer>() // UID -> Peer
    private val _peersLiveData = MutableLiveData<List<Peer>>()
    val peersLiveData: LiveData<List<Peer>> get() = _peersLiveData

    private val rtdbListeners = mutableMapOf<String, ValueEventListener>()

    // Session caching
    private var cachedSessionId: String? = null
    private var cachedPeerUid: String? = null
    private var sessionListener: ValueEventListener? = null
    private var watchingStudentUid: String? = null

    private val _sessionLiveData = MutableLiveData<Pair<String, String>?>()
    val sessionLiveData: LiveData<Pair<String, String>?> get() = _sessionLiveData

    fun loadPeers() {
        val firestore = FirebaseFirestore.getInstance()
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        // If cache is already loaded, post it immediately
        if (peersMap.isNotEmpty()) {
            _peersLiveData.postValue(peersMap.values.toList())
        }

        // Attach real-time Firestore listener
        firestore.collection("account_details")
            .whereEqualTo("userType", "peer")
            .addSnapshotListener { docs, error ->
                if (error != null) {
                    Log.e("UserCache", "Firestore error", error)
                    // On failure, fallback to cache
                    if (peersMap.isNotEmpty()) {
                        _peersLiveData.postValue(peersMap.values.toList())
                    }
                    return@addSnapshotListener
                }

                if (docs == null) return@addSnapshotListener

                var changed = false
                for (doc in docs) {
                    val uid = doc.id
                    val firstName = doc.getString("fname") ?: ""
                    val lastName = doc.getString("lname") ?: ""
                    val name = "$firstName $lastName"
                    val photoUrl = doc.getString("avatarUrl") ?: ""

                    val existing = peersMap[uid]
                    if (existing == null || existing.name != name || existing.photoUrl != photoUrl) {
                        val peer = Peer(uid, name, photoUrl, existing?.isOnline ?: false)
                        peersMap[uid] = peer
                        listenPresence(peer, rtdb)
                        changed = true
                    }
                }

                if (changed || peersMap.isEmpty()) {
                    _peersLiveData.postValue(peersMap.values.toList())
                }
            }
    }

    private fun listenPresence(peer: Peer, rtdb: FirebaseDatabase) {
        if (rtdbListeners.containsKey(peer.uid)) return // already listening

        val ref = rtdb.getReference("status/${peer.uid}/state")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(String::class.java)
                val isOnline = state == "online"

                val existing = peersMap[peer.uid] ?: return

                // Only update if changed
                if (existing.isOnline != isOnline) {
                    // Replace instead of mutating
                    peersMap[peer.uid] = existing.copy(isOnline = isOnline)

                    // Emit a FRESH list instance so observers trigger
                    _peersLiveData.postValue(peersMap.values.toList())
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UserCache", "Presence listener error for ${peer.uid}", error.toException())
            }
        }
        ref.addValueEventListener(listener)
        rtdbListeners[peer.uid] = listener
    }

    /**
     * Start watching a student's active session in real-time
     */
    fun watchSession(studentUid: String) {
        // Don't create duplicate listeners
        if (watchingStudentUid == studentUid && sessionListener != null) {
            Log.d("UserCache", "Already watching session for $studentUid")
            return
        }

        // Clean up old listener if watching different student
        stopWatchingSession()

        watchingStudentUid = studentUid

        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        val sessionsRef = rtdb.getReference("sessions")

        Log.d("UserCache", "Starting session watch for student: $studentUid")

        sessionListener = sessionsRef.orderByChild("student").equalTo(studentUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var foundActive = false

                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "active") {
                            val sessionId = child.key
                            val peerUid = child.child("peer").getValue(String::class.java)

                            if (sessionId != null && peerUid != null) {
                                // Update cache
                                cachedSessionId = sessionId
                                cachedPeerUid = peerUid
                                _sessionLiveData.postValue(Pair(sessionId, peerUid))
                                foundActive = true
                                Log.d("UserCache", "Active session updated: $sessionId with peer $peerUid")
                                break
                            }
                        }
                    }

                    if (!foundActive) {
                        // No active session found - session was ended or deleted
                        val wasActive = cachedSessionId != null

                        cachedSessionId = null
                        cachedPeerUid = null
                        _sessionLiveData.postValue(null)

                        if (wasActive) {
                            Log.d("UserCache", "Active session ended/deleted for student")
                        } else {
                            Log.d("UserCache", "No active session for student")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("UserCache", "Session listener error", error.toException())
                }
            })
    }

    /**
     * Stop watching the current session
     */
    fun stopWatchingSession() {
        sessionListener?.let {
            val rtdb = FirebaseDatabase.getInstance(
                "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
            )
            rtdb.getReference("sessions").removeEventListener(it)
            sessionListener = null
            watchingStudentUid = null
            Log.d("UserCache", "Stopped watching session")
        }
    }

    /**
     * Get cached session without triggering network call
     */
    fun getActiveSession(): Pair<String, String>? {
        return if (cachedSessionId != null && cachedPeerUid != null) {
            Pair(cachedSessionId!!, cachedPeerUid!!)
        } else {
            null
        }
    }

    fun clear() {
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        // Clear presence listeners
        rtdbListeners.forEach { (uid, listener) ->
            rtdb.getReference("status/$uid/state").removeEventListener(listener)
        }
        rtdbListeners.clear()

        // Clear session listener
        stopWatchingSession()

        // Clear caches
        peersMap.clear()
        cachedSessionId = null
        cachedPeerUid = null

        _peersLiveData.postValue(emptyList())
        _sessionLiveData.postValue(null)

        Log.d("UserCache", "Cache cleared")
    }
}