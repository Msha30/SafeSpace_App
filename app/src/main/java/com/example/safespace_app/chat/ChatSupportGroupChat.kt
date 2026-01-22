package com.example.safespace_app.chat

import GroupChatAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.GroupChatMessage
import com.example.safespace_app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*

class ChatSupportGroupChat : Fragment() {

    companion object {
        const val ARG_GROUP_ID = "supportGroupId"
        const val ARG_GROUPCHAT_ID = "groupchatId"
        private const val PAGE_SIZE: Long = 50
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var inputField: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var headerName: TextView
    private lateinit var backBtn: ImageView

    private val adapter = GroupChatAdapter()
    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var listener: ListenerRegistration? = null

    private var groupchatId: String? = null
    private var groupId: String? = null

    private val viewModel: ChatViewModel by viewModels()

    // For pagination
    private var firstVisibleMessage: GroupChatMessage? = null
    private var isLoadingOlderMessages = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupchatId = it.getString(ARG_GROUPCHAT_ID)
            groupId = it.getString(ARG_GROUP_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_chat_support_group_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.group_chats)
        inputField = view.findViewById(R.id.messageInput)
        sendBtn = view.findViewById(R.id.send)
        headerName = view.findViewById(R.id.groupchat_name)
        backBtn = view.findViewById(R.id.backbtn)

        recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        recyclerView.adapter = adapter

        sendBtn.setOnClickListener { sendMessage() }
        backBtn.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                val layout = rv.layoutManager as LinearLayoutManager
                if (!isLoadingOlderMessages && layout.findFirstVisibleItemPosition() == 0) {
                    loadOlderMessages()
                }
            }
        })

        viewModel.messages.observe(viewLifecycleOwner, Observer { messages ->
            adapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    recyclerView.scrollToPosition(messages.size - 1)
                    firstVisibleMessage = messages.first()
                }
            }
        })

        loadGroupChatName()
        loadInitialMessages()
    }

    private fun messagesCollection(): CollectionReference? {
        if (groupId.isNullOrBlank() || groupchatId.isNullOrBlank()) return null
        return db.collection("supportgroup")
            .document(groupId!!)
            .collection("groupchats")
            .document(groupchatId!!)
            .collection("messages")
    }

    private fun loadGroupChatName() {
        if (groupId.isNullOrBlank() || groupchatId.isNullOrBlank()) return
        db.collection("supportgroup")
            .document(groupId!!)
            .get()
            .addOnSuccessListener { doc ->
                val groupchats = doc.get("groupchats") as? List<Map<String, Any>> ?: emptyList()
                val chat = groupchats.firstOrNull { it["groupchatId"] == groupchatId }
                headerName.text = chat?.get("name") as? String ?: "Group Chat"
            }
    }

    /** Load the initial last PAGE_SIZE messages */
    private fun loadInitialMessages() {
        val coll = messagesCollection() ?: return

        coll.orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                val messages = snapshot.documents.map { doc -> toMessage(doc) }
                viewModel.addMessages(messages)
                startRealtimeListener(messages.lastOrNull()?.timestamp)
            }
    }

    /** Load older messages for pagination */
    private fun loadOlderMessages() {
        if (isLoadingOlderMessages) return
        isLoadingOlderMessages = true
        val firstMsg = firstVisibleMessage ?: run { isLoadingOlderMessages = false; return }
        val coll = messagesCollection() ?: run { isLoadingOlderMessages = false; return }

        coll.orderBy("timestamp", Query.Direction.ASCENDING)
            .endBefore(firstMsg.timestamp)
            .limitToLast(PAGE_SIZE)
            .get()
            .addOnSuccessListener { snapshot ->
                val oldMessages = snapshot.documents.map { doc -> toMessage(doc) }
                viewModel.prependMessages(oldMessages)
                isLoadingOlderMessages = false
            }
            .addOnFailureListener { isLoadingOlderMessages = false }
    }

    /** Start listener for new messages only */
    private fun startRealtimeListener(lastTimestamp: Long?) {
        val coll = messagesCollection() ?: return

        var query = coll.orderBy("timestamp", Query.Direction.ASCENDING)
        lastTimestamp?.let { ts ->
            query = query.startAfter(ts)
        }

        listener?.remove()
        listener = query.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            val newMessages = snapshot.documents.map { doc -> toMessage(doc) }
            viewModel.addMessages(newMessages)
        }
    }

    /** Convert Firestore document to GroupChatMessage with resolved senderName */
    private fun toMessage(doc: DocumentSnapshot): GroupChatMessage {
        val senderId = doc.getString("senderId") ?: ""
        val message = doc.getString("message") ?: ""
        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

        val cachedName = viewModel.userDisplayNameCache[senderId]
        if (cachedName != null) {
            return GroupChatMessage(doc.id, senderId, cachedName, message, timestamp)
        }

        db.collection("account_details")
            .document(senderId)
            .get()
            .addOnSuccessListener { userDoc ->
                val name = when (userDoc.getString("userType")) {
                    "student" -> userDoc.getString("username") ?: "Unknown"
                    "peer" -> {
                        val fname = userDoc.getString("fname") ?: ""
                        val lname = userDoc.getString("lname") ?: ""
                        "$fname $lname".trim().ifEmpty { "Unknown" }
                    }
                    else -> "Unknown"
                }
                viewModel.userDisplayNameCache[senderId] = name
                viewModel.updateMessageSenderName(doc.id, name)
            }

        return GroupChatMessage(doc.id, senderId, cachedName ?: "Loading...", message, timestamp)
    }


    private fun sendMessage() {
        val text = inputField.text.toString().trim()
        if (text.isEmpty()) return

        val coll = messagesCollection() ?: return

        val senderName = FirebaseAuth.getInstance().currentUser?.displayName ?: "You"
        val msg = hashMapOf(
            "senderId" to currentUserId,
            "senderName" to senderName, // stored locally, name resolution is separate for other users
            "message" to text,
            "timestamp" to System.currentTimeMillis()
        )

        coll.add(msg).addOnSuccessListener { inputField.text.clear() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
    }
}
