package com.example.safespace_app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.example.safespace_app.databinding.FragmentChatSupportGroupHomeBinding
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.Announcement
import com.example.safespace_app.home.Home2.NotificationAdapter.NotificationViewHolder
import com.example.safespace_app.home.PhotoDisplayAdapter

class ChatSupportGroupHome : Fragment() {

    companion object {
        const val ARG_GROUP_ID = "supportGroupId"
        const val ARG_GROUP_NAME = "supportGroupName"
    }
    private lateinit var announcementAdapter: AnnouncementAdapter
    private var announcementListener: ListenerRegistration? = null

    private var _binding: FragmentChatSupportGroupHomeBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private lateinit var groupId: String
    private var groupNameArg: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = requireArguments()
        groupId = args.getString(ARG_GROUP_ID)
            ?: error("supportGroupId is required")
        groupNameArg = args.getString(ARG_GROUP_NAME)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatSupportGroupHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initial title
        binding.supportgroupName.text = groupNameArg ?: "Support Group"

        binding.backbtn.setOnClickListener {
            findNavController().navigate(R.id.nav_chat)
        }

        announcementAdapter = AnnouncementAdapter(mutableListOf())

        binding.notifications.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = announcementAdapter
            setHasFixedSize(true)
        }

        startListeningToGroup(groupId)
        startListeningToAnnouncements(groupId)

    }
    private fun startListeningToAnnouncements(groupId: String) {
        announcementListener = db.collection("announcements")
            .whereEqualTo("represented_by", groupId)
            .addSnapshotListener { snapshots, error ->
                if (error != null || snapshots == null) return@addSnapshotListener

                val announcements = snapshots.documents
                    .mapNotNull { it.toObject(Announcement::class.java) }
                    .sortedByDescending { it.date_created }

                announcementAdapter.submit(announcements)
            }
    }


    private fun startListeningToGroup(id: String) {
        listener = db.collection("supportgroup")
            .document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val data = snapshot.data ?: return@addSnapshotListener

                // ---- Group title ----
                binding.supportgroupName.text =
                    (data["supportgroup_name"] as? String) ?: binding.supportgroupName.text

                val bannerUrl = data["supportgroup_cover_URL"] as? String
                if (!bannerUrl.isNullOrBlank()) {
                    Glide.with(this@ChatSupportGroupHome)
                        .load(bannerUrl)
                        .placeholder(R.drawable.img_placeholder)
                        .centerCrop() // makes it fill the view nicely
                        .into(binding.supportgroupBanner)
                } else {
                    binding.supportgroupBanner.setImageResource(R.drawable.img_placeholder)
                }

                // ---- Group Chats (list of maps) ----
                val groupChats =
                    (data["groupchats"] as? List<*>)?.mapNotNull { it as? Map<*, *> } ?: emptyList()

                bindGroupChat(
                    index = 0,
                    chats = groupChats,
                    container = binding.gc1,
                    nameTv = binding.gc1Name as TextView,
                    pfpIv = binding.gc1Pfp
                )

                bindGroupChat(
                    index = 1,
                    chats = groupChats,
                    container = binding.gc2,
                    nameTv = binding.gc2Name as TextView,
                    pfpIv = binding.gc2Pfp
                )
            }
    }

    private fun bindGroupChat(
        index: Int,
        chats: List<Map<*, *>>,
        container: View,
        nameTv: TextView,
        pfpIv: ShapeableImageView
    ) {
        if (chats.size <= index) {
            nameTv.text = "No Group"
            pfpIv.setImageResource(R.drawable.img_placeholder)
            container.setOnClickListener(null)
            return
        }

        val chat = chats[index]
        val name = chat["name"] as? String ?: "Group Chat"
        val pfp = chat["pfp_URL"] as? String
        val groupchatId = chat["groupchatId"] as? String

        nameTv.text = name

        if (!pfp.isNullOrBlank()) {
            Glide.with(this)
                .load(pfp)
                .placeholder(R.drawable.img_placeholder)
                .into(pfpIv)
        } else {
            pfpIv.setImageResource(R.drawable.img_placeholder)
        }

        // Click opens the group chat fragment
        container.setOnClickListener {
            if (groupchatId.isNullOrBlank()) return@setOnClickListener

            val bundle = Bundle().apply {
                putString("groupchatId", groupchatId)
                putString("groupchatName", name)
                putString("supportGroupId", groupId)
            }

            findNavController().navigate(
                R.id.action_chatSupportGroupHome_to_chatSupportGroupChat,
                bundle
            )
        }
    }
    private  class AnnouncementAdapter(
        private val items: MutableList<Announcement>
    ) : RecyclerView.Adapter<AnnouncementAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val content: TextView = view.findViewById(R.id.content)
            val photo: ShapeableImageView = view.findViewById(R.id.photo)
            val photoRow: RecyclerView = view.findViewById(R.id.photorow)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_notif, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val announcement = items[position]

            holder.title.text = announcement.title
            holder.content.text = announcement.description

            // assign tag for async loading
            holder.photo.tag = announcement.represented_by
            fetchGroupPfp(announcement.represented_by, holder)  // <-- pass holder, not 'this'

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

        private fun fetchGroupPfp(groupId: String, holder: ViewHolder) {
            val db = FirebaseFirestore.getInstance()

            holder.photo.setImageResource(R.drawable.img_placeholder)

            db.collection("supportgroup").document(groupId)
                .get()
                .addOnSuccessListener { snapshot ->
                    // ViewHolder reused? Abort.
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
                    if (holder.photo.tag == groupId) {
                        holder.photo.setImageResource(R.drawable.img_placeholder)
                    }
                }
        }

        override fun getItemCount(): Int = items.size

        fun submit(newItems: List<Announcement>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        announcementListener?.remove()
        listener = null
        announcementListener = null
        _binding = null
    }

}
