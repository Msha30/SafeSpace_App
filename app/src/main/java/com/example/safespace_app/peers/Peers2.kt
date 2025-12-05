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
import com.example.safespace_app.cache.UserCache
import com.example.safespace_app.chat.ChatManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ActiveSession(
    val sessionId: String,
    val studentUid: String,
    val studentName: String,
    val studentPhoto: String,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0
)

class Peers2 : Fragment() {

    private lateinit var adapter: SessionsAdapter
    private val sessionsList = mutableListOf<ActiveSession>()
    private val pairingManager = PairingManager()
    private val chatManager = ChatManager()
    private val peerUid by lazy { FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    private var currentDialog: AlertDialog? = null
    private val shownRequests = mutableSetOf<String>()
    private var sessionsListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_peers2, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPeers)

        adapter = SessionsAdapter(sessionsList) { session ->
            openChat(session.sessionId, session.studentUid)
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        UserCache.loadPeers()
        registerPairingCallback()
        loadActiveSessions()

        // Watch all active sessions in cache
        observeActiveSessions()

        lifecycleScope.launch {
            delay(200)
            checkForPendingRequests()
        }
    }

    // ------------------------------------------------------------
    // Load Active Sessions
    // ------------------------------------------------------------
    private fun loadActiveSessions() {
        val rtdb = FirebaseDatabase.getInstance(
            "https://safespace-af7ec-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
        val sessionsRef = rtdb.getReference("sessions")

        Log.d("Peers2", "Loading active sessions for peer: $peerUid")

        sessionsListener = sessionsRef.orderByChild("peer").equalTo(peerUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    sessionsList.clear()

                    val sessionIds = mutableListOf<String>()

                    for (child in snapshot.children) {
                        val status = child.child("status").getValue(String::class.java)
                        if (status == "active") {
                            val sessionId = child.key ?: continue
                            val studentUid = child.child("student").getValue(String::class.java) ?: continue

                            sessionIds.add(sessionId)

                            // Load student info and add to list
                            loadStudentInfoAndAddSession(sessionId, studentUid)
                        }
                    }

                    Log.d("Peers2", "Found ${sessionIds.size} active sessions")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("Peers2", "Sessions listener error", error.toException())
                }
            })
    }
    private fun observeActiveSessions() {
        UserCache.sessionLiveData.observe(viewLifecycleOwner) { sessionPair ->
            if (sessionPair != null) {
                val (sessionId, studentUid) = sessionPair
                // Check if already in list
                val existingIndex = sessionsList.indexOfFirst { it.sessionId == sessionId }
                if (existingIndex < 0) {
                    // Load student info and add session
                    loadStudentInfoAndAddSession(sessionId, studentUid)
                }
            } else {
                // Session ended or deleted → remove from list
                val removed = sessionsList.removeAll { session ->
                    UserCache.getActiveSession()?.first != session.sessionId
                }
                if (removed) {
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun loadStudentInfoAndAddSession(sessionId: String, studentUid: String) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(studentUid)
            .get()
            .addOnSuccessListener { doc ->
                val firstName = doc.getString("fname") ?: ""
                val lastName = doc.getString("lname") ?: ""
                val name = "$firstName $lastName".trim().ifEmpty { "Student" }
                val photo = doc.getString("avatarUrl") ?: ""

                // Get last message and unread count
                chatManager.getLastMessage(sessionId) { lastMessage ->
                    chatManager.getUnreadCount(sessionId, peerUid) { unreadCount ->
                        val session = ActiveSession(
                            sessionId = sessionId,
                            studentUid = studentUid,
                            studentName = name,
                            studentPhoto = photo,
                            lastMessage = lastMessage?.message ?: "No messages yet",
                            lastMessageTime = lastMessage?.timestamp ?: 0L,
                            unreadCount = unreadCount
                        )

                        // Update list
                        if (isAdded) {
                            val existingIndex = sessionsList.indexOfFirst { it.sessionId == sessionId }
                            if (existingIndex >= 0) {
                                sessionsList[existingIndex] = session
                            } else {
                                sessionsList.add(session)
                            }

                            // Sort by last message time
                            sessionsList.sortByDescending { it.lastMessageTime }
                            adapter.notifyDataSetChanged()
                        }
                    }
                }
            }
            .addOnFailureListener {
                Log.e("Peers2", "Failed to load student info", it)
            }
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
    // Pairing Request Handling (existing code)
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

        fetchStudentInfo(studentUid) { name, photoUrl ->
            if (isAdded) {
                nameView.text = name
                if (!photoUrl.isNullOrEmpty()) {
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

        fetchStudentInfo(studentUid) { name, photoUrl ->
            if (isAdded) {
                nameView.text = name
                if (!photoUrl.isNullOrEmpty()) {
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

    private fun fetchStudentInfo(studentUid: String, callback: (String, String?) -> Unit) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(studentUid)
            .get()
            .addOnSuccessListener { doc ->
                val first = doc.getString("fname") ?: ""
                val last = doc.getString("lname") ?: ""
                val photo = doc.getString("avatarUrl")

                val name = "$first $last".trim().ifEmpty { "Student" }

                callback(name, photo)
            }
            .addOnFailureListener {
                callback("Student", null)
            }
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

        Log.d("Peers2", "Fragment destroyed. Callback cleared.")
    }
}