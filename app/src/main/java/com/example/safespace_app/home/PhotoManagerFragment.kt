package com.example.safespace_app.home

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.safespace_app.R
import com.example.safespace_app.databinding.FragmentHomePhotoManagerBinding


class PhotoManagerFragment : Fragment() {

    private lateinit var binding: FragmentHomePhotoManagerBinding
    private lateinit var adapter: PhotoManagerAdapter
    private val photos = mutableListOf<Uri>()

    private val pickImages =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
            if (!uris.isNullOrEmpty()) {
                val currentSize = photos.size
                val availableSlots = 10 - currentSize
                val photosToAdd = uris.take(availableSlots)

                photos.addAll(photosToAdd)
                adapter.notifyDataSetChanged()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomePhotoManagerBinding.inflate(inflater, container, false)

        // Get photos from bundle
        arguments?.getParcelableArrayList<Uri>("photos")?.let {
            photos.addAll(it)
        }

        adapter = PhotoManagerAdapter(photos) { position ->
            photos.removeAt(position)
            adapter.notifyDataSetChanged()
        }

        binding.recyclerViewManager.adapter = adapter
        binding.recyclerViewManager.layoutManager = LinearLayoutManager(requireContext())

        binding.btnDone.setOnClickListener {
            // Return updated list
            findNavController().previousBackStackEntry?.savedStateHandle?.set("updatedPhotos", photos)
            findNavController().popBackStack()
        }

        binding.addphoto.setOnClickListener {
            if (photos.size < 10) {
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } else {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Maximum 10 photos allowed",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        return binding.root
    }
}