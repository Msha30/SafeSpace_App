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
import com.example.safespace_app.cache.UserCache
import com.example.safespace_app.chat.ChatManager
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener

data class StudentActiveSession(
    val sessionId: String,
    val peerUid: String,
    val peerName: String,
    val peerPhoto: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0
)

class Peers_Chat : Fragment() {

    private val chatManager = ChatManager()
    private val studentUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    private lateinit var adapter: StudentSessionAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var guideButton: MaterialButton
    private lateinit var peerButton: MaterialButton

    private val sessionsList = mutableListOf<StudentActiveSession>()

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
        observeSession()
    }

    private fun setupRecyclerView() {
        adapter = StudentSessionAdapter(sessionsList) { session ->
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

    private fun observeSession() {
        UserCache.sessionLiveData.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                val (sessionId, peerUid) = session
                Log.d("Peers_Chat", "Active session: $sessionId with peer: $peerUid")

                loadPeerInfoAndUpdateSession(sessionId, peerUid)
            } else {
                Log.d("Peers_Chat", "No active session")
                sessionsList.clear()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private val lastMessageListeners = mutableMapOf<String, ValueEventListener>()

    private fun loadPeerInfoAndUpdateSession(sessionId: String, peerUid: String) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        firestore.collection("account_details")
            .document(peerUid)
            .get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("fname") ?: ""
                val lastName = doc.getString("lname") ?: ""
                val name = "$firstName $lastName".trim().ifEmpty { "Peer" }
                val photo = doc.getString("avatarUrl") ?: ""

                // Remove previous listener if exists
                lastMessageListeners[sessionId]?.let {
                    chatManager.removeListener(sessionId, it)
                }

                // START REAL-TIME LISTENER
                val listener = chatManager.listenForMessages(sessionId) { messages ->
                    if (!isAdded) return@listenForMessages

                    val lastMsg = messages.lastOrNull()
                    val unread = messages.count { !it.isRead && it.senderId != studentUid }

                    val updatedSession = StudentActiveSession(
                        sessionId = sessionId,
                        peerUid = peerUid,
                        peerName = name,
                        peerPhoto = photo,
                        lastMessage = lastMsg?.message ?: "No messages yet",
                        lastMessageTime = lastMsg?.timestamp ?: 0L,
                        unreadCount = unread
                    )

                    sessionsList.clear()
                    sessionsList.add(updatedSession)

                    // Bold unread
                    adapter.notifyDataSetChanged()
                }

                lastMessageListeners[sessionId] = listener
            }
            .addOnFailureListener {
                Log.e("Peers_Chat", "Failed to load peer info", it)
            }
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
        lastMessageListeners.forEach { (sessionId, listener) ->
            chatManager.removeListener(sessionId, listener)
        }
        lastMessageListeners.clear()
    }
}