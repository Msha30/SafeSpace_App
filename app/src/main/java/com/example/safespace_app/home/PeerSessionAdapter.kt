package com.example.safespace_app.home

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.CallActivity
import com.example.safespace_app.PeerSession
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.imageview.ShapeableImageView


class PeerSessionAdapter(
    private val context: Context,
    private var sessions: MutableList<PeerSession>,
    private val onCancelClick: (PeerSession) -> Unit,
    private val onConfirmClick: (PeerSession) -> Unit
) : RecyclerView.Adapter<PeerSessionAdapter.SessionViewHolder>() {

    inner class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view.findViewById(R.id.sessionCard)
        val profileImage: ShapeableImageView = view.findViewById(R.id.profileImage)
        val nameText: TextView = view.findViewById(R.id.nameText)
        val infoText: TextView = view.findViewById(R.id.infoText)
        val btnCancel: MaterialButton = view.findViewById(R.id.btnCancel)
        val btnConfirm: MaterialButton = view.findViewById(R.id.btnConfirm)
        val btnStartCall: MaterialButton = view.findViewById(R.id.btnStartCall)
        val statusBadge: TextView = view.findViewById(R.id.statusBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_peer_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        // Load user details
        UserCache.getUserDetails(session.studentUid) { displayName, photoUrl ->
            holder.nameText.text = displayName
            if (photoUrl.isNotEmpty()) {
                Glide.with(context)
                    .load(photoUrl)
                    .placeholder(R.drawable.img_placeholder)
                    .into(holder.profileImage)
            } else {
                holder.profileImage.setImageResource(R.drawable.img_placeholder)
            }
        }

        // Display session info
        val modeDisplay = when {
            session.preferredMode == "Video Call" -> "📹 Video Call"
            session.preferredMode == "Call" -> "📞 Voice Call"
            session.preferredMode == "Face to Face" && session.location.isNullOrEmpty() -> "📍 Unconfirmed"
            session.preferredMode == "Face to Face" -> "📍 ${session.location}"
            else -> session.preferredMode
        }

        holder.infoText.text = "📅 ${session.selectedDate}\n" +
                "🕒 ${session.selectedTimeSlot}\n" +
                modeDisplay

        // Show/hide buttons based on status
        when (session.status) {
            "pending" -> {
                holder.btnCancel.visibility = View.VISIBLE
                holder.btnConfirm.visibility = View.VISIBLE
                holder.btnStartCall.visibility = View.GONE
                holder.statusBadge.visibility = View.GONE

                holder.btnCancel.setOnClickListener { onCancelClick(session) }
                holder.btnConfirm.setOnClickListener { onConfirmClick(session) }
            }
            "confirmed" -> {
                holder.btnCancel.visibility = View.GONE
                holder.btnConfirm.visibility = View.GONE
                holder.statusBadge.visibility = View.VISIBLE
                holder.statusBadge.text = "✓ Confirmed"
                holder.statusBadge.setTextColor(context.getColor(R.color.green))


                // Show call button for video/voice calls
                if (session.preferredMode == "Video Call" || session.preferredMode == "Call") {
                    holder.btnStartCall.visibility = View.VISIBLE

                    android.util.Log.d("PeerAdapter", "Binding session ${session.sessionId}: callStatus=${session.callStatus}")

                    // Check call status
                    when (session.callStatus) {
                        "active" -> {
                            holder.btnStartCall.text = "Join Call"
                            holder.btnStartCall.setBackgroundResource(R.drawable.f_rounded_green)
                            android.util.Log.d("PeerAdapter", "Button set to JOIN CALL")
                        }
                        else -> {
                            holder.btnStartCall.text = "Start Call"
                            holder.btnStartCall.setBackgroundResource(R.drawable.f_rounded_blue)
                            android.util.Log.d("PeerAdapter", "Button set to START CALL")
                        }
                    }

                    holder.btnStartCall.setOnClickListener {
                        startOrJoinCall(session)
                    }
                } else {
                    holder.btnStartCall.visibility = View.GONE
                }
            }
            "cancelled" -> {
                holder.btnCancel.visibility = View.GONE
                holder.btnConfirm.visibility = View.GONE
                holder.btnStartCall.visibility = View.GONE
                holder.statusBadge.visibility = View.VISIBLE
                holder.statusBadge.text = "✗ Cancelled"
                holder.statusBadge.setTextColor(context.getColor(R.color.red))

            }
            else -> {
                holder.btnCancel.visibility = View.GONE
                holder.btnConfirm.visibility = View.GONE
                holder.btnStartCall.visibility = View.GONE
                holder.statusBadge.visibility = View.GONE
            }
        }
    }

    private fun startOrJoinCall(session: PeerSession) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val isVideoCall = session.preferredMode == "Video Call"
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val sessionRef = firestore.collection("peer_session_requests").document(session.sessionId)

        // Use transaction to atomically check and set call status
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(sessionRef)
            val currentCallStatus = snapshot.getString("callStatus")
            val currentInitiatorUid = snapshot.getString("callInitiatorUid")

            if (currentCallStatus == "active" && currentInitiatorUid != null) {
                // Call already active, we'll join as receiver
                return@runTransaction mapOf(
                    "isInitiator" to false,
                    "initiatorUid" to currentInitiatorUid
                )
            } else {
                // No active call, we become the initiator
                transaction.update(sessionRef, mapOf(
                    "callStatus" to "active",
                    "callInitiatorUid" to currentUid
                ))
                return@runTransaction mapOf(
                    "isInitiator" to true,
                    "initiatorUid" to currentUid
                )
            }
        }.addOnSuccessListener { result ->
            val isInitiator = result["isInitiator"] as Boolean
            val initiatorUid = result["initiatorUid"] as String

            // Update local session object
            session.callStatus = "active"
            session.callInitiatorUid = initiatorUid
            notifyDataSetChanged()

            android.util.Log.d("PeerSessionAdapter", "Call role: ${if (isInitiator) "INITIATOR" else "RECEIVER"}")

            // Get other user's name for display
            UserCache.getUserDetails(session.studentUid) { peerName, _ ->
                val intent = Intent(context, CallActivity::class.java).apply {
                    putExtra(CallActivity.EXTRA_SESSION_ID, session.sessionId)
                    putExtra(CallActivity.EXTRA_PEER_NAME, peerName)
                    putExtra(CallActivity.EXTRA_IS_VIDEO_CALL, isVideoCall)
                    putExtra(CallActivity.EXTRA_IS_INITIATOR, isInitiator)
                }
                context.startActivity(intent)
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("PeerSessionAdapter", "Transaction failed", e)
            android.widget.Toast.makeText(
                context,
                "Failed to start/join call",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun getItemCount() = sessions.size

    fun updateSessions(newSessions: List<PeerSession>) {
        android.util.Log.d("PeerAdapter", "updateSessions called with ${newSessions.size} sessions")
        newSessions.forEach { session ->
            android.util.Log.d("PeerAdapter", "  Session ${session.sessionId}: callStatus=${session.callStatus}")
        }
        sessions.clear()
        sessions.addAll(newSessions)
        notifyDataSetChanged()
    }
}