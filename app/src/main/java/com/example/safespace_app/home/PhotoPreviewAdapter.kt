package com.example.safespace_app.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R

class PhotoPreviewAdapter(
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<PhotoPreviewAdapter.PhotoViewHolder>() {

    private val photos = mutableListOf<Uri>()

    fun setPhotos(list: List<Uri>) {
        photos.clear()
        photos.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return when {
            photos.size <= 3 -> photos.size
            else -> 3   // always show only 3 items
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_addphoto, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val max = photos.size

        if (max > 3 && position == 2) {
            // third item → overlay +X
            val remaining = max - 2
            holder.overlay.text = "+$remaining"
            holder.overlay.visibility = View.VISIBLE
            holder.image.setImageURI(photos[2])
        } else {
            holder.overlay.visibility = View.GONE
            holder.image.setImageURI(photos[position])
        }

        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imageViewThumbnail)
        val overlay: TextView = view.findViewById(R.id.textViewOverlay)
    }
}
