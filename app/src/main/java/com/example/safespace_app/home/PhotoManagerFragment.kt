package com.example.safespace_app.home

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.example.safespace_app.R
import com.example.safespace_app.databinding.FragmentHomePhotoManagerBinding


class PhotoManagerFragment : Fragment() {

    private lateinit var binding: FragmentHomePhotoManagerBinding
    private lateinit var adapter: PhotoManagerAdapter
    private val photos = mutableListOf<Uri>()

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

        adapter = PhotoManagerAdapter(photos) {
            photos.removeAt(it)
            adapter.notifyDataSetChanged()
        }

        binding.recyclerViewManager.adapter = adapter

        binding.btnDone.setOnClickListener {
            // return updated list
            findNavController().previousBackStackEntry?.savedStateHandle?.set("updatedPhotos", photos)
            findNavController().popBackStack()
        }

        return binding.root
    }
}
