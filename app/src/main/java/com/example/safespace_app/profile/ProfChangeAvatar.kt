package com.example.safespace_app.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.bumptech.glide.Glide
import com.example.safespace_app.SupaClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.coroutines.launch
import java.io.File

class ProfChangeAvatar : Fragment() {

    private var selectedImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            view?.findViewById<ImageView>(R.id.imgPreview)?.setImageURI(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val rootView = inflater.inflate(R.layout.fragment_prof_change_avatar, container, false)

        rootView.findViewById<ImageView>(R.id.backbtn).setOnClickListener {
            requireActivity().onBackPressed()
        }

        rootView.findViewById<MaterialButton>(R.id.btnPickPhoto).setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        rootView.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            val uri = selectedImageUri
            if (uri != null) uploadWithSupabase(uri)
            else Toast.makeText(requireContext(), "Please select an image first", Toast.LENGTH_SHORT).show()
        }

        return rootView
    }

    private fun uploadWithSupabase(uri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // 1. Read file from URI into File or ByteArray
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Toast.makeText(requireContext(), "Failed to read image", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Here we convert to File (cache) for easier handling
                val tmpFile = File(requireContext().cacheDir, "avatar_$userId.jpg")
                tmpFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }

                // 2. Upload to Supabase public bucket
                val bucket = SupaClient.client.storage.from("ProfilePictures")
                val path = "avatars/$userId.jpg"  // you can define your folder /file name

                bucket.upload(path, tmpFile) {
                    upsert = true  // overwrite if exists
                }

                // 3. Get public URL
                val publicUrl = bucket.publicUrl(path)

                // 4. Save URL to Firestore (or your user metadata store)
                FirebaseFirestore.getInstance()
                    .collection("account_details")
                    .document(userId)
                    .update("avatarUrl", publicUrl)
                    .addOnSuccessListener {
                        // Update preview with new URL
                        view?.findViewById<ImageView>(R.id.imgPreview)?.let { imgView ->
                            Glide.with(this@ProfChangeAvatar)
                                .load(publicUrl)
                                .into(imgView)
                        }
                        Toast.makeText(requireContext(), "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        e.printStackTrace()
                        Toast.makeText(requireContext(), "Failed to save avatar URL: ${e.message}", Toast.LENGTH_LONG).show()
                    }

                // Clean up tmp file
                tmpFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
