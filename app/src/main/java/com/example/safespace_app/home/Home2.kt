package com.example.safespace_app.home

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.Announcement
import com.example.safespace_app.PeerSession
import com.example.safespace_app.R
import com.example.safespace_app.UserCache
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class Home2 : Fragment() {

    private val viewModel: Home2ViewModel by viewModels()

    private lateinit var upcomingRecyclerView: RecyclerView
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var upcomingAdapter: UpcomingAdapter
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var emptyText: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home2, container, false)

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

        return view
    }

    private fun loadAnnouncements() {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("announcements")
            .orderBy("date_created", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Home2", "Error loading announcements", error)
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
            // Filter sessions: only CONFIRMED sessions for this peer
            val upcomingSessions = sessions.filter {
                it.status == "confirmed" &&
                        !it.sessionComplete
            }
            upcomingAdapter.updateSessions(upcomingSessions)

            // Toggle empty view
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

            // Show call button for video/voice calls
            if (session.preferredMode == "Video Call" || session.preferredMode == "Call") {
                holder.btnCallAction?.visibility = View.VISIBLE

                // Update button based on call status
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

            // Check if call is already active
            if (session.callStatus == "active" && session.callInitiatorUid != null) {
                // Call is active, JOIN as receiver
                val isInitiator = false

            } else {
                // Call not active, START as initiator
                val isInitiator = true

                // Update call status atomically to prevent race condition
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

                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("Home2", "Failed to update call status", e)
                        android.widget.Toast.makeText(
                            requireContext(),
                            "Failed to start call",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }

        private fun updateCallStatus(session: PeerSession, status: String, initiatorUid: String) {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            firestore.collection("peer_session_requests")
                .document(session.sessionId)
                .update(mapOf(
                    "callStatus" to status,
                    "callInitiatorUid" to initiatorUid
                ))
                .addOnSuccessListener {
                    session.callStatus = status
                    session.callInitiatorUid = initiatorUid
                    notifyDataSetChanged()
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

        val currentPeerUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentPeerUid != null) {
            UserCache.loadActiveSessionsForUser(peerUid = currentPeerUid)
            observeUpcomingSessions()
        } else {
            emptyText.visibility = View.VISIBLE
            upcomingRecyclerView.visibility = View.GONE
        }

        val btnschedule = view.findViewById<ShapeableImageView>(R.id.counseling)
        val btnevent = view.findViewById<ImageView>(R.id.addEvent)

        btnevent.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homeNewEvent)
        }

        btnschedule.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homeSchedule)
        }
    }
}