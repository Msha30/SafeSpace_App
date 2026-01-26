package com.example.safespace_app.home

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.safespace_app.Announcement
import com.example.safespace_app.PeerToPeerSession
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Home2 : Fragment() {

    private val viewModel: Home2ViewModel by viewModels()
    private lateinit var upcomingRecyclerView: RecyclerView
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var upcomingAdapter: UpcomingAdapter
    private lateinit var notificationAdapter: NotificationAdapter
    private lateinit var emptyText: TextView

    private var sessionsListener: ListenerRegistration? = null
    private val TAG = "Home2"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home2, container, false)

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

        val currentPeerUid = FirebaseAuth.getInstance().currentUser?.uid
        if (currentPeerUid != null) {
            observeUpcomingSessions(currentPeerUid)
        } else {
            emptyText.visibility = View.VISIBLE
            upcomingRecyclerView.visibility = View.GONE
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up Firestore listeners to avoid leaks
        sessionsListener?.remove()
        sessionsListener = null
    }

    private fun loadAnnouncements() {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("announcements")
            .orderBy("date_created", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error loading announcements", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val announcements = snapshot.toObjects(Announcement::class.java)
                    notificationAdapter.updateAnnouncements(announcements)
                }
            }
    }

    /**
     * Observe sessions for the current peer. Important:
     * - Map boolean fields directly from the snapshot (doc.getBoolean) to avoid relying
     *   on toObject() defaults when a field is missing or has different type/casing.
     * - Keep sessions where cancellationConfirmed == false (so peer can confirm a cancellation).
     */
    private fun observeUpcomingSessions(peerUid: String) {
        val firestore = FirebaseFirestore.getInstance()
        sessionsListener?.remove()

        sessionsListener = firestore.collection("peertopeer_session")
            .whereEqualTo("peerUid", peerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    Log.e(TAG, "Error loading sessions", error)
                    return@addSnapshotListener
                }

                // DEBUG: raw snapshot for quick inspection if something looks off
                snapshot.documents.forEach { doc ->
                    Log.d(TAG, "RAW Firestore: ${doc.id} -> ${doc.data}")
                }

                val now = System.currentTimeMillis()

                val sessions = snapshot.documents.mapNotNull { doc ->
                    try {
                        // Read fields defensively using typed getters
                        val sessionId = doc.id
                        val studentUid = doc.getString("studentUid") ?: ""
                        val peerUidField = doc.getString("peerUid") ?: ""
                        val startTs = doc.get("start_time") as? com.google.firebase.Timestamp
                        val endTs = doc.get("end_time") as? com.google.firebase.Timestamp
                        val location = doc.getString("location") ?: ""
                        val dateSubmitted = doc.get("date_submitted") as? com.google.firebase.Timestamp

                        // IMPORTANT: read booleans directly from snapshot to avoid defaulting errors
                        val isCancelled = doc.getBoolean("isCancelled") ?: false
                        val cancellationReason = doc.getString("cancellationReason") ?: ""
                        val cancellationConfirmed = doc.getBoolean("cancellationConfirmed") ?: false
                        val cancelledBy = doc.getString("cancelledBy") ?: ""

                        // Build model using copy to ensure data class fields have the right values
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
                }
                    // Filter out confirmed cancellations only (we want to keep isCancelled==true
                    // when cancellationConfirmed==false so the peer can confirm).
                    .filter { session ->
                        val endTime = session.end_time?.toDate()?.time ?: 0L
                        val isNotPast = endTime > now
                        // keep only not-confirmed cancellations, and not past sessions
                        !session.cancellationConfirmed && isNotPast
                    }
                    .sortedByDescending { it.start_time?.toDate()?.time ?: 0L }

                upcomingAdapter.updateSessions(sessions)
                emptyText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
                upcomingRecyclerView.visibility =
                    if (sessions.isEmpty()) View.GONE else View.VISIBLE
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

            // Load Student Name and Avatar (defensive: check tag to avoid recycled view issues)
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

        private fun loadUserDetails(
            uid: String,
            imageView: ShapeableImageView,
            nameView: TextView
        ) {
            val firestore = FirebaseFirestore.getInstance()
            // Attach uid as tag so recycled view won't display wrong data
            nameView.tag = uid
            imageView.tag = uid

            firestore.collection("account_details")
                .document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    // Ensure view hasn't been recycled for another uid
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
                    // Ensure placeholder if view still the same uid
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

            // Check if session is cancelled (student clicked cancel). If so, peer must confirm.
            if (session.isCancelled) {
                Log.d(TAG, "Session is cancelled (student requested): ${session.sessionId}")
                return if (session.cancelledBy == currentUid) {
                    ButtonState.CANCELLED_BY_ME
                } else {
                    ButtonState.CONFIRM_CANCEL
                }
            } else {
                val now = Calendar.getInstance()
                val start = session.start_time?.toDate()
                val end = session.end_time?.toDate()

                // If no start/end available, show Cancel (safe fallback)
                if (start == null || end == null) return ButtonState.CANCEL

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

                    contentText.text =
                        "Are you sure you want to cancel this Session with $studentName on $dateStr for $startTimeStr - $endTimeStr?"
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
                    contentText.text =
                        "By clicking confirm, you understand that $studentName has cancelled their session with you due to the following reason :"
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
                        Log.e(TAG, "Failed to cancel session on cancel button ${session.sessionId}", e)
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

            // Set default/placeholder first
            holder.photo.setImageResource(R.drawable.img_placeholder)

            // Handle GCO / PEERS static icons
            val repr = announcement.represented_by?.trim()
            if (!repr.isNullOrEmpty()) {
                val reprUpper = repr.uppercase(Locale.getDefault())
                when (reprUpper) {
                    "GCO" -> {
                        holder.photo.setImageResource(R.drawable.pfp_gco)
                    }
                    "PEERS" -> {
                        holder.photo.setImageResource(R.drawable.pfp_peers)
                    }
                    else -> {
                        // Asynchronous fetch from Firestore using the represented_by value as document id.
                        // Use tag on the ImageView to avoid incorrect images when ViewHolder is recycled.
                        holder.photo.tag = repr
                        fetchGroupPfpAndLoad(repr, holder)
                    }
                }
            } else {
                holder.photo.setImageResource(R.drawable.img_placeholder)
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

        private fun fetchGroupPfpAndLoad(groupId: String, holder: NotificationViewHolder) {
            val db = FirebaseFirestore.getInstance()
            // set placeholder while loading
            holder.photo.setImageResource(R.drawable.img_placeholder)

            db.collection("supportgroup").document(groupId)
                .get()
                .addOnSuccessListener { snapshot ->
                    // ViewHolder may have been recycled: ensure tag still matches this groupId
                    if (holder.photo.tag != groupId) return@addOnSuccessListener

                    val pfpUrl = snapshot.getString("supportgroup_pfp_URL")
                    if (pfpUrl.isNullOrBlank()) {
                        holder.photo.setImageResource(R.drawable.img_placeholder)
                    } else {
                        Glide.with(holder.itemView.context)
                            .load(pfpUrl)
                            .placeholder(R.drawable.img_placeholder)
                            .error(R.drawable.img_placeholder)
                            .circleCrop()
                            .into(holder.photo)
                    }
                }
                .addOnFailureListener {
                    // Only set placeholder if still the same item
                    if (holder.photo.tag == groupId) {
                        holder.photo.setImageResource(R.drawable.img_placeholder)
                    }
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

        val btnschedule = view.findViewById<ShapeableImageView>(R.id.counseling)
        val btnevent = view.findViewById<ImageView>(R.id.addEvent)

        btnevent.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homeNewEvent)
        }

        btnschedule.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home2_to_homeSessionManagement)
        }
    }
}
