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
        private val pfp: ShapeableImageView = itemView.findViewById(R.id.support_pfp)
        private val name: TextView = itemView.findViewById(R.id.support_name)
        private val memberNum: TextView = itemView.findViewById(R.id.support_memberNum)

        fun bind(group: SupportGroup) {
            name.text = group.name
            memberNum.text = "${group.members.size} Members  |  Join Now!"

            if (!group.pfpUrl.isNullOrBlank()) {
                Glide.with(itemView)
                    .load(group.pfpUrl)
                    .placeholder(R.drawable.img_placeholder)
                    .into(pfp)
            } else {
                pfp.setImageResource(R.drawable.img_placeholder)
            }

            itemView.setOnClickListener {
                // Prevent multiple rapid clicks
                itemView.isEnabled = false
                onClick(group)
                itemView.isEnabled = true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_supportgroup, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount(): Int = groups.size

    fun updateData(newGroups: List<SupportGroup>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }
}

