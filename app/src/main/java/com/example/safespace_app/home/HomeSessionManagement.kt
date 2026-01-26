package com.example.safespace_app.home

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.safespace_app.PeerToPeerSession
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeSessionManagement : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter

    private var sessionsListener: ListenerRegistration? = null
    private val TAG = "HomeSessionManagement"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_home_session_management, container, false)

        val backBtn = rootView.findViewById<ImageView>(R.id.backbtn)
        backBtn.setOnClickListener { requireActivity().onBackPressed() }

        recyclerView = rootView.findViewById(R.id.peerSupportSessionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = SessionAdapter(mutableListOf())
        recyclerView.adapter = adapter

        val currentPeerUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentPeerUid != null) {
            observeSessions(currentPeerUid)
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sessionsListener?.remove()
        sessionsListener = null
    }

    /**
     * Observe peer sessions for this peerUid using defensive mapping.
     * Reads boolean fields directly from snapshot to avoid toObject() defaulting problems.
     */
    private fun observeSessions(peerUid: String) {
        val firestore = FirebaseFirestore.getInstance()
        sessionsListener?.remove()

        sessionsListener = firestore.collection("peertopeer_session")
            .whereEqualTo("peerUid", peerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error loading sessions", error)
                    return@addSnapshotListener
                }
                if (snapshot == null) {
                    Log.w(TAG, "Snapshot was null")
                    adapter.updateSessions(emptyList())
                    return@addSnapshotListener
                }

                // Debug raw documents (helps spot mismatched field names/types)
                snapshot.documents.forEach { doc ->
                    Log.d(TAG, "RAW peer doc: ${doc.id} -> ${doc.data}")
                }

                val now = System.currentTimeMillis()
                val sessions = snapshot.documents.mapNotNull { doc ->
                    try {
                        val sessionId = doc.id
                        val studentUid = doc.getString("studentUid") ?: ""
                        val peerUidField = doc.getString("peerUid") ?: ""

                        val startTs = doc.get("start_time") as? Timestamp
                        val endTs = doc.get("end_time") as? Timestamp
                        val location = doc.getString("location") ?: ""
                        val dateSubmitted = doc.get("date_submitted") as? Timestamp

                        // Read cancellation-related fields defensively
                        val isCancelled = doc.getBoolean("isCancelled") ?: false
                        val cancellationReason = doc.getString("cancellationReason") ?: ""
                        val cancellationConfirmed = doc.getBoolean("cancellationConfirmed") ?: false
                        val cancelledBy = doc.getString("cancelledBy") ?: ""

                        PeerToPeerSession(
                            sessionId = sessionId,
                            studentUid = studentUid,
                            peerUid = peerUidField,
                            start_time = startTs,
                            end_time = endTs,
                            location = location,
                            date_submitted = dateSubmitted,
                            isCancelled = isCancelled,
                            cancellationReason = cancellationReason,
                            cancellationConfirmed = cancellationConfirmed,
                            cancelledBy = cancelledBy
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to map session ${doc.id}", e)
                        null
                    }
                }.filter { session ->
                    // Filter out confirmed cancellations and past sessions
                    val endTime = session.end_time?.toDate()?.time ?: 0L
                    val isNotPast = endTime > now
                    !session.cancellationConfirmed && isNotPast
                }.sortedByDescending { it.start_time?.toDate()?.time ?: 0L }

                adapter.updateSessions(sessions)
            }
    }

    inner class SessionAdapter(
        private var sessions: List<PeerToPeerSession>
    ) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

        inner class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val sideBar: View = view.findViewById(R.id.side)
            val profileImage: ShapeableImageView = view.findViewById(R.id.profileImage)
            val nameText: TextView = view.findViewById(R.id.nameText)
            val infoText: TextView = view.findViewById(R.id.infoText)
            val btnCallAction: MaterialButton = view.findViewById(R.id.btnCallAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session, parent, false)
            return SessionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
            val session = sessions[position]

            // defensive: set width or other item params if needed (keeps same look)
            // Load Student Name and Avatar (use tags to avoid recycled-view flicker)
            loadUserDetails(session.studentUid, holder.profileImage, holder.nameText)

            // Determine button state FIRST
            val buttonState = determineButtonState(session)

            // Format info text based on cancellation status
            if (session.isCancelled) {
                val reason = session.cancellationReason.ifEmpty { "No reason provided." }
                holder.infoText.text = "Cancellation reason:\n$reason"
            } else {
                holder.infoText.text = formatSessionInfo(session)
            }

            configureButton(holder, session, buttonState)
        }

        private fun loadUserDetails(uid: String, imageView: ShapeableImageView, nameView: TextView) {
            val firestore = FirebaseFirestore.getInstance()

            // defensive tags to avoid showing wrong data on recycled views
            nameView.tag = uid
            imageView.tag = uid

            if (uid.isBlank()) {
                nameView.text = "Student"
                imageView.setImageResource(R.drawable.img_placeholder)
                return
            }

            firestore.collection("account_details")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    // ensure view hasn't been recycled for another uid
                    if (nameView.tag != uid || imageView.tag != uid) return@addOnSuccessListener

                    val displayName = doc.getString("displayName")
                        ?: "${doc.getString("lname") ?: ""} ${doc.getString("fname") ?: ""}"
                            .trim().ifEmpty { "Student" }
                    val avatarUrl = doc.getString("avatarUrl") ?: ""

                    nameView.text = displayName

                    if (avatarUrl.isNotEmpty()) {
                        if (avatarUrl.startsWith("http")) {
                            Glide.with(imageView.context)
                                .load(avatarUrl)
                                .placeholder(R.drawable.img_placeholder)
                                .error(R.drawable.img_placeholder)
                                .skipMemoryCache(true)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .into(imageView)
                        } else {
                            val drawableRes = when (avatarUrl) {
                                "image_1" -> R.drawable.avatar_panda
                                "image_2" -> R.drawable.avatar_butterfly
                                "image_3" -> R.drawable.avatar_wolf
                                "image_4" -> R.drawable.avatar_buffalo
                                else -> R.drawable.img_placeholder
                            }
                            imageView.setImageResource(drawableRes)
                        }
                    } else {
                        imageView.setImageResource(R.drawable.img_placeholder)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed loading account_details for $uid", e)
                    if (nameView.tag == uid) nameView.text = "Student"
                    if (imageView.tag == uid) imageView.setImageResource(R.drawable.img_placeholder)
                }
        }

        private fun formatSessionInfo(session: PeerToPeerSession): String {
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

            val startDate = session.start_time?.toDate()
            val endTime = session.end_time?.toDate()

            val dateStr = startDate?.let { dateFormat.format(it) } ?: "Date TBD"
            val startTimeStr = startDate?.let { timeFormat.format(it) } ?: "0:00"
            val endTimeStr = endTime?.let { timeFormat.format(it) } ?: "0:00"
            val location = session.location.ifEmpty { "Location TBD" }

            return "$dateStr\n$startTimeStr - $endTimeStr\n$location"
        }

        private fun determineButtonState(session: PeerToPeerSession): ButtonState {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val cancelled = session.isCancelled
            Log.d(TAG, "Checking session ${session.sessionId} cancelled=$cancelled")

            if (cancelled) {
                Log.d(TAG, "Session is cancelled: ${session.sessionId}")
                return if (session.cancelledBy == currentUid) {
                    ButtonState.CANCELLED_BY_ME
                } else {
                    ButtonState.CONFIRM_CANCEL
                }
            } else {
                val now = Calendar.getInstance()
                val start = session.start_time?.toDate() ?: return ButtonState.CANCEL
                val end = session.end_time?.toDate() ?: return ButtonState.CANCEL

                val startCal = Calendar.getInstance().apply { time = start }
                val endCal = Calendar.getInstance().apply { time = end }

                return when {
                    // If current time is within the session time range
                    now.timeInMillis in startCal.timeInMillis..endCal.timeInMillis -> ButtonState.NOW
                    // If same day but time hasn't started yet
                    isSameDay(now, startCal) && now.timeInMillis < startCal.timeInMillis -> ButtonState.TODAY
                    // Otherwise, can cancel (future sessions)
                    else -> ButtonState.CANCEL
                }
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun configureButton(holder: SessionViewHolder, session: PeerToPeerSession, state: ButtonState) {
            holder.btnCallAction.setOnClickListener(null)

            when (state) {
                ButtonState.NOW -> {
                    holder.btnCallAction.text = "Now"
                    holder.btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_green, null)
                    holder.sideBar.backgroundTintList = resources.getColorStateList(R.color.green, null)
                    holder.btnCallAction.isEnabled = false
                }

                ButtonState.TODAY -> {
                    holder.btnCallAction.text = "Today"
                    holder.btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_blue, null)
                    holder.sideBar.backgroundTintList = resources.getColorStateList(R.color.blue, null)
                    holder.btnCallAction.isEnabled = false
                }

                ButtonState.CANCEL -> {
                    holder.btnCallAction.text = "Cancel"
                    holder.btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_grey, null)
                    holder.sideBar.backgroundTintList = resources.getColorStateList(R.color.textgrey, null)
                    holder.btnCallAction.isEnabled = true
                    holder.btnCallAction.setOnClickListener {
                        showCancelDialog(session)
                    }
                }

                ButtonState.CONFIRM_CANCEL -> {
                    holder.btnCallAction.text = "Confirm"
                    holder.btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_red, null)
                    holder.sideBar.backgroundTintList = resources.getColorStateList(R.color.red, null)
                    holder.btnCallAction.isEnabled = true
                    holder.btnCallAction.setOnClickListener {
                        showConfirmCancelDialog(session)
                    }
                }

                ButtonState.CANCELLED_BY_ME -> {
                    holder.btnCallAction.text = "Cancelled"
                    holder.btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_grey, null)
                    holder.sideBar.backgroundTintList = resources.getColorStateList(R.color.textgrey, null)
                    holder.btnCallAction.isEnabled = false
                }
            }
        }

        private fun showCancelDialog(session: PeerToPeerSession) {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_cancelsession, null)
            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create()

            val contentText = dialogView.findViewById<TextView>(R.id.content)
            val reasonInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reason)
            val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btncancel)
            val btnKeep = dialogView.findViewById<MaterialButton>(R.id.btnkeep)

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("account_details").document(session.studentUid).get()
                .addOnSuccessListener { doc ->
                    val studentName = doc.getString("displayName") ?: "the student"
                    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val startDate = session.start_time?.toDate()
                    val endTime = session.end_time?.toDate()

                    val dateStr = startDate?.let { dateFormat.format(it) } ?: "Unknown"
                    val startTimeStr = startDate?.let { timeFormat.format(it) } ?: "0:00 AM"
                    val endTimeStr = endTime?.let { timeFormat.format(it) } ?: "0:00 AM"

                    contentText.text = "Are you sure you want to cancel this Session with $studentName on $dateStr for $startTimeStr - $endTimeStr?"
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch student details for dialog", e)
                }

            btnConfirm.setOnClickListener {
                val reason = reasonInput.text.toString().trim()
                if (reason.isNotEmpty()) {
                    val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    firestore.collection("peertopeer_session")
                        .document(session.sessionId)
                        .update(mapOf(
                            "isCancelled" to true,
                            "cancellationReason" to reason,
                            "cancellationConfirmed" to false,
                            "cancelledBy" to currentUid
                        ))
                        .addOnSuccessListener {
                            dialog.dismiss()
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Session cancelled",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to cancel session ${session.sessionId}", e)
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Failed to cancel session",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Please enter a reason",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }

            btnKeep.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }

        private fun showConfirmCancelDialog(session: PeerToPeerSession) {
            val dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.popup_confirmcancel, null)
            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create()

            val contentText = dialogView.findViewById<TextView>(R.id.content)
            val reasonText = dialogView.findViewById<TextView>(R.id.reason)
            val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btncancel)
            val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnkeep)

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("account_details").document(session.studentUid).get()
                .addOnSuccessListener { doc ->
                    val studentName = doc.getString("displayName") ?: "the student"
                    contentText.text = "By clicking confirm, you understand that $studentName has cancelled their session with you due to the following reason :"
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to fetch student details for confirm dialog", e)
                }

            val reason = session.cancellationReason.ifEmpty { "No reason provided." }
            reasonText.text = "Cancellation reason:\n$reason"

            btnConfirm.setOnClickListener {
                firestore.collection("peertopeer_session")
                    .document(session.sessionId)
                    .update("cancellationConfirmed", true)
                    .addOnSuccessListener {
                        dialog.dismiss()
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Cancellation confirmed",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to confirm cancellation ${session.sessionId}", e)
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to confirm cancellation",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            }

            btnCancel.setOnClickListener {
                val reason = reasonText.text.toString().trim()
                if (reason.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Cannot cancel without a reason",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                firestore.collection("peertopeer_session")
                    .document(session.sessionId)
                    .update(
                        mapOf(
                            "isCancelled" to true,
                            "cancellationReason" to reason,
                            "cancellationConfirmed" to false,
                            "cancelledBy" to currentUid
                        )
                    ).addOnSuccessListener {
                        dialog.dismiss()
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Session Cancelled",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to cancel session ${session.sessionId}", e)
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to cancel session",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            }

            dialog.show()
        }

        override fun getItemCount() = sessions.size

        fun updateSessions(newSessions: List<PeerToPeerSession>) {
            sessions = newSessions
            notifyDataSetChanged()
        }
    }

    enum class ButtonState {
        NOW,
        TODAY,
        CANCEL,
        CONFIRM_CANCEL,
        CANCELLED_BY_ME
    }
}
