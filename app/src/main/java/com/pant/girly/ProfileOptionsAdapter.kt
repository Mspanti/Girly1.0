package com.pant.girly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProfileOptionsAdapter(
    private val options: List<ProfileOption>,
    private val onClick: (ProfileOption) -> Unit
) : RecyclerView.Adapter<ProfileOptionsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvOptionTitle)
        val subtitle: TextView = view.findViewById(R.id.tvOptionSubtitle)
        val icon: ImageView = view.findViewById(R.id.ivOptionIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.profile_option_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option = options[position]
        holder.title.text = option.title
        holder.subtitle.text = option.subtitle
        holder.icon.setImageResource(option.iconRes)

        holder.itemView.setOnClickListener { onClick(option) }
    }

    override fun getItemCount() = options.size
}
