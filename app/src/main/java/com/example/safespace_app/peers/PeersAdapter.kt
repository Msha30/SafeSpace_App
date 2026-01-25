package com.example.safespace_app.peers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.safespace_app.Peer
import com.example.safespace_app.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView

class PeersAdapter(
    private val onClick: (Peer) -> Unit
) : ListAdapter<Peer, PeersAdapter.PeerViewHolder>(DiffCallback) {

    companion object DiffCallback : DiffUtil.ItemCallback<Peer>() {
        override fun areItemsTheSame(oldItem: Peer, newItem: Peer): Boolean =
            oldItem.uid == newItem.uid

        override fun areContentsTheSame(oldItem: Peer, newItem: Peer): Boolean =
            oldItem == newItem
    }

    inner class PeerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photo: ShapeableImageView = itemView.findViewById(R.id.photo)
        val name: TextView = itemView.findViewById(R.id.name)
        val status: TextView = itemView.findViewById(R.id.status)
        val btnInfo: MaterialButton = itemView.findViewById(R.id.btninfo)

        fun bind(peer: Peer) {
            name.text = peer.name

            // --- UPDATED AVATAR LOGIC ---
            if (peer.photoUrl.isNotEmpty()) {
                if (peer.photoUrl.startsWith("http")) {
                    // Peer Side (URL)
                    Glide.with(itemView.context)
                        .load(peer.photoUrl)
                        .placeholder(R.drawable.img_placeholder)
                        .error(R.drawable.img_placeholder)
                        .skipMemoryCache(true)
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // <--- FORCE REFRESH FROM NETWORK
                        .into(photo)
                } else {
                    // Student Side (Preset ID)
                    val drawableRes = when (peer.photoUrl) {
                        "image_1" -> R.drawable.avatar_panda
                        "image_2" -> R.drawable.avatar_butterfly
                        "image_3" -> R.drawable.avatar_wolf
                        "image_4" -> R.drawable.avatar_buffalo
                        else -> R.drawable.img_placeholder
                    }
                    photo.setImageResource(drawableRes)
                }
            } else {
                photo.setImageResource(R.drawable.img_placeholder)
            }

            // Presence state
            if (peer.isOnline) {
                status.text = "ONLINE"
                status.setTextColor(itemView.context.getColor(R.color.green))
            } else {
                status.text = "OFFLINE"
                status.setTextColor(itemView.context.getColor(R.color.textgrey))
            }

            btnInfo.setOnClickListener { onClick(peer) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_peers, parent, false)
        return PeerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PeerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
