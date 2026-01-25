package com.example.safespace_app.peers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.example.safespace_app.UnifiedSession
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.*

class UnifiedSessionAdapter(
    private val sessions: List<UnifiedSession>,
    private val onClick: (UnifiedSession) -> Unit
) : RecyclerView.Adapter<UnifiedSessionAdapter.SessionViewHolder>() {

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photo: ShapeableImageView = itemView.findViewById(R.id.photo)
        val name: TextView = itemView.findViewById(R.id.name)
        val lastMessage: TextView = itemView.findViewById(R.id.username)
        val time: TextView = itemView.findViewById(R.id.time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_messages, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        holder.name.text = session.name
        holder.lastMessage.text = if (session.lastMessage.isNotEmpty()) {
            session.lastMessage.take(50) + if (session.lastMessage.length > 50) "..." else ""
        } else {
            "No messages yet"
        }

        holder.time.text = formatTime(session.lastMessageTime)

        // --- UPDATED AVATAR LOGIC ---
        val photoUrl = session.photoUrl

        if (photoUrl.isNotEmpty()) {
            if (photoUrl.startsWith("http")) {
                // CASE 1: Peer Side (URL)
                // skipMemoryCache(true) forces the image to reload,
                // ensuring new avatars appear instantly.
                Glide.with(holder.itemView.context)
                    .load(photoUrl)
                    .placeholder(R.drawable.img_placeholder)
                    .error(R.drawable.img_placeholder)
                    .skipMemoryCache(true)
                    .into(holder.photo)
            } else {
                // CASE 2: Student Side (Preset ID)
                // Map the string ID to the drawable resource directly for maximum speed
                val drawableRes = when (photoUrl) {
                    "image_1" -> R.drawable.avatar_panda
                    "image_2" -> R.drawable.avatar_butterfly
                    "image_3" -> R.drawable.avatar_wolf
                    "image_4" -> R.drawable.avatar_buffalo
                    else -> R.drawable.img_placeholder
                }
                // Directly set the resource (no Glide needed for preset icons, it's faster)
                holder.photo.setImageResource(drawableRes)
            }
        } else {
            holder.photo.setImageResource(R.drawable.img_placeholder)
        }

        // Bold if unread
        if (session.unreadCount > 0) {
            holder.lastMessage.setTextColor(holder.itemView.context.getColor(R.color.black))
            holder.lastMessage.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else {
            holder.lastMessage.setTextColor(holder.itemView.context.getColor(R.color.textgrey))
            holder.lastMessage.typeface = android.graphics.Typeface.DEFAULT
        }

        holder.itemView.setOnClickListener { onClick(session) }
    }

    override fun getItemCount(): Int = sessions.size

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""
        val messageDate = Date(timestamp)
        val now = Date()
        val diffInMinutes = (now.time - messageDate.time) / (1000 * 60)
        val diffInHours = diffInMinutes / 60

        return when {
            diffInMinutes < 1 -> "now"
            diffInMinutes < 60 -> "${diffInMinutes}m"
            diffInHours < 24 -> "${diffInHours}h"
            diffInHours < 48 -> "Yesterday"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(messageDate)
        }
    }
}