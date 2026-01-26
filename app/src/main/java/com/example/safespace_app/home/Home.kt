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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.safespace_app.*
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class Home : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var upcomingRecyclerView: RecyclerView
    private lateinit var notificationsRecyclerView: RecyclerView
    private lateinit var emptyText: TextView

    // Unified adapter (shows both peer + counseling)
    private lateinit var mixedAdapter: UpcomingMixedAdapter

    // Notification adapter (unchanged)
    private lateinit var notificationAdapter: NotificationAdapter

    // Listeners to remove on destroy
    private var peerListener: ListenerRegistration? = null
    private var counselingListener: ListenerRegistration? = null
    private var callsListener: ListenerRegistration? = null
    private var announcementsListener: ListenerRegistration? = null

    // Local caches
    private val peerSessions = mutableListOf<PeerToPeerSession>()
    private val counselingSessions = mutableListOf<CounselingSession>()

    // For counseling call join logic (map submissionId -> callId)
    private val activeCallsMap = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        emptyText = view.findViewById(R.id.empty)

        // upcoming horizontal list
        upcomingRecyclerView = view.findViewById(R.id.upcoming)

        val spacingInDp = 15f
        val spacingInPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            spacingInDp,
            resources.displayMetrics
        ).toInt()
        upcomingRecyclerView.addItemDecoration(HalfScreenWidthItemDecoration(spacingInPixels))
        upcomingRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Create mixed adapter and attach
        mixedAdapter = UpcomingMixedAdapter()
        upcomingRecyclerView.adapter = mixedAdapter

        // notifications vertical list
        notificationsRecyclerView = view.findViewById(R.id.notifications)
        notificationAdapter = NotificationAdapter(listOf())
        notificationsRecyclerView.adapter = notificationAdapter
        notificationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // load announcements
        loadAnnouncements()

        // always observe both (merge)
        startObservingSessions()

        return view
    }

    private fun startObservingSessions() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            emptyText.visibility = View.VISIBLE
            upcomingRecyclerView.visibility = View.GONE
            return
        }

        observePeerSessions(currentUid)
        observeCounselingSessions(currentUid)
        observeActiveCalls(currentUid)
    }

    private fun observePeerSessions(studentUid: String) {
        val firestore = FirebaseFirestore.getInstance()
        peerListener = firestore.collection("peertopeer_session")
            .whereEqualTo("studentUid", studentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Home", "Error loading peer sessions", error)
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(PeerToPeerSession::class.java)?.copy(sessionId = doc.id)
                } ?: emptyList()

                peerSessions.clear()
                peerSessions.addAll(
                    sessions.filter { session ->
                        (session.cancellationConfirmed != true) &&
                                (session.end_time?.toDate()?.time ?: 0) > now
                    }
                )

                mergeAndDisplay()
            }
    }

    private fun observeCounselingSessions(currentUid: String) {
        val firestore = FirebaseFirestore.getInstance()

        // NOTE: If you get a "Missing Index" error in Logcat, click the link in the error to create the index in Firebase Console.
        counselingListener = firestore.collection("CounselingForm_Submissions")
            .whereEqualTo("createdBy", currentUid)
            .whereIn("status", listOf("assigned", "taken", "in_progress"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Home", "Error loading counseling sessions", error)
                    return@addSnapshotListener
                }

                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    val session = doc.toObject(CounselingSession::class.java)
                    session?.copy(id = doc.id)
                } ?: emptyList()

                counselingSessions.clear()
                counselingSessions.addAll(sessions)

                mergeAndDisplay()
            }
    }

    private fun observeActiveCalls(currentUid: String) {
        val firestore = FirebaseFirestore.getInstance()
        callsListener = firestore.collection("calls")
            .whereEqualTo("createdBy", currentUid)
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Home", "Error listening to calls", error)
                    return@addSnapshotListener
                }

                activeCallsMap.clear()
                snapshot?.documents?.forEach { doc ->
                    val submissionId = doc.getString("submissionId")
                    val callId = doc.id
                    if (submissionId != null && callId != null) {
                        activeCallsMap[submissionId] = callId
                    }
                }
                // Notify adapter to refresh the "Join Call" buttons
                mixedAdapter.notifyDataSetChanged()
            }
    }

    private fun mergeAndDisplay() {
        val items = mutableListOf<UpcomingItem>()

        peerSessions.forEach { items.add(UpcomingItem.Peer(it)) }
        counselingSessions.forEach { items.add(UpcomingItem.Counseling(it)) }

        // Sort by start time ascending (soonest first)
        items.sortWith(compareBy { it.sortTime })

        mixedAdapter.update(items)

        val isEmpty = items.isEmpty()
        emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
        upcomingRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun loadAnnouncements() {
        val firestore = FirebaseFirestore.getInstance()
        announcementsListener = firestore.collection("announcements")
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

    inner class HalfScreenWidthItemDecoration(private val spacing: Int) :
        RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            outRect.left = if (position == 0) 0 else spacing
        }
    }

    sealed class UpcomingItem {
        abstract val sortTime: Long
        data class Peer(val session: PeerToPeerSession) : UpcomingItem() {
            override val sortTime: Long = session.start_time?.toDate()?.time ?: Long.MAX_VALUE
        }
        data class Counseling(val session: CounselingSession) : UpcomingItem() {
            override val sortTime: Long = session.assigned_sched?.start?.toDate()?.time ?: Long.MAX_VALUE
        }
    }

    inner class UpcomingMixedAdapter(
        private var items: List<UpcomingItem> = listOf()
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_PEER = 1
        private val TYPE_COUNSELING = 2

        fun update(newItems: List<UpcomingItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is UpcomingItem.Peer -> TYPE_PEER
                is UpcomingItem.Counseling -> TYPE_COUNSELING
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_upcoming, parent, false)
            return when (viewType) {
                TYPE_PEER -> PeerVH(v)
                TYPE_COUNSELING -> CounselingVH(v)
                else -> throw IllegalStateException("Unknown view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = items[position]
            when {
                holder is PeerVH && item is UpcomingItem.Peer -> holder.bind(item.session)
                holder is CounselingVH && item is UpcomingItem.Counseling -> holder.bind(item.session)
            }
        }

        override fun getItemCount(): Int = items.size

        inner class PeerVH(view: View) : RecyclerView.ViewHolder(view) {
            private val profileImage: ShapeableImageView = view.findViewById(R.id.profileImage)
            private val nameText: TextView = view.findViewById(R.id.nameText)
            private val infoText: TextView = view.findViewById(R.id.infoText)
            private val btnCallAction: MaterialButton = view.findViewById(R.id.btnCallAction)
            private val topBar: View = view.findViewById(R.id.top)

            fun bind(session: PeerToPeerSession) {
                val params = itemView.layoutParams
                params.width = (itemView.context.resources.displayMetrics.widthPixels * 0.42).toInt()
                itemView.layoutParams = params

                loadUserDetails(session.peerUid ?: "", profileImage, nameText)
                infoText.text = formatPeerSessionInfo(session)

                val state = determinePeerButtonState(session)
                configurePeerButton(session, state)
            }

            private fun configurePeerButton(session: PeerToPeerSession, state: ButtonState) {
                btnCallAction.setOnClickListener(null)
                when (state) {
                    ButtonState.NOW -> {
                        btnCallAction.text = "Now"
                        btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_green, null)
                        topBar.backgroundTintList = resources.getColorStateList(R.color.green, null)
                        btnCallAction.isEnabled = false
                    }
                    ButtonState.TODAY -> {
                        btnCallAction.text = "Today"
                        btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_blue, null)
                        topBar.backgroundTintList = resources.getColorStateList(R.color.blue, null)
                        btnCallAction.isEnabled = false
                    }
                    ButtonState.CANCEL -> {
                        btnCallAction.text = "Cancel"
                        btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_grey, null)
                        topBar.backgroundTintList = resources.getColorStateList(R.color.textgrey, null)
                        btnCallAction.isEnabled = true
                        btnCallAction.setOnClickListener { showCancelDialog(session) }
                    }
                    ButtonState.CONFIRM_CANCEL -> {
                        btnCallAction.text = "Confirm"
                        btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_red, null)
                        topBar.backgroundTintList = resources.getColorStateList(R.color.red, null)
                        btnCallAction.isEnabled = true
                        btnCallAction.setOnClickListener { showConfirmCancelDialog(session) }
                    }
                    ButtonState.CANCELLED_BY_ME -> {
                        btnCallAction.text = "Cancelled"
                        btnCallAction.background = resources.getDrawable(R.drawable.f_rounded_grey, null)
                        topBar.backgroundTintList = resources.getColorStateList(R.color.textgrey, null)
                        btnCallAction.isEnabled = false
                    }
                }
            }

            private fun formatPeerSessionInfo(session: PeerToPeerSession): String {
                // Otherwise show normal date/time/location
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                val startDate = session.start_time?.toDate()
                val endTime = session.end_time?.toDate()
                val dateStr = startDate?.let { dateFormat.format(it) } ?: "Date TBD"
                val startTimeStr = startDate?.let { timeFormat.format(it) } ?: "0:00"
                val endTimeStr = endTime?.let { timeFormat.format(it) } ?: "0:00"
                val location = (session.location ?: "").ifEmpty { "Location TBD" }
                return "$dateStr\n$startTimeStr - $endTimeStr\n$location"
            }

            private fun determinePeerButtonState(session: PeerToPeerSession): ButtonState {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                if (session.isCancelled == true) {
                    return if (session.cancelledBy == currentUid) ButtonState.CANCELLED_BY_ME else ButtonState.CONFIRM_CANCEL
                }
                val now = Calendar.getInstance()
                val start = session.start_time?.toDate() ?: return ButtonState.CANCEL
                val end = session.end_time?.toDate() ?: return ButtonState.CANCEL
                val startCal = Calendar.getInstance().apply { time = start }
                val endCal = Calendar.getInstance().apply { time = end }
                return when {
                    now.timeInMillis in startCal.timeInMillis..endCal.timeInMillis -> ButtonState.NOW
                    isSameDay(now, startCal) && now.timeInMillis < startCal.timeInMillis -> ButtonState.TODAY
                    else -> ButtonState.CANCEL
                }
            }
        }

        inner class CounselingVH(view: View) : RecyclerView.ViewHolder(view) {
            private val pfp_pic: ShapeableImageView = view.findViewById(R.id.profileImage)
            private val infoText: TextView = view.findViewById(R.id.infoText)
            private val btnCallAction: MaterialButton? = view.findViewById(R.id.btnCallAction)
            private val titleText: TextView? = view.findViewById(R.id.titleText)
            private val nameText: TextView? = view.findViewById(R.id.nameText)

            fun bind(session: CounselingSession) {
                val params = itemView.layoutParams
                params.width = (itemView.context.resources.displayMetrics.widthPixels * 0.42).toInt()
                itemView.layoutParams = params

                titleText?.text = session.title ?: "Counseling Session"

                // Fetch Counselor Name & PFP
                val counselorId = session.taken_by
                nameText?.text = "Counselor" // Default
                pfp_pic.setImageResource(R.drawable.pfp_gco) // Default

                if (!counselorId.isNullOrBlank()) {
                    pfp_pic.tag = counselorId

                    FirebaseFirestore.getInstance().collection("account_details")
                        .document(counselorId)
                        .get()
                        .addOnSuccessListener { doc ->
                            if (pfp_pic.tag == counselorId) {
                                val displayName = doc.getString("displayName")
                                    ?: "${doc.getString("name") ?: ""} ${doc.getString("fname") ?: ""}".trim().ifEmpty { "Counselor" }
                                nameText?.text = displayName

                                val avatarUrl = doc.getString("avatarUrl")
                                if (!avatarUrl.isNullOrEmpty()) {
                                    if (avatarUrl.startsWith("http")) {
                                        Glide.with(pfp_pic.context)
                                            .load(avatarUrl)
                                            .placeholder(R.drawable.img_placeholder)
                                            .error(R.drawable.pfp_gco)
                                            .circleCrop()
                                            .into(pfp_pic)
                                    } else {
                                        pfp_pic.setImageResource(R.drawable.pfp_gco)
                                    }
                                }
                            }
                        }
                }

                // Format Time
                val dateText = tryFormatDateToShort(session.assigned_sched?.date)
                val start = session.assigned_sched?.start?.toDate()
                val end = session.assigned_sched?.end?.toDate()

                val timeText = if (start != null && end != null) {
                    "${formatTime(start)} - ${formatTime(end)}"
                } else {
                    "—"
                }

                infoText.text = "📅 $dateText\n🕑 $timeText"

                btnCallAction?.visibility = View.VISIBLE
                val isFaceToFace = session.preferredPlatform?.trim().equals("face to face", ignoreCase = true) ||
                        session.preferredPlatform?.trim().equals("in-person", ignoreCase = true)

                when (session.status) {
                    "in_progress" -> {
                        if (isFaceToFace) {
                            // Face-to-face in progress: No call button
                            btnCallAction?.text = "In Session"
                            btnCallAction?.setBackgroundResource(R.drawable.f_rounded_green)
                            btnCallAction?.isEnabled = false
                        } else {
                            // Video/Audio in progress - check for active call
                            val callId = activeCallsMap[session.id]
                            if (callId != null) {
                                // Call is active: Join Call
                                btnCallAction?.text = "Join Call"
                                btnCallAction?.setBackgroundResource(R.drawable.f_rounded_green)
                                btnCallAction?.isEnabled = true
                                btnCallAction?.setOnClickListener { joinCall(callId, session.id) }
                            } else {
                                // Call not started yet
                                btnCallAction?.text = "In Progress"
                                btnCallAction?.setBackgroundResource(R.drawable.f_rounded_grey)
                                btnCallAction?.isEnabled = false
                                btnCallAction?.setOnClickListener {
                                    Toast.makeText(
                                        itemView.context,
                                        "Waiting for counselor to start call...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
                    "assigned", "taken" -> {
                        // Session scheduled, waiting for counselor to start
                        btnCallAction?.text = "Scheduled"
                        btnCallAction?.setBackgroundResource(R.drawable.f_rounded_blue)
                        btnCallAction?.isEnabled = false
                    }
                    else -> {
                        btnCallAction?.text = "Pending"
                        btnCallAction?.setBackgroundResource(R.drawable.f_rounded_grey)
                        btnCallAction?.isEnabled = false
                    }
                }
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

            when (announcement.represented_by?.uppercase(Locale.getDefault())) {
                "GCO" -> holder.photo.setImageResource(R.drawable.pfp_gco)
                "PEERS" -> holder.photo.setImageResource(R.drawable.pfp_peers)
                else -> {
                    holder.photo.tag = announcement.represented_by
                    fetchGroupPfp(announcement.represented_by, holder)
                }
            }

            if (announcement.photo_urls.isNotEmpty()) {
                holder.photoRow.visibility = View.VISIBLE
                val photoAdapter = PhotoDisplayAdapter(announcement.photo_urls)
                val gridLayoutManager = androidx.recyclerview.widget.GridLayoutManager(holder.itemView.context, 3)
                gridLayoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
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

        private fun fetchGroupPfp(groupId: String?, holder: NotificationViewHolder) {
            if (groupId.isNullOrBlank()) {
                holder.photo.setImageResource(R.drawable.img_placeholder)
                return
            }
            val db = FirebaseFirestore.getInstance()
            holder.photo.setImageResource(R.drawable.img_placeholder)
            db.collection("supportgroup").document(groupId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (holder.photo.tag != groupId) return@addOnSuccessListener
                    val pfpUrl = snapshot.getString("supportgroup_pfp_URL")
                    if (pfpUrl.isNullOrEmpty()) {
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
                    if (holder.photo.tag == groupId) holder.photo.setImageResource(R.drawable.img_placeholder)
                }
        }

        override fun getItemCount() = announcements.size
        fun updateAnnouncements(newAnnouncements: List<Announcement>) {
            announcements = newAnnouncements
            notifyDataSetChanged()
        }
    }

    enum class ButtonState {
        NOW, TODAY, CANCEL, CONFIRM_CANCEL, CANCELLED_BY_ME
    }

    private fun loadUserDetails(uid: String, imageView: ShapeableImageView, nameView: TextView) {
        if (uid.isBlank()) {
            imageView.setImageResource(R.drawable.img_placeholder)
            nameView.text = "Peer"
            return
        }
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("account_details").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                val displayName = doc.getString("displayName")
                    ?: "${doc.getString("lname") ?: ""} ${doc.getString("fname") ?: ""}".trim().ifEmpty { "Peer" }
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
            .addOnFailureListener {
                imageView.setImageResource(R.drawable.img_placeholder)
                nameView.text = "Peer"
            }
    }

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
            raw ?: "Date TBD"
        }
    }

    private fun formatTime(date: java.util.Date): String {
        // Use device's default timezone (which should be Asia/Manila in Philippines)
        val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        formatter.timeZone = java.util.TimeZone.getTimeZone("Asia/Manila")
        return formatter.format(date)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun joinCall(callId: String, submissionId: String) {
        val intent = Intent(requireContext(), CallActivity::class.java)
        intent.putExtra("CALL_ID", callId)
        intent.putExtra("SUBMISSION_ID", submissionId)
        startActivity(intent)
    }

    private fun showCancelDialog(session: PeerToPeerSession) {
        Toast.makeText(requireContext(), "Cancel dialog: ${session.sessionId}", Toast.LENGTH_SHORT).show()
    }

    private fun showConfirmCancelDialog(session: PeerToPeerSession) {
        Toast.makeText(requireContext(), "Confirm cancel: ${session.sessionId}", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        peerListener?.remove()
        counselingListener?.remove()
        callsListener?.remove()
        announcementsListener?.remove()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val btnCounseling = view.findViewById<ShapeableImageView>(R.id.counseling)
        btnCounseling.setOnClickListener {
            findNavController().navigate(R.id.action_nav_home_to_homeCounseling)
        }
    }
}