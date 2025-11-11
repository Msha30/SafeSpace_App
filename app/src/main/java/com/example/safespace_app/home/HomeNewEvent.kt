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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R
import com.bumptech.glide.Glide

class HomeNewEvent : Fragment() {


    companion object {
        fun newInstance() = HomeNewEvent()
    }

    private val viewModel: HomeNewEventViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    private val selectedPhotoUris = mutableListOf<Uri>()
    private lateinit var photosAdapter: PhotoAdapter

    // Activity Result Launcher for selecting multiple images
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri>? ->
        uris?.let {
            // Add new URIs to the existing list
            selectedPhotoUris.addAll(it)
            photosAdapter.notifyDataSetChanged() // Refresh the grid
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home_new_event, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize RecyclerView and Adapter
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPhotos)
        photosAdapter = PhotoAdapter(selectedPhotoUris)
        recyclerView.adapter = photosAdapter

        // Set up the "Add photos" button click listener
        view.findViewById<Button>(R.id.btnimage).setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }
    }

    // --- START: Encapsulated PhotoAdapter ---

    private inner class PhotoAdapter(private val photoUris: List<Uri>) :
        RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

        private val MAX_DISPLAY_COUNT = 3

        // ViewHolder class
        inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.imageViewThumbnail)
            val textViewOverlay: TextView = itemView.findViewById(R.id.textViewOverlay)
        }

        // We only use one ViewType, and handle the overlay visibility in onBindViewHolder
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_addphoto, parent, false)
            return PhotoViewHolder(view)
        }

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val isOverlayPosition = photoUris.size > MAX_DISPLAY_COUNT && position == MAX_DISPLAY_COUNT - 1

            // 1. Enforce Square Aspect Ratio
            // (Uses itemView.post to ensure width is calculated before setting height)
            holder.itemView.post {
                val width = holder.itemView.width
                val params = holder.itemView.layoutParams
                if (params.height != width) {
                    params.height = width
                    holder.itemView.layoutParams = params
                }
            }

            // 2. Set Content (Photo or Photo + Overlay)
            val uriToShow = photoUris[position]
            Glide.with(holder.itemView.context).load(uriToShow).into(holder.imageView)

            if (isOverlayPosition) {
                // This is the 3rd position (index 2) and we have more than 3 photos
                val remainingCount = photoUris.size - (MAX_DISPLAY_COUNT - 1)
                holder.textViewOverlay.text = "+$remainingCount"
                holder.textViewOverlay.visibility = View.VISIBLE
            } else {
                // Regular photo position
                holder.textViewOverlay.visibility = View.GONE
            }
        }

        override fun getItemCount(): Int {
            // Limit the count to 3 if more than 3 photos are selected
            return if (photoUris.size > MAX_DISPLAY_COUNT) {
                MAX_DISPLAY_COUNT
            } else {
                photoUris.size
            }
        }
    }
}