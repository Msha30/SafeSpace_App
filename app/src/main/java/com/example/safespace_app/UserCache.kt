package com.example.safespace_app.cache

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.safespace_app.Peer
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore

object UserCache {

    private val peersMap = mutableMapOf<String, Peer>() // UID -> Peer
    private val _peersLiveData = MutableLiveData<List<Peer>>()
    val peersLiveData: LiveData<List<Peer>> get() = _peersLiveData

    private val rtdbListeners = mutableMapOf<String, ValueEventListener>()

    fun loadPeers() {
        val firestore = FirebaseFirestore.getInstance() // local instance
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )

        // 1️⃣ If cache is already loaded, post it immediately
        if (peersMap.isNotEmpty()) {
            _peersLiveData.postValue(peersMap.values.toList())
        }

        // 2️⃣ Attach real-time Firestore listener
        firestore.collection("account_details")
            .whereEqualTo("userType", "peer")
            .addSnapshotListener { docs, error ->
                if (error != null) {
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
                        listenPresence(peer, rtdb) // pass local rtdb instance
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


            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        rtdbListeners[peer.uid] = listener
    }

    fun clear() {
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        rtdbListeners.forEach { (uid, listener) ->
            rtdb.getReference("status/$uid/state").removeEventListener(listener)
        }
        rtdbListeners.clear()
        peersMap.clear()
        _peersLiveData.postValue(emptyList())
    }

}
