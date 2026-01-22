package com.example.safespace_app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.example.safespace_app.databinding.FragmentChatSupportGroupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ChatSupportGroup : Fragment() {

    companion object {
        const val ARG_GROUP_ID = "supportGroupId"
        const val ARG_GROUP_NAME = "supportGroupName"
        const val ARG_GROUP_PFP = "supportGroupPfp"
        const val ARG_GROUP_DESC = "supportGroupDesc"
    }

    private var _binding: FragmentChatSupportGroupBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var groupId: String
    private var groupName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupId = it.getString(ARG_GROUP_ID)
                ?: error("supportGroupId is required")
            groupName = it.getString(ARG_GROUP_NAME)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatSupportGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "You must be logged in", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        // Set initial info from arguments
        binding.supportgroupName.text = groupName ?: "Support Group"
        binding.supportDesc.text = arguments?.getString(ARG_GROUP_DESC) ?: ""
        val pfpUrl = arguments?.getString(ARG_GROUP_PFP)
        if (!pfpUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(pfpUrl)
                .placeholder(R.drawable.img_placeholder)
                .circleCrop()
                .into(binding.supportPfp)
        }

        // Back button
        binding.backbtn.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        checkMembershipAndHandle(uid)
    }

    private fun checkMembershipAndHandle(uid: String) {
        db.collection("supportgroup").document(groupId)
            .get()
            .addOnSuccessListener { doc ->
                val members = (doc.get("member_list") as? List<*>)?.map { it.toString() } ?: emptyList()

                if (members.contains(uid)) {
                    // Already a member → navigate straight to ChatSupportGroupHome
                    navigateToHome()
                } else {
                    // Show join button
                    binding.btnjoin.visibility = View.VISIBLE
                    binding.btnjoin.setOnClickListener {
                        joinGroup(uid)
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Failed to load group info", Toast.LENGTH_SHORT).show()
            }
    }

    private fun joinGroup(uid: String) {
        db.collection("supportgroup").document(groupId)
            .update("member_list", FieldValue.arrayUnion(uid))
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "You joined the group!", Toast.LENGTH_SHORT).show()
                binding.btnjoin.visibility = View.GONE
                binding.supportDesc.text = "Welcome to the group!"
                // Navigate after joining
                navigateToHome()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to join: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToHome() {
        val bundle = Bundle().apply {
            putString(ChatSupportGroupHome.ARG_GROUP_ID, groupId)
            putString(ChatSupportGroupHome.ARG_GROUP_NAME, groupName)
        }
        findNavController().navigate(R.id.action_chatSupportGroup_to_chatSupportGroupHome, bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
