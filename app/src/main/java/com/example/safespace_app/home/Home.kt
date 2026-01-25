package com.example.safespace_app.home

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.example.safespace_app.Announcement
import com.example.safespace_app.CallActivity
import com.example.safespace_app.CounselingSession
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Home : Fragment() {

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var upcomingRecyclerView: RecyclerView
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var upcomingAdapter: UpcomingAdapter
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var emptyText: TextView

    // RESTORED: Map to track active WebRTC calls (Student side)
    private val activeCallsMap = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        emptyText = view.findViewById(R.id.empty)

        // --- Upcoming RecyclerView ---
        upcomingRecyclerView = view.findViewById(R.id.upcoming)
        upcomingAdapter = UpcomingAdapter(listOf())
        upcomingRecyclerView.adapter = upcomingAdapter
        upcomingRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        val spacingInDp = 15f
        val spacingInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            spacingInDp,
            resources.displayMetrics
        ).toInt()
        upcomingRecyclerView.addItemDecoration(HalfScreenWidthItemDecoration(spacingInPixels))

        // --- Notifications RecyclerView ---
        notificationsRecyclerView = view.findViewById(R.id.notifications)
        notificationAdapter = NotificationAdapter(listOf())
        notificationsRecyclerView.adapter = notificationAdapter
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Load announcements
        loadAnnouncements()

        // Load student sessions
        val currentStudentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentStudentUid != null) {
            UserCache.loadActiveSessionsForUser(studentUid = currentStudentUid)
            observeUpcomingSessions()
        } else {
            emptyText.visibility = View.VISIBLE
            upcomingRecyclerView.visibility = View.GONE
        }

        return view
    }

    private fun loadAnnouncements() {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("announcements")
            .orderBy("date_created", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Home", "Error loading announcements", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val announcements = snapshot.toObjects(Announcement::class.java)
                    notificationAdapter.updateAnnouncements(announcements)
                }
            }
    }

    private fun observeUpcomingSessions() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        // 1. Listener for Submissions (Student view of their own sessions)
        firestore.collection("CounselingForm_Submissions")
            .whereEqualTo("createdBy", currentUid)
            // Listen for active sessions
            .whereIn("status", listOf("assigned", "in_progress"))
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val sessions = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CounselingSession::class.java)?.copy(id = doc.id)
                }

                // 2. Update adapter with sessions AND active call map
                upcomingAdapter.updateSessions(sessions, activeCallsMap)

                emptyText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                upcomingRecyclerView.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
            }

        // 3. RESTORED: Listener for Calls (Detect incoming calls from Web)
        observeActiveCalls()
    }

    // RESTORED: Function to listen for calls
    private fun observeActiveCalls() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("calls")
            // Listen for calls intended for this user (Student is createdBy)
            .whereEqualTo("createdBy", currentUid)
            .whereEqualTo("status", "ringing") // Only active calls
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    android.util.Log.e("Home", "Error listening to calls", error)
                    return@addSnapshotListener
                }

                activeCallsMap.clear()
                for (doc in snapshot.documents) {
                    val submissionId = doc.getString("submissionId")
                    val callId = doc.id
                    if (submissionId != null) {
                        activeCallsMap[submissionId] = callId
                    }
                }

                // Refresh adapter to show/hide "Join Call" buttons
                upcomingAdapter.notifyDataSetChanged()
            }
    }

    inner class HalfScreenWidthItemDecoration(private val spacing: Int) :
        RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            outRect.left = if (position == 0) 0 else spacing
        }
    }

    private fun formatTime(date: java.util.Date): String {
        val formatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return formatter.format(date)
    }

    inner class UpcomingAdapter(
        private var sessions: List<CounselingSession> = listOf()
    ) : RecyclerView.Adapter<UpcomingAdapter.CardViewHolder>() {

        inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dateText: TextView = view.findViewById(R.id.dateText)
            val timeText: TextView = view.findViewById(R.id.timeText)
            val btnCallAction: MaterialButton? = view.findViewById(R.id.btnCallAction)
            val titleText: TextView? = view.findViewById(R.id.titleText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_upcoming, parent, false)
            return CardViewHolder(view)
        }

        // Updated function to accept calls map
        fun updateSessions(newSessions: List<CounselingSession>, callsMap: Map<String, String>) {
            sessions = newSessions
            activeCallsMap.clear()
            activeCallsMap.putAll(callsMap)
            notifyDataSetChanged()
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val session = sessions[position]
            holder.titleText?.text = session.title ?: "Counseling Session"
            holder.dateText.text = tryFormatDateToShort(session.assigned_sched?.date)

            val start = session.assigned_sched?.start?.toDate()
            val end = session.assigned_sched?.end?.toDate()

            holder.timeText.text = if (start != null && end != null) {
                "${formatTime(start)} - ${formatTime(end)}"
            } else {
                "—"
            }

            holder.btnCallAction?.visibility = View.VISIBLE

            // --- LOGIC FIX HERE ---

            val isFaceToFace = session.preferredPlatform?.trim().equals("face to face", ignoreCase = true) ||
                    session.preferredPlatform?.trim().equals("in-person", ignoreCase = true)

            when (session.status) {
                "in_progress" -> {
                    if (isFaceToFace) {
                        // 1. Face-to-face in progress: No Start Call button
                        holder.btnCallAction?.text = "In Session"
                        holder.btnCallAction?.setBackgroundResource(R.drawable.f_rounded_green)
                        holder.btnCallAction?.isEnabled = false
                        holder.btnCallAction?.setOnClickListener {
                            android.util.Log.i("Home", "Face-to-face session is active")
                        }
                    } else {
                        // 2. Video/Audio in progress
                        // Check active call map for "Join Call" capability
                        val callId = activeCallsMap[session.id]
                        if (callId != null) {
                            // 2a. Call is active: Join Call
                            holder.btnCallAction?.text = "Join Call"
                            holder.btnCallAction?.setBackgroundResource(R.drawable.f_rounded_green)
                            holder.btnCallAction?.isEnabled = true
                            holder.btnCallAction?.setOnClickListener {
                                joinCall(callId, session.id)
                            }
                        } else {
                            // 2b. Call is NOT active (just waiting): In Progress
                            holder.btnCallAction?.text = "In Progress"
                            holder.btnCallAction?.setBackgroundResource(R.drawable.f_rounded_grey)
                            holder.btnCallAction?.isEnabled = false
                            holder.btnCallAction?.setOnClickListener {
                                Toast.makeText(
                                    holder.itemView.context,
                                    "Waiting for counselor to start...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
                "assigned" -> {
                    // 3. Session taken, waiting for counselor to start
                    holder.btnCallAction?.text = "Scheduled"
                    holder.btnCallAction?.setBackgroundResource(R.drawable.f_rounded_blue)
                    holder.btnCallAction?.isEnabled = false
                    holder.btnCallAction?.setOnClickListener { /* Do nothing or show info */ }
                }
                else -> { // "pending" or others
                    holder.btnCallAction?.text = "Pending"
                    holder.btnCallAction?.setBackgroundResource(R.drawable.f_rounded_green)
                    holder.btnCallAction?.isEnabled = false
                }
            }
        }

        // RESTORED: Navigate to CallActivity (Only for WebRTC calls)
        private fun joinCall(callId: String, submissionId: String) {
            val intent = Intent(requireContext(), CallActivity::class.java)
            intent.putExtra("CALL_ID", callId)
            intent.putExtra("SUBMISSION_ID", submissionId)
            startActivity(intent)
        }

        override fun getItemCount() = sessions.size

        private fun tryFormatDateToShort(raw: String?): String {
            if (raw.isNullOrBlank()) return "Date TBD"
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val d = parser.parse(raw)
                if (d != null) {
                    val out = SimpleDateFormat("MMM d", Locale.getDefault())
                    out.format(d)
                } else raw
            } catch (e: Exception) {
                raw
            }
        }
    }

    inner class NotificationAdapter(private var announcements: List<Announcement>) :
        RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

        inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photo: ShapeableImageView = view.findViewById(R.id.photo)
            val title: TextView = view.findViewById(R.id.title)
            val content: TextView = view.findViewById(R.id.content)
            val photoRow: RecyclerView = view.findViewById(R.id.photorow)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notif, parent, false)
            return NotificationViewHolder(view)
        }

        override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
            val announcement = announcements[position]

            holder.title.text = announcement.title
            holder.content.text = announcement.description

            // Set image based on represented_by
            when (announcement.represented_by.uppercase()) {
                "GCO" -> holder.photo.setImageResource(R.drawable.pfp_gco)
                "PEERS" -> holder.photo.setImageResource(R.drawable.pfp_peers)
                else -> holder.photo.setImageResource(R.drawable.img_placeholder)
            }
            // Handle photo display
            if (announcement.photo_urls.isNotEmpty()) {
                holder.photoRow.visibility = View.VISIBLE

                val photoAdapter = PhotoDisplayAdapter(announcement.photo_urls)

                val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(
                    holder.itemView.context,
                    3
                )

                gridLayoutManager.spanSizeLookup =
                    object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return if (photoAdapter.totalPhotos == 1) 3 else 1
                        }
                    }

                holder.photoRow.layoutManager = gridLayoutManager
                holder.photoRow.adapter = photoAdapter

            } else {
                holder.photoRow.visibility = View.GONE
            }
        }

        override fun getItemCount() = announcements.size

        fun updateAnnouncements(newAnnouncements: List<Announcement>) {
            announcements = newAnnouncements
            notifyDataSetChanged()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnCounseling = view.findViewById<ShapeableImageView>(R.id.counseling)

        btnCounseling.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_homeCounseling)
        }
    }
}