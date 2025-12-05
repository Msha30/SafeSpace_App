package com.example.safespace_app.peers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.*

class StudentSessionAdapter(
    private val sessions: List<StudentActiveSession>,
    private val onClick: (StudentActiveSession) -> Unit
) : RecyclerView.Adapter<StudentSessionAdapter.SessionViewHolder>() {

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photo: ShapeableImageView = itemView.findViewById(R.id.photo)
        val name: TextView = itemView.findViewById(R.id.name)
        val username: TextView = itemView.findViewById(R.id.username)
        val time: TextView = itemView.findViewById(R.id.time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_messages, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        holder.name.text = session.peerName
        holder.username.text = if (session.lastMessage.isNotEmpty()) {
            session.lastMessage.take(50) + if (session.lastMessage.length > 50) "..." else ""
        } else {
            "No messages yet"
        }

        holder.time.text = formatTime(session.lastMessageTime)

        // Load photo
        Glide.with(holder.itemView.context)
            .load(if (session.peerPhoto.isEmpty()) R.drawable.img_placeholder else session.peerPhoto)
            .placeholder(R.drawable.img_placeholder)
            .error(R.drawable.img_placeholder)
            .into(holder.photo)

        holder.itemView.setOnClickListener { onClick(session) }
    }

    override fun getItemCount(): Int = sessions.size

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return ""

        val messageDate = Date(timestamp)
        val now = Date()
        val diffInMillis = now.time - messageDate.time
        val diffInMinutes = diffInMillis / (1000 * 60)
        val diffInHours = diffInMillis / (1000 * 60 * 60)

        return when {
            diffInMinutes < 1 -> "now"
            diffInMinutes < 60 -> "${diffInMinutes}m"
            diffInHours < 24 -> "${diffInHours}h"
            diffInHours < 48 -> "Yesterday"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(messageDate)
        }
    }
}