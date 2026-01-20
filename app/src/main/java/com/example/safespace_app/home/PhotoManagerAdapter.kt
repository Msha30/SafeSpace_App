package com.example.safespace_app.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.R

class PhotoManagerAdapter(
    private val photos: MutableList<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PhotoManagerAdapter.PhotoManagerViewHolder>() {

    inner class PhotoManagerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imagePhoto: ImageView = view.findViewById(R.id.imagePhoto)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoManagerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_manager, parent, false)
        return PhotoManagerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoManagerViewHolder, position: Int) {
        Glide.with(holder.itemView.context)
            .load(photos[position])
            .centerCrop()
            .into(holder.imagePhoto)

        holder.btnRemove.setOnClickListener {
            onRemove(position)
        }
    }

    override fun getItemCount() = photos.size
}