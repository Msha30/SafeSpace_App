package com.example.safespace_app.chat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ChatMessageFragment : Fragment() {

    private val chatManager = ChatManager()
    private val currentUserId by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageView
    private lateinit var endChatButton: MaterialButton
    private lateinit var backButton: ImageView
    private lateinit var nameText: TextView
    private lateinit var referButton: MaterialButton
    private lateinit var schedButton: ImageView

    private var messageListener: ValueEventListener? = null
    private var currentSessionId: String? = null
    private var otherUserName: String = "Chat"
    private var currentUserDisplayName: String = "You"
    private var otherUserId: String = ""
    private var userType: String = ""

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
        referButton = view.findViewById(R.id.referBtn)
        schedButton = view.findViewById(R.id.schedBtn)

        setupRecyclerView()
        setupClickListeners()
        loadCurrentUserName()
        loadUserType()
        observeSession()
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(currentUserId)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
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

        referButton.setOnClickListener {
            showReferralDialog()
        }

        schedButton.setOnClickListener {
            showScheduleSessionDialog()
        }
    }

    private fun loadUserType() {
        firestore.collection("account_details").document(currentUserId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userType = document.getString("userType") ?: ""
                    Log.d("ChatMessage", "User type loaded: $userType")

                    // Show/hide buttons based on user type
                    if (userType == "peer") {
                        referButton.visibility = View.VISIBLE
                        schedButton.visibility = View.VISIBLE
                    } else {
                        referButton.visibility = View.GONE
                        schedButton.visibility = View.GONE
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ChatMessage", "Error loading user type", e)
                // Hide buttons by default on error
                referButton.visibility = View.GONE
                schedButton.visibility = View.GONE
            }
    }

    private fun showReferralDialog() {
        val dialogView = layoutInflater.inflate(R.layout.popup_referral, null)

        val titleText = dialogView.findViewById<TextView>(R.id.title)
        val contentText = dialogView.findViewById<TextView>(R.id.content)
        val reasonInput = dialogView.findViewById<TextInputEditText>(R.id.reason)
        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.btnkeep)
        val submitBtn = dialogView.findViewById<MaterialButton>(R.id.btncancel)

        // Replace [name] with other user's name
        contentText.text = contentText.text.toString().replace("[name]", otherUserName)

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        submitBtn.setOnClickListener {
            val reason = reasonInput.text.toString().trim()

            if (reason.isEmpty()) {
                Toast.makeText(requireContext(), "Please provide a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitReferral(reason)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun submitReferral(reason: String) {
        val referralData = hashMapOf(
            "date_submitted" to com.google.firebase.Timestamp.now(),
            "messageId" to (currentSessionId ?: ""),
            "reason" to reason,
            "studentUid" to otherUserId,
            "submitted_by" to currentUserId
        )

        firestore.collection("referral_submission")
            .add(referralData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Referral submitted successfully", Toast.LENGTH_SHORT).show()
                Log.d("ChatMessage", "Referral submitted: ${it.id}")
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to submit referral", Toast.LENGTH_SHORT).show()
                Log.e("ChatMessage", "Error submitting referral", e)
            }
    }

    private fun showScheduleSessionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.popup_schedulesession, null)

        val dateInput = dialogView.findViewById<TextInputEditText>(R.id.date)
        val timeFromInput = dialogView.findViewById<TextInputEditText>(R.id.time_from)
        val timeToInput = dialogView.findViewById<TextInputEditText>(R.id.time_to)
        val locationInput = dialogView.findViewById<TextInputEditText>(R.id.sesh_loc)
        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.btnkeep)
        val confirmBtn = dialogView.findViewById<MaterialButton>(R.id.btncancel)

        val calendar = Calendar.getInstance()
        var selectedDate: Date? = null
        var selectedStartTime: Pair<Int, Int>? = null
        var selectedEndTime: Pair<Int, Int>? = null

        // Date picker
        dateInput.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    selectedDate = calendar.time
                    val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
                    dateInput.setText(dateFormat.format(selectedDate))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Time from picker
        timeFromInput.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedStartTime = Pair(hourOfDay, minute)
                    timeFromInput.setText(String.format("%02d:%02d", hourOfDay, minute))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        // Time to picker
        timeToInput.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    selectedEndTime = Pair(hourOfDay, minute)
                    timeToInput.setText(String.format("%02d:%02d", hourOfDay, minute))
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        val dialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        confirmBtn.setOnClickListener {
            val location = locationInput.text.toString().trim()

            if (selectedDate == null || selectedStartTime == null || selectedEndTime == null || location.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill out all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitScheduleSession(selectedDate!!, selectedStartTime!!, selectedEndTime!!, location)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun submitScheduleSession(
        date: Date,
        startTime: Pair<Int, Int>,
        endTime: Pair<Int, Int>,
        location: String
    ) {
        // Create start and end timestamps
        val startCalendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, startTime.first)
            set(Calendar.MINUTE, startTime.second)
            set(Calendar.SECOND, 0)
        }

        val endCalendar = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, endTime.first)
            set(Calendar.MINUTE, endTime.second)
            set(Calendar.SECOND, 0)
        }

        val sessionData = hashMapOf(
            "date_submitted" to com.google.firebase.Timestamp.now(),
            "studentUid" to otherUserId,
            "peerUid" to currentUserId,
            "start_time" to com.google.firebase.Timestamp(startCalendar.time),
            "end_time" to com.google.firebase.Timestamp(endCalendar.time),
            "location" to location
        )

        firestore.collection("peertopeer_session")
            .add(sessionData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Session scheduled successfully", Toast.LENGTH_SHORT).show()
                Log.d("ChatMessage", "Session scheduled: ${it.id}")
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to schedule session", Toast.LENGTH_SHORT).show()
                Log.e("ChatMessage", "Error scheduling session", e)
            }
    }

    private fun showEndChatDialog() {
        val dialogView = layoutInflater.inflate(R.layout.popup_endchat, null)

        val titleText = dialogView.findViewById<TextView>(R.id.title)
        val contentText = dialogView.findViewById<TextView>(R.id.content)
        val cancelBtn = dialogView.findViewById<MaterialButton>(R.id.btncancel)
        val confirmBtn = dialogView.findViewById<MaterialButton>(R.id.btnout)

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
                val (sessionId, userId) = session

                if (currentSessionId != sessionId) {
                    messageListener?.let {
                        currentSessionId?.let { oldSessionId ->
                            chatManager.removeListener(oldSessionId, it)
                        }
                    }

                    currentSessionId = sessionId
                    otherUserId = userId
                    loadOtherUserInfo(userId)
                    startListeningForMessages(sessionId)
                    markMessagesAsRead(sessionId)
                }
            } else {
                Log.d("ChatMessage", "Session ended, navigating back")
                if (isAdded) {
                    findNavController().navigateUp()
                }
            }
        }
    }

    private fun loadOtherUserInfo(userId: String) {
        UserCache.getUserDetails(userId) { name, photoUrl ->
            if (isAdded) {
                otherUserName = name
                nameText.text = name
                Log.d("ChatMessage", "Loaded other user info: $name")
            }
        }
    }

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
                    if (messages.isNotEmpty()) {
                        recyclerView.smoothScrollToPosition(messages.size - 1)
                    }
                }
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
            senderName = currentUserDisplayName,
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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        messageListener?.let { listener ->
            currentSessionId?.let { sessionId ->
                chatManager.removeListener(sessionId, listener)
            }
        }

        messageListener = null
        currentSessionId = null
    }
}