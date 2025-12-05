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

data class StudentActiveSession(
    val sessionId: String,
    val peerUid: String,
    val peerName: String,
    val peerPhoto: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L
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

    private fun loadPeerInfoAndUpdateSession(sessionId: String, peerUid: String) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(peerUid)
            .get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("fname") ?: ""
                val lastName = doc.getString("lname") ?: ""
                val name = "$firstName $lastName".trim().ifEmpty { "Peer" }
                val photo = doc.getString("avatarUrl") ?: ""

                // Get last message
                chatManager.getLastMessage(sessionId) { lastMessage ->
                    if (isAdded) {
                        val session = StudentActiveSession(
                            sessionId = sessionId,
                            peerUid = peerUid,
                            peerName = name,
                            peerPhoto = photo,
                            lastMessage = lastMessage?.message ?: "No messages yet",
                            lastMessageTime = lastMessage?.timestamp ?: 0L
                        )

                        sessionsList.clear()
                        sessionsList.add(session)
                        adapter.notifyDataSetChanged()
                    }
                }
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
}