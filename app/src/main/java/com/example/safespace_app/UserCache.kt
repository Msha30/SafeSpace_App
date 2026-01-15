package com.example.safespace_app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

object UserCache {

    private val peersMap = mutableMapOf<String, Peer>()
    private val _peersLiveData = MutableLiveData<List<Peer>>()
    val peersLiveData: LiveData<List<Peer>> get() = _peersLiveData

    private val rtdbListeners = mutableMapOf<String, ValueEventListener>()

    private var cachedSessionId: String? = null
    private var cachedPeerUid: String? = null
    private var sessionListener: ValueEventListener? = null
    private var watchingStudentUid: String? = null

    private val _sessionLiveData = MutableLiveData<Pair<String, String>?>()
    val sessionLiveData: LiveData<Pair<String, String>?> get() = _sessionLiveData

    private val sessionsCache = mutableMapOf<String, UnifiedSession>()
    private val _sessionsLiveData = MutableLiveData<List<UnifiedSession>>()
    val sessionsLiveData: LiveData<List<UnifiedSession>> get() = _sessionsLiveData

    private val userDetailsCache = mutableMapOf<String, Pair<String, String>>()
    private val userDetailsFetchTime = mutableMapOf<String, Long>()
    private val CACHE_EXPIRY_MS = 5 * 60 * 1000L

    private val availabilityCache = mutableMapOf<String, List<CachedAvailability>>()

    private val _availabilityLiveData = MutableLiveData<List<CachedAvailability>>()
    val availabilityLiveData: LiveData<List<CachedAvailability>> get() = _availabilityLiveData

