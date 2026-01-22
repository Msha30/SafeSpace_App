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

class ChatSupportGroupHome : Fragment() {

    companion object {
        const val ARG_GROUP_ID = "supportGroupId"
        const val ARG_GROUP_NAME = "supportGroupName"
    }

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

        startListeningToGroup(groupId)
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


    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        _binding = null
    }
}
