package com.example.safespace_app.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.safespace_app.R

class PhotoManagerAdapter(
    private val photos: MutableList<Uri>,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PhotoManagerAdapter.PhotoManagerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoManagerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_manager, parent, false)
        return PhotoManagerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoManagerViewHolder, position: Int) {
        val uri = photos[position]
        holder.image.setImageURI(uri)

        holder.btnRemove.setOnClickListener {
            onRemove(position)
        }
    }

    override fun getItemCount(): Int = photos.size

    class PhotoManagerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imagePhoto)
        val btnRemove: ImageView = view.findViewById(R.id.btnRemove)
    }
}
