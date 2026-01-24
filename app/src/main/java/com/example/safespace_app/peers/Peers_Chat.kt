package com.example.safespace_app.peers

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.example.safespace_app.UnifiedSession
import com.example.safespace_app.UserCache
import com.example.safespace_app.chat.ChatManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore

class Peers_Chat : Fragment() {

    private val chatManager = ChatManager()
    private val studentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: UnifiedSessionAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var guideButton: MaterialButton
    private lateinit var peerButton: MaterialButton

    private val sessionsList = mutableListOf<UnifiedSession>()
    private val lastMessageListeners = mutableMapOf<String, ValueEventListener>()

    // Store roles for cleanup
    private val sessionRoles = mutableMapOf<String, Pair<String, String>>() // sessionId -> (studentId, peerId)
    private var currentSessionId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_peers_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewStudentSession)
        guideButton = view.findViewById(R.id.guide)
        peerButton = view.findViewById(R.id.peer)

        setupRecyclerView()
        setupButtons()

        // Load from cache first
        loadCachedSession()

        // Then observe session changes
        observeSession()
    }

    private fun setupRecyclerView() {
        adapter = UnifiedSessionAdapter(sessionsList) { session ->
            openChat()
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupButtons() {
        guideButton.setOnClickListener {
            findNavController().navigate(R.id.action_peers_Chat_to_peers_Guide)
        }

        peerButton.setOnClickListener {
            findNavController().navigate(R.id.action_peers_Chat_to_peers_info)
        }
    }

    private fun loadCachedSession() {
        val cachedSession = UserCache.getActiveSession()
        if (cachedSession != null) {
            val (sessionId, peerUid) = cachedSession
            Log.d("Peers_Chat", "Loading cached session: $sessionId")

            // Store roles (student is current user, peer is the other user)
            sessionRoles[sessionId] = Pair(studentUid, peerUid)

            // Check if we have this session in cache
            val sessionData = UserCache.getCachedSession(sessionId)
            if (sessionData != null) {
                sessionsList.clear()
                sessionsList.add(sessionData)
                adapter.notifyDataSetChanged()

                // Setup message listener for updates
                setupMessageListener(sessionId, peerUid, sessionData.name, sessionData.photoUrl)
            } else {
                // Load from Firestore
                loadPeerInfoAndUpdateSession(sessionId, peerUid)
            }
        }
    }

    private fun observeSession() {
        UserCache.sessionLiveData.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                val (sessionId, peerUid) = session

                // Only reload if session changed
                if (currentSessionId != sessionId) {
                    Log.d("Peers_Chat", "Active session changed: $sessionId with peer: $peerUid")
                    currentSessionId = sessionId

                    // Store roles
                    sessionRoles[sessionId] = Pair(studentUid, peerUid)

                    // Clean up old listener
                    lastMessageListeners.forEach { (oldSessionId, listener) ->
                        val roles = sessionRoles[oldSessionId]
                        if (roles != null) {
                            chatManager.removeListener(roles.first, roles.second, listener)
                        }
                    }
                    lastMessageListeners.clear()

                    // Check cache first
                    val cachedSession = UserCache.getCachedSession(sessionId)
                    if (cachedSession != null) {
                        Log.d("Peers_Chat", "Using cached session data")
                        sessionsList.clear()
                        sessionsList.add(cachedSession)
                        adapter.notifyDataSetChanged()

                        setupMessageListener(sessionId, peerUid, cachedSession.name, cachedSession.photoUrl)
                    } else {
                        loadPeerInfoAndUpdateSession(sessionId, peerUid)
                    }
                }
            } else {
                Log.d("Peers_Chat", "No active session")

                // Clean up listeners
                lastMessageListeners.forEach { (sessionId, listener) ->
                    val roles = sessionRoles[sessionId]
                    if (roles != null) {
                        chatManager.removeListener(roles.first, roles.second, listener)
                    }
                }
                lastMessageListeners.clear()
                sessionRoles.clear()

                currentSessionId = null
                sessionsList.clear()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun loadPeerInfoAndUpdateSession(sessionId: String, peerUid: String) {
        // Use cached user details
        UserCache.getUserDetails(peerUid) { name, photoUrl ->
            if (isAdded) {
                setupMessageListener(sessionId, peerUid, name, photoUrl)
            }
        }
    }

    private fun setupMessageListener(sessionId: String, peerUid: String, name: String, photoUrl: String) {
        // Remove previous listener if exists
        val roles = sessionRoles[sessionId]
        lastMessageListeners[sessionId]?.let { listener ->
            if (roles != null) {
                chatManager.removeListener(roles.first, roles.second, listener)
            }
        }

        // Student only sees current session messages
        val listener = chatManager.listenForMessages(
            studentId = studentUid,
            peerId = peerUid,
            currentUserId = studentUid,
            userType = "student"
        ) { messages ->
            if (!isAdded) return@listenForMessages

            val lastMsg = messages.lastOrNull()
            val unread = messages.count { !it.isRead && it.senderId != studentUid }

            val updatedSession = UnifiedSession(
                sessionId = sessionId,
                userUid = peerUid,
                name = name,
                photoUrl = photoUrl,
                lastMessage = lastMsg?.message ?: "No messages yet",
                lastMessageTime = lastMsg?.timestamp ?: 0L,
                unreadCount = unread
            )

            // Update cache (this persists the data)
            UserCache.updateSession(updatedSession)

            // Update local list
            sessionsList.clear()
            sessionsList.add(updatedSession)
            adapter.notifyDataSetChanged()

            Log.d("Peers_Chat", "Session updated: unread=$unread")
        }

        lastMessageListeners[sessionId] = listener
    }

    private fun openChat() {
        try {
            findNavController().navigate(R.id.action_peers_Chat_to_chatMessageFragment2)
        } catch (e: Exception) {
            Log.e("Peers_Chat", "Navigation error", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Clean up all listeners with proper parameters
        lastMessageListeners.forEach { (sessionId, listener) ->
            val roles = sessionRoles[sessionId]
            if (roles != null) {
                chatManager.removeListener(roles.first, roles.second, listener)
            }
        }
        lastMessageListeners.clear()
        sessionRoles.clear()
        currentSessionId = null
    }
}