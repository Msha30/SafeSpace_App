package com.example.safespace_app.cache

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.safespace_app.Peer
import com.example.safespace_app.UnifiedSession
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.example.safespace_app.chat.ChatManager

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

    // NEW: Cache for multiple sessions with user info
    private val sessionsCache = mutableMapOf<String, UnifiedSession>() // sessionId -> UnifiedSession
    private val _sessionsLiveData = MutableLiveData<List<UnifiedSession>>()
    val sessionsLiveData: LiveData<List<UnifiedSession>> get() = _sessionsLiveData

    // NEW: Cache for user details (student/peer info)
    private val userDetailsCache = mutableMapOf<String, Pair<String, String>>() // uid -> (name, photoUrl)
    private val userDetailsFetchTime = mutableMapOf<String, Long>() // uid -> timestamp
    private val CACHE_EXPIRY_MS = 5 * 60 * 1000L // 5 minutes

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
                                // Determine the OTHER user in the session
                                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                                val otherUserId = if (currentUserId == peerUid) studentUid else peerUid

                                // Update cache
                                cachedSessionId = sessionId
                                cachedPeerUid = peerUid
                                _sessionLiveData.postValue(Pair(sessionId, otherUserId))
                                foundActive = true
                                Log.d("UserCache", "Active session updated: $sessionId with otherUser $otherUserId")
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

    // NEW: Update session in cache
    fun updateSession(session: UnifiedSession) {
        sessionsCache[session.sessionId] = session
        _sessionsLiveData.postValue(sessionsCache.values.sortedByDescending { it.lastMessageTime })
        Log.d("UserCache", "Session ${session.sessionId} updated in cache")
    }

    // NEW: Get cached session
    fun getCachedSession(sessionId: String): UnifiedSession? {
        return sessionsCache[sessionId]
    }

    // NEW: Get all cached sessions
    fun getCachedSessions(): List<UnifiedSession> {
        return sessionsCache.values.sortedByDescending { it.lastMessageTime }
    }

    // NEW: Remove session from cache
    fun removeSession(sessionId: String) {
        sessionsCache.remove(sessionId)
        _sessionsLiveData.postValue(sessionsCache.values.sortedByDescending { it.lastMessageTime })
        Log.d("UserCache", "Session $sessionId removed from cache")
    }

    // NEW: Fetch user details with caching
    fun getUserDetails(
        uid: String,
        forceRefresh: Boolean = false,
        callback: (String, String) -> Unit
    ) {
        // Check if we have valid cached data
        val cachedData = userDetailsCache[uid]
        val fetchTime = userDetailsFetchTime[uid] ?: 0L
        val isCacheValid = cachedData != null &&
                (System.currentTimeMillis() - fetchTime) < CACHE_EXPIRY_MS

        if (!forceRefresh && isCacheValid && cachedData != null) {
            Log.d("UserCache", "Using cached user details for $uid")
            callback(cachedData.first, cachedData.second)
            return
        }

        // Fetch from Firestore
        Log.d("UserCache", "Fetching user details for $uid from Firestore")
        FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("fname") ?: ""
                val lastName = doc.getString("lname") ?: ""
                val name = "$firstName $lastName".trim().ifEmpty { "User" }
                val photoUrl = doc.getString("avatarUrl") ?: ""

                // Update cache
                userDetailsCache[uid] = Pair(name, photoUrl)
                userDetailsFetchTime[uid] = System.currentTimeMillis()

                callback(name, photoUrl)
            }
            .addOnFailureListener { error ->
                Log.e("UserCache", "Failed to fetch user details for $uid", error)
                // Return cached data if available, even if expired
                val fallback = userDetailsCache[uid]
                if (fallback != null) {
                    callback(fallback.first, fallback.second)
                } else {
                    callback("User", "")
                }
            }
    }

    // NEW: Clear expired user details cache
    fun clearExpiredUserDetailsCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = userDetailsFetchTime.filter { (_, time) ->
            (currentTime - time) > CACHE_EXPIRY_MS
        }.keys

        expiredKeys.forEach { uid ->
            userDetailsCache.remove(uid)
            userDetailsFetchTime.remove(uid)
        }

        if (expiredKeys.isNotEmpty()) {
            Log.d("UserCache", "Cleared ${expiredKeys.size} expired user detail entries")
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

        // clear sessions
        stopWatchingSession()

        // clear peersMap
        peersMap.clear()
        cachedSessionId = null
        cachedPeerUid = null

        // NEW: Clear session caches
        sessionsCache.clear()
        userDetailsCache.clear()
        userDetailsFetchTime.clear()

        _peersLiveData.postValue(emptyList())
        _sessionLiveData.postValue(null)
        _sessionsLiveData.postValue(emptyList())

        Log.d("UserCache", "Cache cleared")
    }
}