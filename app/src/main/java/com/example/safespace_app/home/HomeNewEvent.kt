package com.example.safespace_app.home

import android.net.Uri
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.example.safespace_app.SupaClient
import com.example.safespace_app.databinding.FragmentHomeNewEventBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

        adapter = PhotoPreviewAdapter {
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
        binding.btnpost.text = "Posting..."

        // If there are photos, we must create the doc first to get the ID, then upload
        if (selectedPhotos.isNotEmpty()) {
            createFirestoreDocThenUpload(title, description, currentUserId)
        } else {
            // No photos, create announcement directly
            saveAnnouncementToFirestore(title, description, currentUserId, emptyList())
        }
    }

    // Step 1: Create the document to get the Announcement ID
    private fun createFirestoreDocThenUpload(
        title: String,
        description: String,
        userId: String
    ) {
        val announcementData = hashMapOf(
            "title" to title,
            "description" to description,
            "represented_by" to "PEERS",
            "created_by" to userId,
            "date_created" to Timestamp.now(),
            "photo_urls" to emptyList<String>() // Start empty
        )

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("announcements")
            .add(announcementData)
            .addOnSuccessListener { documentReference ->
                // Step 2: Pass the ID to the upload function
                uploadPhotosAndUpdate(documentReference.id)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to create announcement: ${e.message}", Toast.LENGTH_SHORT).show()
                resetButtonState()
            }
    }

    // Step 2: Upload photos to Supabase using the ID
    private fun uploadPhotosAndUpdate(announcementId: String) {
        lifecycleScope.launch {
            try {
                // Upload all photos concurrently using IO dispatcher
                val uploadedUrls = withContext(Dispatchers.IO) {
                    selectedPhotos.mapIndexed { index, uri ->
                        async { uploadSingleImageToSupabase(uri, announcementId, index) }
                    }.awaitAll()
                }

                // Step 3: Update Firestore with the new URLs
                updateFirestoreWithUrls(announcementId, uploadedUrls)

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to upload photos: ${e.message}", Toast.LENGTH_SHORT).show()
                resetButtonState()
            }
        }
    }

    private suspend fun uploadSingleImageToSupabase(uri: Uri, announcementId: String, index: Int): String {
        // 1. Convert Uri to temporary File
        val inputStream = requireContext().contentResolver.openInputStream(uri)
            ?: throw Exception("Failed to read image")

        val tmpFile = File(requireContext().cacheDir, "tmp_${System.currentTimeMillis()}.jpg")
        tmpFile.outputStream().use { output -> inputStream.copyTo(output) }
        inputStream.close()

        // 2. Upload to Supabase
        // Bucket name: "Event"
        // Path: announcementId_index.jpg (e.g. "abc123_0.jpg")
        val bucket = SupaClient.client.storage.from("ProfilePictures")
        val path = "announcement_pic/$announcementId/$index.jpg"

        bucket.upload(path, tmpFile) { upsert = true }

        // 3. Get Public URL
        val publicUrl = bucket.publicUrl(path)

        // 4. Cleanup temp file
        tmpFile.delete()

        return publicUrl
    }

    // Step 3: Update the document with URLs and finish
    private fun updateFirestoreWithUrls(announcementId: String, photoUrls: List<String>) {
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("announcements")
            .document(announcementId)
            .update("photo_urls", photoUrls)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Announcement posted successfully!", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(requireContext(), "Failed to save photo URLs: ${e.message}", Toast.LENGTH_SHORT).show()
                resetButtonState()
            }
    }

    // Standard save for when there are NO photos
    private fun saveAnnouncementToFirestore(
        title: String,
        description: String,
        userId: String,
        photoUrls: List<String>
    ) {
        val announcementData = hashMapOf(
            "title" to title,
            "description" to description,
            "represented_by" to "PEERS",
            "created_by" to userId,
            "date_created" to Timestamp.now(),
            "photo_urls" to photoUrls
        )

        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("announcements")
            .add(announcementData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(
                    requireContext(),
                    "Announcement posted successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                // Navigate back to Home2
                findNavController().navigateUp()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Failed to post announcement",
                    Toast.LENGTH_SHORT
                ).show()
                resetButtonState()
            }
    }

    private fun resetButtonState() {
        binding.btnpost.isEnabled = true
        binding.btnpost.text = "Post"
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

                if (selectedPhotos.isEmpty()) {
                    binding.recyclerViewPhotos.visibility = View.GONE
                } else {
                    binding.recyclerViewPhotos.visibility = View.VISIBLE
                }
            }
    }
}