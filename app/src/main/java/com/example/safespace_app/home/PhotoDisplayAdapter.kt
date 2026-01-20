package com.example.safespace_app.home

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.R

class PhotoDisplayAdapter(
    private val photoUrls: List<String>
) : RecyclerView.Adapter<PhotoDisplayAdapter.PhotoDisplayViewHolder>() {

    inner class PhotoDisplayViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageViewThumbnail: ImageView = view.findViewById(R.id.imageViewThumbnail)
        val textViewOverlay: TextView = view.findViewById(R.id.textViewOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoDisplayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_addphoto, parent, false)
        return PhotoDisplayViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoDisplayViewHolder, position: Int) {
        if (position < 2) {
            // Show first 2 photos
            Glide.with(holder.itemView.context)
                .load(photoUrls[position])
                .centerCrop()
                .into(holder.imageViewThumbnail)

            holder.textViewOverlay.visibility = View.GONE
        } else if (position == 2) {
            // Third item shows photo with overlay
            Glide.with(holder.itemView.context)
                .load(photoUrls[position])
                .centerCrop()
                .into(holder.imageViewThumbnail)

            val remaining = photoUrls.size - 3
            if (remaining > 0) {
                holder.textViewOverlay.visibility = View.VISIBLE
                holder.textViewOverlay.text = "+$remaining"
            } else {
                holder.textViewOverlay.visibility = View.GONE
            }
        }

        // Show full image dialog on click
        holder.itemView.setOnClickListener {
            showImageDialog(holder.itemView, photoUrls[position])
        }
    }

    override fun getItemCount(): Int {
        return minOf(photoUrls.size, 3)
    }

    private fun showImageDialog(view: View, imageUrl: String) {
        val dialog = Dialog(view.context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val imageView = dialog.findViewById<ImageView>(R.id.imageViewFull)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnClose)

        Glide.with(view.context)
            .load(imageUrl)
            .into(imageView)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}