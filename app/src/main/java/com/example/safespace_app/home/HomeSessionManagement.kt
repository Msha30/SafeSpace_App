package com.example.safespace_app.home

import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.PeerSession
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.google.firebase.auth.FirebaseAuth

class HomeSessionManagement : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PeerSessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_home_session_management, container, false)

        val backBtn = rootView.findViewById<ImageView>(R.id.backbtn)
        backBtn.setOnClickListener { requireActivity().onBackPressed() }

        recyclerView = rootView.findViewById(R.id.peerSupportSessionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = PeerSessionAdapter(
            requireContext(),
            mutableListOf(),
            onCancelClick = { session ->
                showCancelReasonDialog(session)
            },
            onConfirmClick = { session ->
                // For Face to Face mode, require location before confirming
                if (session.preferredMode.trim() == "Face to Face" && session.location.isNullOrEmpty()) {
                    showLocationDialog(session)
                } else {
                    confirmSession(session)
                }
            }
        )

        recyclerView.adapter = adapter

        // Get current peer UID (the logged-in peer)
        val currentPeerUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentPeerUid != null) {
            // Observe Firestore peer_session_requests for this peer
            UserCache.activeSessionsLiveData.observe(viewLifecycleOwner) { sessions ->
                // Show only pending and confirmed sessions (not cancelled or completed)
                val activeSessions = sessions.filter {
                    it.status == "pending" || it.status == "confirmed"
                }
                adapter.updateSessions(activeSessions)
            }

            // Start listening for this peer's session requests
            UserCache.loadActiveSessionsForUser(peerUid = currentPeerUid)
        }

        return rootView
    }

    private fun showLocationDialog(session: PeerSession) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.popup_sessionconfirm, null)
        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val locationInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.location)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnconfirm)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btncancel)
        val nameText = dialogView.findViewById<TextView>(R.id.name)
        val infoText = dialogView.findViewById<TextView>(R.id.info)
        val concernText = dialogView.findViewById<TextView>(R.id.concern)

        UserCache.getUserDetails(session.studentUid) { displayName, _photoUrl ->
            nameText.text = displayName
        }

        // Set session info
        val modeDisplay = if (session.preferredMode == "Face to Face" && session.location.isNullOrEmpty()) {
            "Unconfirmed"
        } else if (session.preferredMode == "Face to Face") {
            session.location ?: "Unconfirmed"
        } else {
            session.preferredMode
        }

        infoText.text = "📅 ${session.selectedDate}\n" +
                "🕒 ${session.selectedTimeSlot}\n" +
                "📍 $modeDisplay"

        concernText.text = session.topicOfConcern ?: ""

        // Pre-fill location input if exists
        locationInput.setText(session.location ?: "")

        btnConfirm.setOnClickListener {
            val newLocation = locationInput.text.toString().trim()
            if (newLocation.isNotEmpty()) {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                firestore.collection("peer_session_requests")
                    .document(session.sessionId)
                    .update(mapOf(
                        "location" to newLocation,
                        "status" to "confirmed"
                    ))
                    .addOnSuccessListener {
                        session.location = newLocation
                        session.status = "confirmed"
                        adapter.notifyDataSetChanged()
                        dialog.dismiss()
                        android.widget.Toast.makeText(
                            context,
                            "Session confirmed with location",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("HomeSessionManagement", "Failed to update session", e)
                        android.widget.Toast.makeText(
                            context,
                            "Failed to confirm session",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Please enter a location",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCancelReasonDialog(session: PeerSession) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.popup_cancelsession, null)
        val dialog = android.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val contentText = dialogView.findViewById<TextView>(R.id.content)
        val reasonInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reason)
        val btnConfirm = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btncancel)
        val btnKeep = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnkeep)

        // Build dynamic confirmation text
        val timeSlot = session.selectedTimeSlot.ifEmpty { "0:00 - 0:00" }
        val displayDate = session.selectedDate.ifEmpty { "Month 0" }

        contentText.text = "Are you sure you want to cancel Peer Support Session for $timeSlot on $displayDate?"

        btnConfirm.setOnClickListener {
            val reason = reasonInput.text.toString().trim()
            if (reason.isNotEmpty()) {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                firestore.collection("peer_session_requests")
                    .document(session.sessionId)
                    .update(
                        mapOf(
                            "status" to "cancelled",
                            "cancellationReason" to reason
                        )
                    )
                    .addOnSuccessListener {
                        session.status = "cancelled"
                        adapter.notifyDataSetChanged()
                        dialog.dismiss()
                        android.widget.Toast.makeText(
                            context,
                            "Session cancelled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("HomeSessionManagement", "Failed to cancel session", e)
                        android.widget.Toast.makeText(
                            context,
                            "Failed to cancel session",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Please enter a reason",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnKeep.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun confirmSession(session: PeerSession) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("peer_session_requests")
            .document(session.sessionId)
            .update("status", "confirmed")
            .addOnSuccessListener {
                session.status = "confirmed"
                adapter.notifyDataSetChanged()
                android.widget.Toast.makeText(
                    context,
                    "Session confirmed",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("HomeSessionManagement", "Failed to confirm session", e)
                android.widget.Toast.makeText(
                    context,
                    "Failed to confirm session",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
    }
}