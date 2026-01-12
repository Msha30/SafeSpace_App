package com.example.safespace_app.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class ChatMessageFragment  : Fragment() {

    private val chatManager = ChatManager()
    private val currentUserId by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var endChatButton: MaterialButton
    private lateinit var backButton: ImageView
    private lateinit var nameText: TextView

    private var messageListener: ValueEventListener? = null
    private var currentSessionId: String? = null
    private var otherUserName: String = "Chat"
    private var currentUserDisplayName: String = "You"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat_message, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerViewChat)
        messageInput = view.findViewById(R.id.messageInput)
        sendButton = view.findViewById(R.id.send)
        endChatButton = view.findViewById(R.id.endBtn)
        backButton = view.findViewById(R.id.backbtn)
        nameText = view.findViewById(R.id.name)

        setupRecyclerView()
        setupClickListeners()
        loadCurrentUserName()
        observeSession()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(currentUserId)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true // Start from bottom
        }
    }

    private fun setupClickListeners() {
        sendButton.setOnClickListener {
            sendMessage()
        }

        endChatButton.setOnClickListener {
            showEndChatDialog()
        }

        backButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun showEndChatDialog() {
        val dialogView = layoutInflater.inflate(R.layout.popup_endchat, null)

        val titleText = dialogView.findViewById<TextView>(R.id.title)
        val contentText = dialogView.findViewById<TextView>(R.id.content)
        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.btncancel)
        val confirmBtn = dialogView.findViewById<MaterialButton>(R.id.btnout)

        // Replace [user] in content
        contentText.text = contentText.text.toString().replace("[user]", otherUserName)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        confirmBtn.setOnClickListener {
            dialog.dismiss()
            endSession()
        }

        dialog.show()
    }

    private fun observeSession() {
        UserCache.sessionLiveData.observe(viewLifecycleOwner) { session ->
            if (session != null) {
                val (sessionId, otherUserId) = session

                if (currentSessionId != sessionId) {
                    // Session changed, clean up old listener
                    messageListener?.let {
                        currentSessionId?.let { oldSessionId ->
                            chatManager.removeListener(oldSessionId, it)
                        }
                    }

                    currentSessionId = sessionId
                    loadOtherUserInfo(otherUserId)
                    startListeningForMessages(sessionId)
                    markMessagesAsRead(sessionId)
                }
            } else {
                // Session ended
                Log.d("ChatMessage", "Session ended, navigating back")
                if (isAdded) {
                    findNavController().navigateUp()
                }
            }
        }
    }

    /**
     * Load other user's info using UserCache (respects student anonymity)
     */
    private fun loadOtherUserInfo(otherUserId: String) {
        UserCache.getUserDetails(otherUserId) { name, photoUrl ->
            if (isAdded) {
                otherUserName = name
                nameText.text = name
                Log.d("ChatMessage", "Loaded other user info: $name")
            }
        }
    }

    /**
     * Load current user's display name (username for students, full name for peers)
     */
    private fun loadCurrentUserName() {
        UserCache.getUserDetails(currentUserId) { name, photoUrl ->
            if (isAdded) {
                currentUserDisplayName = name
                Log.d("ChatMessage", "Loaded current user name: $name")
            }
        }
    }

    private fun startListeningForMessages(sessionId: String) {
        Log.d("ChatMessage", "Starting to listen for messages in session: $sessionId")

        messageListener = chatManager.listenForMessages(sessionId) { messages ->
            if (isAdded) {
                adapter.submitList(messages) {
                    // Scroll to bottom after messages are updated
                    if (messages.isNotEmpty()) {
                        recyclerView.smoothScrollToPosition(messages.size - 1)
                    }
                }

                // Mark as read when messages arrive
                markMessagesAsRead(sessionId)
            }
        }
    }

    private fun sendMessage() {
        val sessionId = currentSessionId ?: return
        val message = messageInput.text.toString()

        if (message.trim().isEmpty()) {
            return
        }

        chatManager.sendMessage(
            sessionId = sessionId,
            senderId = currentUserId,
            senderName = currentUserDisplayName, // Use the loaded display name
            message = message,
            onSuccess = {
                if (isAdded) {
                    messageInput.text?.clear()
                }
            },
            onFailure = { error ->
                if (isAdded) {
                    Toast.makeText(requireContext(), "Failed to send: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun markMessagesAsRead(sessionId: String) {
        chatManager.markMessagesAsRead(sessionId, currentUserId)
    }

    private fun endSession() {
        val sessionId = currentSessionId ?: return

        val pairingManager = com.example.safespace_app.peers.PairingManager()

        pairingManager.endSession(sessionId) {
            if (isAdded) {
                Log.d("ChatMessage", "Session ended successfully")
                // The session observer will handle navigation
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Clean up listener
        messageListener?.let { listener ->
            currentSessionId?.let { sessionId ->
                chatManager.removeListener(sessionId, listener)
            }
        }

        messageListener = null
        currentSessionId = null
    }
}