package com.example.safespace_app.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfUserAvatar : Fragment() {

    // Use the shared ProfileViewModel so the main Profile screen updates instantly
    private val viewModel: ProfileViewModel by activityViewModels()

    private var selectedAvatarId: String? = null
    private var selectedImageView: ImageView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_prof_user_avatar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Find Views
        val backBtn = view.findViewById<ImageView>(R.id.backbtn)
        val img1 = view.findViewById<ImageView>(R.id.imgPreview1)
        val img2 = view.findViewById<ImageView>(R.id.imgPreview2)
        val img3 = view.findViewById<ImageView>(R.id.imgPreview3)
        val img4 = view.findViewById<ImageView>(R.id.imgPreview4)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)

        // 2. Handle Selection Logic
        fun selectAvatar(imageView: ImageView, avatarStringId: String) {
            // Reset previous selection visual (remove color filter)
            selectedImageView?.setColorFilter(null)

            // Set new selection
            selectedImageView = imageView
            selectedAvatarId = avatarStringId

            // Visual Feedback: Add slight grey filter to indicate selection
            imageView.setColorFilter(android.graphics.Color.parseColor("#80000000"), android.graphics.PorterDuff.Mode.SRC_ATOP)
        }

        // Map ImageViews to the String IDs you want to save in Firestore
        img1.setOnClickListener { selectAvatar(img1, "image_1") }
        img2.setOnClickListener { selectAvatar(img2, "image_2") }
        img3.setOnClickListener { selectAvatar(img3, "image_3") }
        img4.setOnClickListener { selectAvatar(img4, "image_4") }

        // 3. Handle Navigation
        backBtn.setOnClickListener {
            findNavController().popBackStack()
        }

        // 4. Handle Save
        btnSave.setOnClickListener {
            if (selectedAvatarId == null) {
                Toast.makeText(requireContext(), "Please select an avatar first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveStudentAvatar(selectedAvatarId!!)
        }
    }

    private fun saveStudentAvatar(avatarStringId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Update Firestore with the String ID (e.g., "image_1")
        FirebaseFirestore.getInstance()
            .collection("account_details")
            .document(userId)
            .update("avatarUrl", avatarStringId)
            .addOnSuccessListener {
                // Update the shared ViewModel so the main Profile fragment updates
                viewModel.updateAvatar(avatarStringId)

                Toast.makeText(requireContext(), "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to save avatar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}