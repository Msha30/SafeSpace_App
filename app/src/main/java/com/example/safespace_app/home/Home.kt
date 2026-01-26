package com.example.safespace_app.home

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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.safespace_app.Announcement
import com.example.safespace_app.PeerToPeerSession
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

        notificationsRecyclerView = view.findViewById(R.id.notifications)
        notificationAdapter = NotificationAdapter(listOf())
        notificationsRecyclerView.adapter = notificationAdapter
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        loadAnnouncements()

        val currentStudentUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentStudentUid != null) {
            observeUpcomingSessions(currentStudentUid)
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

    private fun observeUpcomingSessions(studentUid: String) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("peertopeer_session")
            .whereEqualTo("studentUid", studentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    android.util.Log.e("Home", "Error loading sessions", error)
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                val sessions = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(PeerToPeerSession::class.java)?.copy(sessionId = doc.id)
                }.filter { session ->
                    // Show if cancellationConfirmed is NOT true AND end time hasn't passed
                    (session.cancellationConfirmed != true) &&
                            (session.end_time?.toDate()?.time ?: 0) > now
                }.sortedByDescending { it.start_time?.toDate()?.time ?: 0 }.reversed()

                upcomingAdapter.updateSessions(sessions)
                emptyText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                upcomingRecyclerView.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
            }
    }

    inner class HalfScreenWidthItemDecoration(private val spacing: Int) :
        RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            outRect.left = if (position == 0) 0 else spacing
        }
    }

    inner class UpcomingAdapter(
        private var sessions: List<PeerToPeerSession> = listOf()
    ) : RecyclerView.Adapter<UpcomingAdapter.CardViewHolder>() {

        inner class CardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val topBar: View = view.findViewById(R.id.top)
            val profileImage: ShapeableImageView = view.findViewById(R.id.profileImage)
            val nameText: TextView = view.findViewById(R.id.nameText)
            val infoText: TextView = view.findViewById(R.id.infoText)
            val btnCallAction: MaterialButton = view.findViewById(R.id.btnCallAction)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_upcoming, parent, false)
            return CardViewHolder(view)
        }

        fun updateSessions(newSessions: List<PeerToPeerSession>) {
            sessions = newSessions
            notifyDataSetChanged()
        }

        override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
            val session = sessions[position]

            // Set item width to show 2 items at a time
            val params = holder.itemView.layoutParams
            params.width =
                (holder.itemView.context.resources.displayMetrics.widthPixels * 0.42).toInt()
            holder.itemView.layoutParams = params

            // Load Peer Name and Avatar
            loadUserDetails(session.peerUid, holder.profileImage, holder.nameText)

            // Determine button state FIRST
            val buttonState = determineButtonState(session)

            // Format info text based on cancellation status
            if (session.isCancelled == true) {
                holder.infoText.text = session.cancellationReason
            } else {
                holder.infoText.text = formatSessionInfo(session)
            }

            configureButton(holder, session, buttonState)
        }

        private fun loadUserDetails(
            uid: String,
            imageView: ShapeableImageView,
            nameView: TextView
        ) {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("account_details")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    val displayName = doc.getString("displayName") ?: "Peer"
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
                    }
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

            android.util.Log.d("ButtonState", "=== Determining Button State ===")
            android.util.Log.d("ButtonState", "Session ID: ${session.sessionId}")
            android.util.Log.d("ButtonState", "isCancelled: ${session.isCancelled}")
            android.util.Log.d("ButtonState", "cancelledBy: '${session.cancelledBy}'")

            // EXPLICIT CHECK: isCancelled == true (not just isCancelled)
            if (session.isCancelled == true) {
                val state = if (session.cancelledBy == currentUid) {
                    android.util.Log.d("ButtonState", "Result: CANCELLED_BY_ME")
                    ButtonState.CANCELLED_BY_ME
                } else {
                    android.util.Log.d("ButtonState", "Result: CONFIRM_CANCEL")
                    ButtonState.CONFIRM_CANCEL
                }
                return state
            }

            android.util.Log.d("ButtonState", "Not cancelled, checking time...")

            val now = Calendar.getInstance()
            val start = session.start_time?.toDate() ?: return ButtonState.CANCEL
            val end = session.end_time?.toDate() ?: return ButtonState.CANCEL

            val startCal = Calendar.getInstance().apply { time = start }
            val endCal = Calendar.getInstance().apply { time = end }

            return when {
                now.timeInMillis in startCal.timeInMillis..endCal.timeInMillis -> {
                    android.util.Log.d("ButtonState", "Result: NOW")
                    ButtonState.NOW
                }
                isSameDay(now, startCal) && now.timeInMillis < startCal.timeInMillis -> {
                    android.util.Log.d("ButtonState", "Result: TODAY")
                    ButtonState.TODAY
                }
                else -> {
                    android.util.Log.d("ButtonState", "Result: CANCEL")
                    ButtonState.CANCEL
                }
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun configureButton(
            holder: CardViewHolder,
            session: PeerToPeerSession,
            state: ButtonState
        ) {
            holder.btnCallAction.setOnClickListener(null)

            when (state) {
                ButtonState.NOW -> {
                    holder.btnCallAction.text = "Now"
                    holder.btnCallAction.background =
                        resources.getDrawable(R.drawable.f_rounded_green, null)
                    holder.topBar.backgroundTintList =
                        resources.getColorStateList(R.color.green, null)
                    holder.btnCallAction.isEnabled = false
                }

                ButtonState.TODAY -> {
                    holder.btnCallAction.text = "Today"
                    holder.btnCallAction.background =
                        resources.getDrawable(R.drawable.f_rounded_blue, null)
                    holder.topBar.backgroundTintList =
                        resources.getColorStateList(R.color.blue, null)
                    holder.btnCallAction.isEnabled = false
                }

                ButtonState.CANCEL -> {
                    holder.btnCallAction.text = "Cancel"
                    holder.btnCallAction.background =
                        resources.getDrawable(R.drawable.f_rounded_grey, null)
                    holder.topBar.backgroundTintList =
                        resources.getColorStateList(R.color.textgrey, null)
                    holder.btnCallAction.isEnabled = true
                    holder.btnCallAction.setOnClickListener {
                        showCancelDialog(session)
                    }
                }

                ButtonState.CONFIRM_CANCEL -> {
                    holder.btnCallAction.text = "Confirm"
                    holder.btnCallAction.background =
                        resources.getDrawable(R.drawable.f_rounded_red, null)
                    holder.topBar.backgroundTintList =
                        resources.getColorStateList(R.color.red, null)
                    holder.btnCallAction.isEnabled = true
                    holder.btnCallAction.setOnClickListener {
                        showConfirmCancelDialog(session)
                    }
                }

                ButtonState.CANCELLED_BY_ME -> {
                    holder.btnCallAction.text = "Cancelled"
                    holder.btnCallAction.background =
                        resources.getDrawable(R.drawable.f_rounded_grey, null)
                    holder.topBar.backgroundTintList =
                        resources.getColorStateList(R.color.textgrey, null)
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
            val reasonInput =
                dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.reason)
            val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btncancel)
            val btnKeep = dialogView.findViewById<MaterialButton>(R.id.btnkeep)

            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("account_details").document(session.peerUid).get()
                .addOnSuccessListener { doc ->
                    val peerName = doc.getString("displayName") ?: "the peer"
                    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val startDate = session.start_time?.toDate()
                    val endTime = session.end_time?.toDate()

                    val dateStr = startDate?.let { dateFormat.format(it) } ?: "Unknown"
                    val startTimeStr = startDate?.let { timeFormat.format(it) } ?: "0:00 AM"
                    val endTimeStr = endTime?.let { timeFormat.format(it) } ?: "0:00 AM"

                    contentText.text =
                        "Are you sure you want to cancel this Session with $peerName on $dateStr for $startTimeStr - $endTimeStr?"
                }

            btnConfirm.setOnClickListener {
                val reason = reasonInput.text.toString().trim()
                if (reason.isNotEmpty()) {
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
                        )
                        .addOnSuccessListener {
                            dialog.dismiss()
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Session cancelled",
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
            firestore.collection("account_details").document(session.peerUid).get()
                .addOnSuccessListener { doc ->
                    val peerName = doc.getString("displayName") ?: "the peer"
                    contentText.text =
                        "By clicking confirm, you understand that $peerName has cancelled their session with you due to the following reason :"
                }

            reasonText.text = session.cancellationReason

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
                    }
            }
            dialog.show()
        }

        override fun getItemCount() = sessions.size
    }

    enum class ButtonState {
        NOW,
        TODAY,
        CANCEL,
        CONFIRM_CANCEL,
        CANCELLED_BY_ME
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

            when (announcement.represented_by.uppercase()) {
                "GCO" -> holder.photo.setImageResource(R.drawable.pfp_gco)
                "PEERS" -> holder.photo.setImageResource(R.drawable.pfp_peers)
                else -> holder.photo.setImageResource(R.drawable.img_placeholder)
            }

            if (announcement.photo_urls.isNotEmpty()) {
                holder.photoRow.visibility = View.VISIBLE
                val photoAdapter = PhotoDisplayAdapter(announcement.photo_urls)
                val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(
                    holder.itemView.context, 3
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