package com.example.safespace_app.peers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.Peer
import com.example.safespace_app.R
import com.google.android.material.imageview.ShapeableImageView

class MessagesAdapter(
    private val messages: List<Peer>,
    private val onClick: (Peer) -> Unit
) : RecyclerView.Adapter<MessagesAdapter.MessageViewHolder>() {

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photo: ShapeableImageView = itemView.findViewById(R.id.photo)
        val name: TextView = itemView.findViewById(R.id.name)
        val username: TextView = itemView.findViewById(R.id.username)
        val time: TextView = itemView.findViewById(R.id.time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_messages, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val peer = messages[position]
        holder.name.text = peer.name
        holder.username.text = "Username ${peer.uid}" // placeholder
        holder.time.text = "1m" // placeholder

        // Load placeholder image if photoUrl empty
        Glide.with(holder.itemView.context)
            .load(if (peer.photoUrl.isEmpty()) R.drawable.img_placeholder else peer.photoUrl)
            .into(holder.photo)

        holder.itemView.setOnClickListener { onClick(peer) }
    }

    override fun getItemCount(): Int = messages.size
}
