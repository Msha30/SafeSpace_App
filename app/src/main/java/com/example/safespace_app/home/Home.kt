package com.example.safespace_app.home

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.CallActivity
import com.example.safespace_app.PeerSession
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.example.safespace_app.Announcement
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Home : Fragment() {

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var upcomingRecyclerView: RecyclerView
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var upcomingAdapter: UpcomingAdapter
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var emptyText: TextView

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
        UserCache.activeSessionsLiveData.observe(viewLifecycleOwner) { sessions ->
            val currentStudentUid = FirebaseAuth.getInstance().currentUser?.uid
            val upcomingSessions = sessions.filter {
                it.studentUid == currentStudentUid &&
                        it.status == "confirmed" &&
                        !it.sessionComplete
            }
            upcomingAdapter.updateSessions(upcomingSessions)

            if (upcomingSessions.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                upcomingRecyclerView.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                upcomingRecyclerView.visibility = View.VISIBLE
            }
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

    inner class UpcomingAdapter(private var sessions: List<PeerSession>) :
        RecyclerView.Adapter<UpcomingAdapter.CardViewHolder>() {

        inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dateText: TextView = view.findViewById(R.id.dateText)
            val timeText: TextView = view.findViewById(R.id.timeText)
            val btnCallAction: MaterialButton? = view.findViewById(R.id.btnCallAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_upcoming, parent, false)
            return CardViewHolder(view)
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val session = sessions[position]
            holder.dateText.text = session.selectedDate
            holder.timeText.text = session.selectedTimeSlot

            val params = holder.itemView.layoutParams
            params.width =
                (holder.itemView.context.resources.displayMetrics.widthPixels * 0.42).toInt()
            holder.itemView.layoutParams = params

            if (session.preferredMode == "Video Call" || session.preferredMode == "Call") {
                holder.btnCallAction?.visibility = View.VISIBLE

                when (session.callStatus) {
                    "active" -> {
                        holder.btnCallAction?.text = "Join Call"
                        holder.btnCallAction?.setBackgroundResource(R.drawable.f_rounded_green)
                    }
                    else -> {
                        holder.btnCallAction?.text = "Start Call"
                        holder.btnCallAction?.setBackgroundResource(R.drawable.f_rounded_blue)
                    }
                }

                holder.btnCallAction?.setOnClickListener {
                    startOrJoinCall(session)
                }
            } else {
                holder.btnCallAction?.visibility = View.GONE
            }
        }

        private fun startOrJoinCall(session: PeerSession) {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val isVideoCall = session.preferredMode == "Video Call"

            if (session.callStatus == "active" && session.callInitiatorUid != null) {
                val isInitiator = false

                UserCache.getUserDetails(session.peerUid) { peerName, _ ->
                    val intent = Intent(requireContext(), CallActivity::class.java).apply {
                        putExtra(CallActivity.EXTRA_SESSION_ID, session.sessionId)
                        putExtra(CallActivity.EXTRA_PEER_NAME, peerName)
                        putExtra(CallActivity.EXTRA_IS_VIDEO_CALL, isVideoCall)
                        putExtra(CallActivity.EXTRA_IS_INITIATOR, isInitiator)
                    }
                    startActivity(intent)
                }
            } else {
                val isInitiator = true

                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                firestore.collection("peer_session_requests")
                    .document(session.sessionId)
                    .update(mapOf(
                        "callStatus" to "active",
                        "callInitiatorUid" to currentUid
                    ))
                    .addOnSuccessListener {
                        session.callStatus = "active"
                        session.callInitiatorUid = currentUid
                        notifyDataSetChanged()

                        UserCache.getUserDetails(session.peerUid) { peerName, _ ->
                            val intent = Intent(requireContext(), CallActivity::class.java).apply {
                                putExtra(CallActivity.EXTRA_SESSION_ID, session.sessionId)
                                putExtra(CallActivity.EXTRA_PEER_NAME, peerName)
                                putExtra(CallActivity.EXTRA_IS_VIDEO_CALL, isVideoCall)
                                putExtra(CallActivity.EXTRA_IS_INITIATOR, isInitiator)
                            }
                            startActivity(intent)
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("Home", "Failed to update call status", e)
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to start call",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        override fun getItemCount() = sessions.size

        fun updateSessions(newSessions: List<PeerSession>) {
            sessions = newSessions
            notifyDataSetChanged()
        }
    }

    inner class NotificationAdapter(private var announcements: List<Announcement>) :
        RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

        inner class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val photo: ShapeableImageView = view.findViewById(R.id.photo)
            val title: TextView = view.findViewById(R.id.title)
            val content: TextView = view.findViewById(R.id.content)
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
                "GCO" -> holder.photo.setImageResource(R.drawable.pfp_gco) // Replace with your actual drawable
                "PEERS" -> holder.photo.setImageResource(R.drawable.pfp_peers) // Replace with your actual drawable
                else -> holder.photo.setImageResource(R.drawable.img_placeholder)
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