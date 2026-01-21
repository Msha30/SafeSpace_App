package com.example.safespace_app.chat.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.safespace_app.R
import com.example.safespace_app.SupportGroup
import com.google.android.material.imageview.ShapeableImageView

class SupportGroupAdapter(
    private val groups: MutableList<SupportGroup>,
    private val onClick: (SupportGroup) -> Unit
) : RecyclerView.Adapter<SupportGroupAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val pfp: ShapeableImageView = itemView.findViewById(R.id.support_pfp)
        val name: TextView = itemView.findViewById(R.id.support_name)
        val memberNum: TextView = itemView.findViewById(R.id.support_memberNum)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_supportgroup, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = groups[position]

        holder.name.text = group.name
        holder.memberNum.text =
            "${group.members.size} Members  |  Join Now!"

        if (!group.pfpUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView)
                .load(group.pfpUrl)
                .placeholder(R.drawable.img_placeholder)
                .into(holder.pfp)
        } else {
            holder.pfp.setImageResource(R.drawable.img_placeholder)
        }

        holder.itemView.setOnClickListener {
            onClick(group)
        }
    }

    override fun getItemCount() = groups.size

    fun updateData(newGroups: List<SupportGroup>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }
}
