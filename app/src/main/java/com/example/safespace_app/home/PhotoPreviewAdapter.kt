package com.example.safespace_app.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.R

class PhotoPreviewAdapter(
    private val onItemClick: () -> Unit
) : RecyclerView.Adapter<PhotoPreviewAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Uri>()

    inner class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageViewThumbnail: ImageView = view.findViewById(R.id.imageViewThumbnail)
        val textViewOverlay: TextView = view.findViewById(R.id.textViewOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_addphoto, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        if (position < 2) {
            // Show first 2 photos
            Glide.with(holder.itemView.context)
                .load(photos[position])
                .centerCrop()
                .into(holder.imageViewThumbnail)

            holder.textViewOverlay.visibility = View.GONE
        } else if (position == 2) {
            // Third item shows photo with overlay
            Glide.with(holder.itemView.context)
                .load(photos[position])
                .centerCrop()
                .into(holder.imageViewThumbnail)

            val remaining = photos.size - 3
            if (remaining > 0) {
                holder.textViewOverlay.visibility = View.VISIBLE
                holder.textViewOverlay.text = "+$remaining"
            } else {
                holder.textViewOverlay.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener {
            onItemClick()
        }
    }

    override fun getItemCount(): Int {
        return minOf(photos.size, 3)
    }

    fun setPhotos(newPhotos: List<Uri>) {
        photos.clear()
        photos.addAll(newPhotos)
        notifyDataSetChanged()
    }
}