package com.pant.girly
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecyclerView {


    lateinit var layoutManager: Any

    class BluetoothDeviceAdapter(private val deviceList: List<BluetoothDeviceInfo>, private val itemClickListener: (BluetoothDeviceInfo) -> Unit) :
        RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val nameTextView: TextView = itemView.findViewById(android.R.id.text1)
            val addressTextView: TextView = itemView.findViewById(android.R.id.text2)

            fun bind(deviceInfo: BluetoothDeviceInfo, clickListener: (BluetoothDeviceInfo) -> Unit) {
                nameTextView.text = deviceInfo.name ?: "Unknown Device"
                addressTextView.text = deviceInfo.address
                itemView.setOnClickListener { clickListener(deviceInfo) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val currentDevice = deviceList[position]
            holder.bind(currentDevice, itemClickListener)
        }

        override fun getItemCount() = deviceList.size
    }
}