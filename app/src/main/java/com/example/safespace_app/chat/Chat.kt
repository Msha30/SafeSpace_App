package com.example.safespace_app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.safespace_app.R
import com.example.safespace_app.SupportGroup
import com.example.safespace_app.chat.adapter.SupportGroupAdapter
import com.example.safespace_app.databinding.FragmentChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import androidx.navigation.fragment.findNavController

class Chat : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SupportGroupAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            // Not signed in
            return
        }

        adapter = SupportGroupAdapter(mutableListOf()) { group ->
            db.collection("supportgroup").document(group.id)
                .get()
                .addOnSuccessListener { doc ->
                    val members = (doc.get("member_list") as? List<*>)?.map { it.toString() } ?: emptyList()

                    if (members.contains(uid)) {
                        // Already a member → go straight to ChatSupportGroupHome
                        val bundle = Bundle().apply {
                            putString(ChatSupportGroupHome.ARG_GROUP_ID, group.id)
                            putString(ChatSupportGroupHome.ARG_GROUP_NAME, group.name)
                        }
                        findNavController().navigate(R.id.action_chat_to_chatSupportGroupHome, bundle)
                    } else {
                        // Not a member → open join popup (ChatSupportGroup)
                        val bundle = Bundle().apply {
                            putString(ChatSupportGroup.ARG_GROUP_ID, group.id)
                            putString(ChatSupportGroup.ARG_GROUP_NAME, group.name)
                            putString(ChatSupportGroup.ARG_GROUP_PFP, group.pfpUrl)
                            putString(ChatSupportGroup.ARG_GROUP_DESC, doc.getString("supportgroup_description"))
                        }
                        findNavController().navigate(R.id.action_chat_to_chatSupportGroup, bundle)
                    }
                }
        }

        binding.supportGroupList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@Chat.adapter
        }

        listenToSupportGroups()
    }

    private fun listenToSupportGroups() {
        listener = db.collection("supportgroup")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val groups = snapshot.documents.map { doc ->
                    val members = (doc.get("member_list") as? List<*>)?.map { it.toString() } ?: emptyList()
                    SupportGroup(
                        id = doc.id,
                        name = doc.getString("supportgroup_name") ?: "",
                        pfpUrl = doc.getString("supportgroup_pfp_URL"),
                        members = members
                    )
                }

                adapter.updateData(groups)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
        listener = null
        _binding = null
    }
}
