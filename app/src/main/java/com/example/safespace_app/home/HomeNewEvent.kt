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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.bumptech.glide.Glide
import com.example.safespace_app.databinding.FragmentHomeNewEventBinding

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
            // When clicking the “+X” overlay OR any item
            openPhotoManager()
        }

        binding.recyclerViewPhotos.adapter = adapter

        binding.btnimage.setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        return binding.root
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
