package com.example.safespace_app.home

import android.net.Uri
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.bumptech.glide.Glide
import com.example.safespace_app.databinding.FragmentHomeNewEventBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class HomeNewEvent : Fragment() {

    private lateinit var binding: FragmentHomeNewEventBinding
    private lateinit var adapter: PhotoPreviewAdapter

    private val selectedPhotos = mutableListOf<Uri>()

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
            if (!uris.isNullOrEmpty()) {
                selectedPhotos.clear()
                selectedPhotos.addAll(uris.take(10))
                adapter.setPhotos(selectedPhotos)
                binding.recyclerViewPhotos.visibility = View.VISIBLE
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeNewEventBinding.inflate(inflater, container, false)

        adapter = PhotoPreviewAdapter { index ->
            // When clicking the "+X" overlay OR any item
            openPhotoManager()
        }

        binding.recyclerViewPhotos.adapter = adapter

        binding.backbtn.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnimage.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnpost.setOnClickListener {
            createAnnouncement()
        }

        return binding.root
    }

    private fun createAnnouncement() {
        val title = binding.title.text.toString().trim()
        val description = binding.content.text.toString().trim()

        // Validation
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter an event title", Toast.LENGTH_SHORT).show()
            return
        }

        if (description.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter an event description", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable post button to prevent double posting
        binding.btnpost.isEnabled = false

        // Create announcement data
        val announcementData = hashMapOf(
            "title" to title,
            "description" to description,
            "represented_by" to "PEERS",
            "created_by" to currentUserId,
            "date_created" to Timestamp.now()
        )

        // Save to Firestore
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("announcements")
            .add(announcementData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(requireContext(), "Announcement posted successfully!", Toast.LENGTH_SHORT).show()

                // Navigate back to Home2
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("HomeNewEvent", "Error posting announcement", e)
                Toast.makeText(requireContext(), "Failed to post announcement", Toast.LENGTH_SHORT).show()

                // Re-enable post button
                binding.btnpost.isEnabled = true
            }
    }

    private fun openPhotoManager() {
        val bundle = Bundle().apply {
            putParcelableArrayList("photos", ArrayList(selectedPhotos))
        }

        findNavController().navigate(
            R.id.action_homeNewEvent_to_photoManagerFragment,
            bundle
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<List<Uri>>("updatedPhotos")
            ?.observe(viewLifecycleOwner) { updated ->
                selectedPhotos.clear()
                selectedPhotos.addAll(updated)
                adapter.setPhotos(selectedPhotos)
            }
    }
}