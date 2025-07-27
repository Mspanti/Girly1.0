package com.pant.girly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmergencyContactsAdapter(
    private val contacts: MutableList<EmergencyContact>,
    private val onDelete: (EmergencyContact) -> Unit,
    private val onEdit: (EmergencyContact) -> Unit
) : RecyclerView.Adapter<EmergencyContactsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.tvContactName)
        val phoneTextView: TextView = itemView.findViewById(R.id.tvContactPhone)
        val emailTextView: TextView = itemView.findViewById(R.id.tvContactEmail)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteContact)
        val editButton: ImageButton = itemView.findViewById(R.id.btnEditContact)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]

        holder.nameTextView.text = contact.name
        holder.phoneTextView.text = contact.phoneNumber

        if (contact.email.isNullOrEmpty()) {
            holder.emailTextView.visibility = View.GONE
        } else {
            holder.emailTextView.text = contact.email
            holder.emailTextView.visibility = View.VISIBLE
        }

        holder.deleteButton.setOnClickListener {
            onDelete(contact)
        }

        holder.editButton.setOnClickListener {
            onEdit(contact)
        }
    }

    override fun getItemCount(): Int = contacts.size

    fun updateContacts(newContacts: List<EmergencyContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }
}