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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.R

class PhotoDisplayAdapter(
    private val photoUrls: List<String>
) : RecyclerView.Adapter<PhotoDisplayAdapter.PhotoDisplayViewHolder>() {

    // Exposed so SpanSizeLookup can read it
    val totalPhotos: Int
        get() = photoUrls.size

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
        val context = holder.itemView.context

        // --- IMAGE SIZING ONLY ---
        val imageParams = holder.imageViewThumbnail.layoutParams

        if (photoUrls.size == 1) {
            imageParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            imageParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.imageViewThumbnail.adjustViewBounds = true
            holder.imageViewThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            val displayMetrics = context.resources.displayMetrics
            val paddingPx = (32 * displayMetrics.density).toInt()
            val columnWidth = (displayMetrics.widthPixels - paddingPx) / 3

            imageParams.width = ViewGroup.LayoutParams.MATCH_PARENT
            imageParams.height = columnWidth
            holder.imageViewThumbnail.adjustViewBounds = false
            holder.imageViewThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
        }

        holder.imageViewThumbnail.layoutParams = imageParams

        // --- LOAD IMAGE ---
        val imageUrl = photoUrls[position]
        Glide.with(context)
            .load(imageUrl)
            .placeholder(R.drawable.img_placeholder)
            .into(holder.imageViewThumbnail)

        // --- OVERLAY (+X) ---
        if (position == 2 && photoUrls.size > 3) {
            holder.textViewOverlay.visibility = View.VISIBLE
            holder.textViewOverlay.text = "+${photoUrls.size - 3}"
        } else {
            holder.textViewOverlay.visibility = View.GONE
        }

        // --- CLICK ---
        holder.itemView.setOnClickListener {
            showImageDialog(context, imageUrl)
        }
    }

    override fun getItemCount(): Int =
        if (photoUrls.size > 3) 3 else photoUrls.size

    private fun showImageDialog(context: android.content.Context, imageUrl: String) {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_image_view)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val imageView = dialog.findViewById<ImageView>(R.id.imageViewFull)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnClose)

        Glide.with(context).load(imageUrl).fitCenter().into(imageView)

        btnClose.setOnClickListener { dialog.dismiss() }
        imageView.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
