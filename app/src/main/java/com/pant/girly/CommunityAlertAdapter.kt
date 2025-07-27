package com.pant.girly

import android.location.Location
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pant.girly.databinding.ItemCommunityAlertBinding
import com.pant.girly.databinding.ItemCommunityAlertBinding.inflate
import com.pant.girly.models.EmergencyAlert
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CommunityAlertAdapter(
    private var alerts: MutableList<EmergencyAlert>,
    private val onItemClick: (EmergencyAlert) -> Unit
) : RecyclerView.Adapter<CommunityAlertAdapter.AlertViewHolder>() {

    // Current user's location to calculate distance for alerts
    var currentUserLocation: Location? = null
        set(value) {
            field = value
            // When location updates, re-sort and notify adapter to update UI
            sortAlerts()
            notifyDataSetChanged()
        }

    // A copy of all alerts to apply filters
    private var originalAlerts = alerts.toList()
    private var currentFilter: String = "All" // Current filter applied
    private var currentSortOrder: String = "Distance" // Current sort order

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        // Inflate the layout using View Binding
        val binding = inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(alerts[position])
    }

    override fun getItemCount(): Int = alerts.size

    // Updates the list of alerts and applies current filters/sort
    fun updateAlerts(newAlerts: List<EmergencyAlert>) {
        originalAlerts = newAlerts
        applyFiltersAndSort() // Apply filters and sort after new data
        notifyDataSetChanged()
    }

    // Applies a new filter to the alerts
    fun applyFilter(filter: String) {
        currentFilter = filter
        applyFiltersAndSort()
        notifyDataSetChanged() // Notify UI about data change
    }

    // Applies a new sort order to the alerts
    fun applySortOrder(sortOrder: String) {
        currentSortOrder = sortOrder
        sortAlerts()
        notifyDataSetChanged() // Notify UI about data change
    }

    // Filters the original alerts based on the current filter
    private fun applyFiltersAndSort() {
        alerts = originalAlerts.filter { alert ->
            when (currentFilter) {
                "Pending" -> alert.status.equals("Pending", ignoreCase = true)
                "Responded" -> alert.status.equals("Responded", ignoreCase = true)
                "Resolved" -> alert.status.equals("Resolved", ignoreCase = true)
                "Nearby" -> isNearby(alert.location)
                else -> true // "All" or unknown filter
            }
        }.toMutableList()
        sortAlerts() // Always sort after filtering
    }

    // Sorts the current list of alerts based on currentSortOrder
    private fun sortAlerts() {
        currentUserLocation?.let { currentLocation ->
            alerts.sortWith(compareBy { alert ->
                when (currentSortOrder) {
                    "Distance" -> calculateDistance(currentLocation, alert.location)
                    "Time" -> -alert.timestamp.toDouble() // Descending by time
                    else -> calculateDistance(currentLocation, alert.location) // Default to distance
                }
            })
        } ?: run {
            // If current user location is not available, default to sorting by time (descending)
            alerts.sortByDescending { it.timestamp }
        }
    }

    // Calculates distance between two locations using Haversine formula (more accurate)
    private fun calculateDistance(current: Location, alertLocationString: String?): Float {
        if (alertLocationString == null) return Float.MAX_VALUE // Return max value if location is null
        val parts = alertLocationString.split(",")
        if (parts.size == 2) {
            val lat2 = parts[0].toDoubleOrNull() ?: 0.0
            val lon2 = parts[1].toDoubleOrNull() ?: 0.0
            val lat1 = current.latitude
            val lon1 = current.longitude

            val R = 6371e3 // Radius of the Earth in meters
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val deltaPhi = Math.toRadians(lat2 - lat1)
            val deltaLambda = Math.toRadians(lon2 - lon1)

            val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                    cos(phi1) * cos(phi2) *
                    sin(deltaLambda / 2) * sin(deltaLambda / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))

            return (R * c).toFloat() // Distance in meters
        }
        return Float.MAX_VALUE
    }

    // Checks if an alert location is nearby the current user's location (within 1km)
    private fun isNearby(locationString: String?): Boolean {
        if (locationString == null || currentUserLocation == null) return false
        val parts = locationString.split(",")
        if (parts.size == 2) {
            val alertLoc = Location("").apply {
                latitude = parts[0].toDoubleOrNull() ?: 0.0
                longitude = parts[1].toDoubleOrNull() ?: 0.0
            }
            return currentUserLocation!!.distanceTo(alertLoc) <= 1000 // within 1km
        }
        return false
    }

    // ViewHolder class for RecyclerView items
    inner class AlertViewHolder(private val binding: ItemCommunityAlertBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(alert: EmergencyAlert) {
            binding.alert = alert // Bind data to layout using Data Binding
            // Format timestamp for better readability
            binding.timestampTextView.text = Date(alert.timestamp).toLocaleString()

            // Calculate and display distance
            currentUserLocation?.let {
                val distance = calculateDistance(it, alert.location)
                binding.distanceTextView.text = if (distance < Float.MAX_VALUE) {
                    val distanceInKm = distance / 1000
                    String.format(Locale.getDefault(), "%.1f km away", distanceInKm)
                } else {
                    "Distance: Unknown"
                }
            } ?: run {
                binding.distanceTextView.text = "Distance: Calculating..."
            }

            // Set click listener for the item
            binding.root.setOnClickListener { onItemClick(alert) }
            binding.executePendingBindings() // Crucial for immediate UI updates with Data Binding
        }
    }
}