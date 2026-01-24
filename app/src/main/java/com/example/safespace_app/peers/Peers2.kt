package com.example.safespace_app.peers

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.MainNavigation2
import com.example.safespace_app.R
import com.example.safespace_app.UnifiedSession
import com.example.safespace_app.UserCache
import com.example.safespace_app.chat.ChatManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Peers2 : Fragment() {
    private val lastMessageListeners = mutableMapOf<String, ValueEventListener>()
    private lateinit var adapter: UnifiedSessionAdapter
    private val sessionsList = mutableListOf<UnifiedSession>()
    private val pairingManager = PairingManager()
    private val chatManager = ChatManager()
    private val peerUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Store studentId and peerId for each session
    private val sessionRoles = mutableMapOf<String, Pair<String, String>>() // sessionId -> (studentId, peerId)

    private var currentDialog: AlertDialog? = null
    private val shownRequests = mutableSetOf<String>()
    private var sessionsListener: ValueEventListener? = null

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyTextView: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_peers2, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewPeers)
        emptyTextView = view.findViewById(R.id.tvEmpty)

        adapter = UnifiedSessionAdapter(sessionsList) { session ->
            openChat(session.sessionId, session.userUid)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        updateEmptyView()

        return view
    }

    private fun updateEmptyView() {
        if (sessionsList.isEmpty()) {
            emptyTextView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyTextView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        UserCache.loadPeers()
        registerPairingCallback()

        // Load cached sessions first
        loadCachedSessions()

        // Then load from Firebase
        loadActiveSessions()

        // Watch cache updates
        observeCachedSessions()

        lifecycleScope.launch {
            delay(200)
            checkForPendingRequests()
        }
    }

    private fun loadCachedSessions() {
        val cached = UserCache.getCachedSessions()
        if (cached.isNotEmpty()) {
            Log.d("Peers2", "Loading ${cached.size} sessions from cache")
            sessionsList.clear()
            sessionsList.addAll(cached)
            adapter.notifyDataSetChanged()
            updateEmptyView()
        }
    }

    private fun observeCachedSessions() {
        UserCache.sessionsLiveData.observe(viewLifecycleOwner) { cachedSessions ->
            Log.d("Peers2", "Cache updated with ${cachedSessions.size} sessions")
            sessionsList.clear()
            sessionsList.addAll(cachedSessions)
            adapter.notifyDataSetChanged()
            updateEmptyView()
        }
    }

    private fun loadActiveSessions() {
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        val sessionsRef = rtdb.getReference("sessions")

        Log.d("Peers2", "Loading active sessions for peer: $peerUid")

        sessionsListener = sessionsRef.orderByChild("peer").equalTo(peerUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activeSessionIds = mutableSetOf<String>()

                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "active") {
                            val sessionId = child.key ?: continue
                            val studentUid = child.child("student").getValue(String::class.java) ?: continue

                            activeSessionIds.add(sessionId)

                            // Store roles for this session
                            sessionRoles[sessionId] = Pair(studentUid, peerUid)

                            // Check cache first
                            val cachedSession = UserCache.getCachedSession(sessionId)
                            if (cachedSession != null) {
                                // Update message listener only
                                setupMessageListener(sessionId, studentUid, cachedSession.name, cachedSession.photoUrl)
                            } else {
                                // Load from Firestore
                                loadStudentInfoAndAddSession(sessionId, studentUid)
                            }
                        }
                    }

                    // Remove sessions that are no longer active
                    val removedSessions = sessionsList.filter { it.sessionId !in activeSessionIds }
                    removedSessions.forEach { session ->
                        UserCache.removeSession(session.sessionId)

                        // Get roles for cleanup
                        val roles = sessionRoles[session.sessionId]
                        lastMessageListeners[session.sessionId]?.let { listener ->
                            if (roles != null) {
                                chatManager.removeListener(roles.first, roles.second, listener)
                            }
                            lastMessageListeners.remove(session.sessionId)
                        }
                        sessionRoles.remove(session.sessionId)
                    }

                    Log.d("Peers2", "Found ${activeSessionIds.size} active sessions")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Peers2", "Sessions listener error", error.toException())
                }
            })
    }

    private fun loadStudentInfoAndAddSession(sessionId: String, studentUid: String) {
        // Use cached user details
        UserCache.getUserDetails(studentUid) { name, photoUrl ->
            if (isAdded) {
                setupMessageListener(sessionId, studentUid, name, photoUrl)
            }
        }
    }

    private fun setupMessageListener(sessionId: String, studentUid: String, name: String, photoUrl: String) {
        // Remove old listener if exists
        val roles = sessionRoles[sessionId]
        lastMessageListeners[sessionId]?.let { listener ->
            if (roles != null) {
                chatManager.removeListener(roles.first, roles.second, listener)
            }
        }

        // Peer always sees all messages from all sessions
        val listener = chatManager.listenForMessages(
            studentId = studentUid,
            peerId = peerUid,
            currentUserId = peerUid,
            userType = "peer"
        ) { messages ->
            if (!isAdded) return@listenForMessages

            val lastMessage = messages.lastOrNull()
            val unread = messages.count { !it.isRead && it.senderId != peerUid }

            val session = UnifiedSession(
                sessionId = sessionId,
                userUid = studentUid,
                name = name,
                photoUrl = photoUrl,
                lastMessage = lastMessage?.message ?: "No messages yet",
                lastMessageTime = lastMessage?.timestamp ?: 0L,
                unreadCount = unread
            )

            // Update cache (this will trigger LiveData update)
            UserCache.updateSession(session)
        }

        // Save listener for cleanup
        lastMessageListeners[sessionId] = listener
    }

    private fun openChat(sessionId: String, studentUid: String) {
        UserCache.watchSession(studentUid)

        UserCache.sessionLiveData.observe(viewLifecycleOwner) { session ->
            if (session != null && session.first == sessionId) {
                findNavController().navigate(R.id.action_nav_peers2_to_chatMessageFragment)
            }
        }
    }

    // ------------------------------------------------------------
    // Pairing Request Handling
    // ------------------------------------------------------------
    private fun registerPairingCallback() {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Registering pairing request callback")

        activity.onPairingRequestReceived = { requestId, studentUid ->
            if (!shownRequests.contains(requestId)) {
                Log.d("Peers2", "Incoming request while visible: $requestId")
                showPairingRequestDialog(requestId, studentUid)
            } else {
                Log.d("Peers2", "Request $requestId already shown (live), ignoring")
            }
        }
    }

    private fun checkForPendingRequests() {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Checking pending stored requests")

        activity.checkPendingRequests { requestId, studentUid ->
            if (!shownRequests.contains(requestId)) {
                Log.d("Peers2", "Pending request found: $requestId → showing dialog")
                showPairingRequestDialog(requestId, studentUid)
            } else {
                Log.d("Peers2", "Pending request $requestId already shown earlier")
            }
        }
    }

    private fun unregisterPairingCallback() {
        val activity = requireActivity() as? MainNavigation2 ?: return
        activity.onPairingRequestReceived = null
        Log.d("Peers2", "Callback unregistered on fragment destroy")
    }

    private fun showPairingRequestDialog(requestId: String, studentUid: String) {
        if (!isAdded || context == null) return

        shownRequests.add(requestId)

        Log.d("Peers2", "Displaying pairing dialog for: $requestId")

        currentDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.popup_request, null)
        val nameView = dialogView.findViewById<TextView>(R.id.name)
        val photoView = dialogView.findViewById<ShapeableImageView>(R.id.photo)
        val btnYes = dialogView.findViewById<MaterialButton>(R.id.btnyes)
        val btnNo = dialogView.findViewById<MaterialButton>(R.id.btnno)

        nameView.text = "Student"
        photoView.setImageResource(R.drawable.img_placeholder)

        // Use cached user details
        UserCache.getUserDetails(studentUid) { name, photoUrl ->
            if (isAdded) {
                nameView.text = name
                if (photoUrl.isNotEmpty()) {
                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.img_placeholder)
                        .error(R.drawable.img_placeholder)
                        .into(photoView)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        btnYes.setOnClickListener {
            dialog.dismiss()
            acceptRequest(requestId, studentUid)
        }

        btnNo.setOnClickListener {
            dialog.dismiss()
            declineRequest(requestId)
        }

        try {
            currentDialog = dialog
            dialog.show()
            Log.d("Peers2", "Dialog shown")
        } catch (e: Exception) {
            Log.e("Peers2", "Dialog error", e)
        }
    }

    private fun acceptRequest(requestId: String, studentUid: String) {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Accepting request $requestId")

        activity.markRequestHandled(requestId)

        pairingManager.acceptRequest(
            requestId = requestId,
            studentUid = studentUid,
            peerUid = peerUid,
            onSuccess = { sessionId ->
                Log.d("Peers2", "Request accepted → Session: $sessionId")
                showAcceptedDialog(studentUid)
            },
            onFailure = {
                showErrorDialog("Failed to accept pairing request")
            }
        )
    }

    private fun declineRequest(requestId: String) {
        val activity = requireActivity() as? MainNavigation2 ?: return

        Log.d("Peers2", "Declining request $requestId")

        activity.markRequestHandled(requestId)

        pairingManager.declineRequest(requestId) {
            Log.d("Peers2", "Declined + removed")
        }
    }

    private fun showAcceptedDialog(studentUid: String) {
        if (!isAdded || context == null) return

        val dialogView = layoutInflater.inflate(R.layout.popup_paired, null)
        val nameView = dialogView.findViewById<TextView>(R.id.name)
        val photoView = dialogView.findViewById<ShapeableImageView>(R.id.photo)

        nameView.text = "Student"
        photoView.setImageResource(R.drawable.img_placeholder)

        // Use cached user details
        UserCache.getUserDetails(studentUid) { name, photoUrl ->
            if (isAdded) {
                nameView.text = name
                if (photoUrl.isNotEmpty()) {
                    Glide.with(this).load(photoUrl).into(photoView)
                }
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.show()

        lifecycleScope.launch {
            delay(2500)
            if (isAdded && dialog.isShowing) dialog.dismiss()
        }
    }

    private fun showErrorDialog(message: String) {
        if (!isAdded || context == null) return

        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        unregisterPairingCallback()
        currentDialog?.dismiss()
        currentDialog = null
        shownRequests.clear()

        sessionsListener?.let {
            val rtdb = FirebaseDatabase.getInstance(
                "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
            )
            rtdb.getReference("sessions").removeEventListener(it)
        }

        // Clean up message listeners with correct parameters
        lastMessageListeners.forEach { (sessionId, listener) ->
            val roles = sessionRoles[sessionId]
            if (roles != null) {
                chatManager.removeListener(roles.first, roles.second, listener)
            }
        }
        lastMessageListeners.clear()
        sessionRoles.clear()

        Log.d("Peers2", "Fragment destroyed. Callback cleared.")
    }
}