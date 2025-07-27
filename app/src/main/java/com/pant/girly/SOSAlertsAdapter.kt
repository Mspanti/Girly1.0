package com.pant.girly

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class SOSAlertsAdapter(
    private val alertList: MutableList<SOSAlert>,
    private val onDeleteClick: (SOSAlert) -> Unit
) : RecyclerView.Adapter<SOSAlertsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val locationText: TextView = itemView.findViewById(R.id.tvAlertLocation)
        val timeText: TextView = itemView.findViewById(R.id.tvAlertTime)
        val statusText: TextView = itemView.findViewById(R.id.tvAlertStatus)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteAlert)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sos_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = alertList[position]
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        holder.locationText.text = "Location: ${alert.location}"
        holder.timeText.text = "Time: ${dateFormat.format(alert.timestamp)}"
        holder.statusText.text = "Status: ${alert.status}"

        holder.deleteButton.setOnClickListener {
            onDeleteClick(alert)
        }
    }

    override fun getItemCount(): Int = alertList.size

    fun removeAlert(alert: SOSAlert) {
        val position = alertList.indexOf(alert)
        if (position != -1) {
            alertList.removeAt(position)
            notifyItemRemoved(position)
        }
    }
}