    fun loadPeers() {
        val firestore = FirebaseFirestore.getInstance()
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        if (peersMap.isNotEmpty()) {
            _peersLiveData.postValue(peersMap.values.toList())
        }

        firestore.collection("account_details")
            .whereEqualTo("userType", "peer")
            .addSnapshotListener { docs, error ->
                if (error != null) {
                    Log.e("UserCache", "Firestore error", error)
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
        if (rtdbListeners.containsKey(peer.uid)) return

        val ref = rtdb.getReference("status/${peer.uid}/state")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val state = snapshot.getValue(String::class.java)
                val isOnline = state == "online"

                val existing = peersMap[peer.uid] ?: return

                if (existing.isOnline != isOnline) {
                    peersMap[peer.uid] = existing.copy(isOnline = isOnline)
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

    fun watchSession(studentUid: String) {
        if (watchingStudentUid == studentUid && sessionListener != null) {
            Log.d("UserCache", "Already watching session for $studentUid")
            return
        }

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
                                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                                val otherUserId = if (currentUserId == peerUid) studentUid else peerUid

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

    fun getActiveSession(): Pair<String, String>? {
        return if (cachedSessionId != null && cachedPeerUid != null) {
            Pair(cachedSessionId!!, cachedPeerUid!!)
        } else {
            null
        }
    }

    fun updateSession(session: UnifiedSession) {
        sessionsCache[session.sessionId] = session
        _sessionsLiveData.postValue(sessionsCache.values.sortedByDescending { it.lastMessageTime })
        Log.d("UserCache", "Session ${session.sessionId} updated in cache")
    }

    fun getCachedSession(sessionId: String): UnifiedSession? {
        return sessionsCache[sessionId]
    }

    fun getCachedSessions(): List<UnifiedSession> {
        return sessionsCache.values.sortedByDescending { it.lastMessageTime }
    }

    fun removeSession(sessionId: String) {
        sessionsCache.remove(sessionId)
        _sessionsLiveData.postValue(sessionsCache.values.sortedByDescending { it.lastMessageTime })
        Log.d("UserCache", "Session $sessionId removed from cache")
    }

    /**
     * NEW: Fetch user details with username support for students
     * If the user is a student (userType == "student"), returns their username
     * Otherwise returns their full name
     */
    fun getUserDetails(
        uid: String,
        forceRefresh: Boolean = false,
        callback: (String, String) -> Unit
    ) {
        val cachedData = userDetailsCache[uid]
        val fetchTime = userDetailsFetchTime[uid] ?: 0L
        val isCacheValid = cachedData != null &&
                (System.currentTimeMillis() - fetchTime) < CACHE_EXPIRY_MS

        if (!forceRefresh && isCacheValid && cachedData != null) {
            Log.d("UserCache", "Using cached user details for $uid")
            callback(cachedData.first, cachedData.second)
            return
        }

        Log.d("UserCache", "Fetching user details for $uid from Firestore")
        FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                // Check user type to determine what name to show
                val userType = doc.getString("userType") ?: ""

                val displayName = if (userType == "student") {
                    // For students, use username for anonymity
                    doc.getString("username") ?: "Anonymous Student"
                } else {
                    // For peers, use full name
                    val firstName = doc.getString("fname") ?: ""
                    val lastName = doc.getString("lname") ?: ""
                    "$firstName $lastName".trim().ifEmpty { "User" }
                }

                val photoUrl = doc.getString("avatarUrl") ?: ""

                // Update cache
                userDetailsCache[uid] = Pair(displayName, photoUrl)
                userDetailsFetchTime[uid] = System.currentTimeMillis()

                Log.d("UserCache", "Fetched user details for $uid: $displayName (type: $userType)")
                callback(displayName, photoUrl)
            }
            .addOnFailureListener { error ->
                Log.e("UserCache", "Failed to fetch user details for $uid", error)
                val fallback = userDetailsCache[uid]
                if (fallback != null) {
                    callback(fallback.first, fallback.second)
                } else {
                    callback("User", "")
                }
            }
    }

    fun loadPeerAvailability(uid: String) {
        availabilityCache[uid]?.let {
            _availabilityLiveData.postValue(it)
            return
        }

        val docRef = FirebaseFirestore.getInstance()
            .collection("peer_availability")
            .document(uid)

        docRef.get().addOnSuccessListener { doc ->
            val weekly: List<CachedAvailability> = if (!doc.exists() || doc.data.isNullOrEmpty()) {
                val defaultSlots = listOf(
                    TimeSlot("7:00 - 9:00"),
                    TimeSlot("9:00 - 11:00"),
                    TimeSlot("1:00 - 3:00"),
                    TimeSlot("3:00 - 5:00")
                )
                val daysOrder = listOf(
                    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
                )
                val defaultAvailability = daysOrder.map { day ->
                    CachedAvailability(day, defaultSlots.map { TimeSlot(it.label, true) })
                }
                docRef.set(defaultAvailability.associate { it.day to it.slots.map { s -> mapOf("label" to s.label, "selected" to s.selected) } })
                defaultAvailability
            } else {
                val list = mutableListOf<CachedAvailability>()
                for (day in doc.data!!.keys) {
                    val slotList = doc.get(day) as? List<Map<String, Any>> ?: emptyList()
                    list.add(
                        CachedAvailability(
                            day,
                            slotList.map { TimeSlot(it["label"]?.toString() ?: "", it["selected"] as? Boolean ?: false) }
                        )
                    )
                }
                val dayOrder = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
                list.sortedBy { dayOrder.indexOf(it.day) }
            }

            availabilityCache[uid] = weekly
            _availabilityLiveData.postValue(weekly)
        }.addOnFailureListener {
            _availabilityLiveData.postValue(emptyList())
        }
    }

    fun savePeerAvailability(uid: String, weekly: List<DayAvailability>, callback: (Boolean) -> Unit) {
        val map = weekly.associate { day ->
            day.dayName to day.slots.map {
                mapOf(
                    "label" to it.label,
                    "selected" to it.selected
                )
            }
        }

        FirebaseFirestore.getInstance()
            .collection("peer_availability")
            .document(uid)
            .set(map)
            .addOnSuccessListener {
                availabilityCache[uid] = weekly.map {
                    CachedAvailability(it.dayName, it.slots)
                }
                _availabilityLiveData.postValue(availabilityCache[uid])
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    fun clearActiveSession() {
        cachedSessionId = null
        cachedPeerUid = null
        _sessionLiveData.postValue(null)
        Log.d("UserCache", "Active session cleared")
    }

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
    private val peerSessionsCache = mutableMapOf<String, PeerSession>()
    private val _peerSessionsLiveData = MutableLiveData<List<PeerSession>>()
    val activeSessionsLiveData: LiveData<List<PeerSession>> get() = _peerSessionsLiveData
    private var peerSessionsListener: ListenerRegistration? = null

    // Replace the loadActiveSessionsForUser function in UserCache.kt with this:

    fun loadActiveSessionsForUser(peerUid: String? = null, studentUid: String? = null) {
        // Remove previous listener if exists
        peerSessionsListener?.remove()

        val firestore = FirebaseFirestore.getInstance()
        var query = firestore.collection("peer_session_requests") as com.google.firebase.firestore.Query

        when {
            peerUid != null -> query = query.whereEqualTo("peerUid", peerUid)
            studentUid != null -> query = query.whereEqualTo("studentUid", studentUid)
            else -> return // Nothing to load
        }

        peerSessionsListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("UserCache", "Failed to load sessions", error)
                return@addSnapshotListener
            }

            val activeList = mutableListOf<PeerSession>()
            snapshot?.documents?.forEach { doc ->
                val sessionId = doc.id
                val studentUidDoc = doc.getString("studentUid") ?: return@forEach
                val peerUidDoc = doc.getString("peerUid") ?: ""
                val preferredMode = doc.getString("preferredMode")?.trim() ?: ""
                val topicOfConcern = doc.getString("topicOfConcern") ?: ""
                val additionalConcern = doc.getString("additionalConcern") ?: ""
                val selectedDate = doc.getString("selectedDate") ?: ""
                val selectedTimeSlot = doc.getString("selectedTimeSlot") ?: ""
                val status = doc.getString("status") ?: "pending"
                val sessionComplete = doc.getBoolean("sessionComplete") ?: false
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val requestId = doc.getString("requestId") ?: sessionId
                val location = doc.getString("location")?.trim()

                // ✅ READ CALL STATUS FROM FIRESTORE
                val callStatus = doc.getString("callStatus")
                val callInitiatorUid = doc.getString("callInitiatorUid")

                val session = PeerSession(
                    sessionId = sessionId,
                    studentUid = studentUidDoc,
                    peerUid = peerUidDoc,
                    selectedDate = selectedDate,
                    selectedTimeSlot = selectedTimeSlot,
                    location = location,
                    status = status,
                    topicOfConcern = topicOfConcern,
                    additionalConcern = additionalConcern,
                    preferredMode = preferredMode,
                    sessionComplete = sessionComplete,
                    createdAt = createdAt,
                    requestId = requestId,
                    callStatus = callStatus,           // ✅ INCLUDE THIS
                    callInitiatorUid = callInitiatorUid // ✅ INCLUDE THIS
                )

                peerSessionsCache[sessionId] = session
                activeList.add(session)

                // Debug log
                Log.d("UserCache", "Session ${sessionId}: callStatus=$callStatus, initiator=$callInitiatorUid")
            }

            Log.d("UserCache", "Active sessions loaded: ${activeList.size}")
            _peerSessionsLiveData.postValue(activeList)
        }
    }
    fun forceRefreshUserDetails(uid: String, callback: ((String, String) -> Unit)? = null) {
        FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    callback?.invoke("User", "")
                    return@addOnSuccessListener
                }

                // Get display name: prefer username if exists, else first+last name, else fallback
                val displayName = doc.getString("username")
                    ?: "${doc.getString("fname") ?: ""} ${doc.getString("lname") ?: ""}".trim()
                        .ifEmpty { "User" }

                val photoUrl = doc.getString("avatarUrl") ?: ""

                // Update caches
                userDetailsCache[uid] = Pair(displayName, photoUrl)
                userDetailsFetchTime[uid] = System.currentTimeMillis()

                Log.d("UserCache", "Force refreshed user details for $uid: $displayName, avatar=$photoUrl")

                // Update peersMap if user exists there (so observers update)
                peersMap[uid]?.let { existing ->
                    if (existing.name != displayName || existing.photoUrl != photoUrl) {
                        peersMap[uid] = existing.copy(name = displayName, photoUrl = photoUrl)
                        _peersLiveData.postValue(peersMap.values.toList())
                    }
                }

                // Return via callback
                callback?.invoke(displayName, photoUrl)
            }
            .addOnFailureListener { e ->
                Log.e("UserCache", "Failed to force refresh user details for $uid", e)
                // fallback to cached value if exists
                userDetailsCache[uid]?.let { (name, avatar) ->
                    callback?.invoke(name, avatar)
                } ?: callback?.invoke("User", "")
            }
    }
    fun forceUpdateAllUsers() {
        userDetailsCache.keys.forEach { uid ->
            forceRefreshUserDetails(uid)
        }
    }
    fun updateSessionStatus(sessionId: String, status: String) {
        FirebaseFirestore.getInstance()
            .collection("peer_session_requests")
            .document(sessionId)
            .update("status", status)
            .addOnSuccessListener {
                Log.d("UserCache", "Peer session request $sessionId status updated to $status")
            }
            .addOnFailureListener { error ->
                Log.e("UserCache", "Failed to update peer session request $sessionId", error)
            }
    }
    fun clear() {
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        rtdbListeners.forEach { (uid, listener) ->
            rtdb.getReference("status/$uid/state").removeEventListener(listener)
        }
        rtdbListeners.clear()

        stopWatchingSession()

        peersMap.clear()
        cachedSessionId = null
        cachedPeerUid = null

        sessionsCache.clear()
        userDetailsCache.clear()
        userDetailsFetchTime.clear()

        _peersLiveData.postValue(emptyList())
        _sessionLiveData.postValue(null)
        _sessionsLiveData.postValue(emptyList())

        Log.d("UserCache", "Cache cleared")
    }
}