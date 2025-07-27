package com.pant.girly

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import com.pant.girly.databinding.ActivityCommunityBinding
import com.pant.girly.models.EmergencyAlert
import java.util.concurrent.TimeUnit

class CommunityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommunityBinding
    private lateinit var adapter: CommunityAlertAdapter
    private val alerts = mutableListOf<EmergencyAlert>() // Holds current alerts
    private var currentUserLocation: Location? = null // User's last known location
    private val databaseRef = FirebaseDatabase.getInstance().reference.child("sos_alerts") // Firebase DB reference
    private var nearbyAlertDialog: AlertDialog? = null // Dialog for nearby alerts

    private lateinit var fusedLocationClient: FusedLocationProviderClient // For location updates
    private lateinit var locationRequest: LocationRequest // Request configuration for location
    private lateinit var locationCallback: LocationCallback // Callback for location updates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up the Toolbar as the ActionBar
        setSupportActionBar(binding.toolbar)

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationRequest()
        createLocationCallback()

        // Set up UI components
        setupRecyclerView()
        setupSwipeRefreshLayout()

        // Start fetching data and location
        startLocationUpdates()
        observeSOSAlerts()
    }

    // Called when the activity's menu is created
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_community, menu) // Inflate the menu layout
        return true
    }

    // Handles clicks on menu items (Filter and Sort)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter_pending -> { adapter.applyFilter("Pending"); true }
            R.id.action_filter_responded -> { adapter.applyFilter("Responded"); true }
            R.id.action_filter_resolved -> { adapter.applyFilter("Resolved"); true }
            R.id.action_filter_nearby -> { adapter.applyFilter("Nearby"); true }
            R.id.action_filter_all -> { adapter.applyFilter("All"); true }
            R.id.action_sort_distance -> { adapter.applySortOrder("Distance"); true }
            R.id.action_sort_time -> { adapter.applySortOrder("Time"); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // Lifecycle method: Clean up resources when activity is destroyed
    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates() // Stop receiving location updates
        nearbyAlertDialog?.dismiss() // Dismiss any showing dialog
        nearbyAlertDialog = null
    }

    // Sets up the RecyclerView with an adapter and layout manager
    private fun setupRecyclerView() {
        binding.alertsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CommunityAlertAdapter(alerts) { alert ->
            // Handle item click: navigate to RescueMapActivity
            startActivity(Intent(this, RescueMapActivity::class.java).apply {
                putExtra("TARGET_USER_ID", alert.userId)
                putExtra("TARGET_LOCATION", alert.location)
                putExtra("ALERT_STATUS", alert.status)
            })
        }
        binding.alertsRecyclerView.adapter = adapter
    }

    // Sets up the SwipeRefreshLayout for pull-to-refresh functionality
    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            observeSOSAlerts() // Re-fetch alerts on refresh
        }
    }

    // Observes SOS alerts from Firebase Realtime Database
    private fun observeSOSAlerts() {
        // Use addListenerForSingleValueEvent for one-time fetch on refresh
        // If you need real-time updates without pull-to-refresh, use addValueEventListener
        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val fetchedAlerts = mutableListOf<EmergencyAlert>()
                for (userAlertsSnapshot in snapshot.children) {
                    val userId = userAlertsSnapshot.key // Get user ID from Firebase key
                    for (alertSnapshot in userAlertsSnapshot.children) {
                        // Deserialize alert data
                        val alertData = alertSnapshot.getValue(object : GenericTypeIndicator<Map<String, Any>>() {})
                        alertData?.let {
                            val timestamp = it["timestamp"] as? Long ?: 0
                            val location = it["location"] as? String ?: ""
                            val status = it["status"] as? String ?: ""
                            // Fetch user's display name from Firebase Auth
                            val userName = FirebaseAuth.getInstance().currentUser?.displayName ?: "Unknown User"
                            val emergencyAlert = EmergencyAlert(userId, userName, timestamp, location, status)
                            fetchedAlerts.add(emergencyAlert)

                            // Check for nearby alerts and show dialog if applicable
                            if (currentUserLocation != null && isNearby(location)) {
                                showNearbyAlertDialog(userName, userId)
                            }
                        }
                    }
                }
                adapter.updateAlerts(fetchedAlerts) // Update adapter with new data
                currentUserLocation?.let { adapter.currentUserLocation = it } // Pass current location to adapter
                // Show/hide empty state text based on alert list
                binding.emptyStateTextView.visibility = if (fetchedAlerts.isEmpty()) View.VISIBLE else View.GONE
                binding.swipeRefreshLayout.isRefreshing = false // Stop refresh animation
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@CommunityActivity, "Error loading SOS alerts: ${error.message}", Toast.LENGTH_SHORT).show()
                binding.swipeRefreshLayout.isRefreshing = false
                binding.emptyStateTextView.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
            }
        })
    }

    // Creates the configuration for location updates
    private fun createLocationRequest() {
        locationRequest = LocationRequest.create().apply {
            interval = TimeUnit.SECONDS.toMillis(10) // Update every 10 seconds
            fastestInterval = TimeUnit.SECONDS.toMillis(5) // Fastest update interval
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY // Request highest accuracy
        }
    }

    // Defines the callback for location updates
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    currentUserLocation = it
                    adapter.currentUserLocation = it // Update location in adapter
                }
            }
        }
    }

    // Starts requesting location updates
    @SuppressLint("MissingPermission") // Suppress permission warning as we handle it
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } else {
            // Request permission if not granted
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    // Stops receiving location updates
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // Handles permission request results
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates() // Permission granted, start updates
            } else {
                Toast.makeText(this, "Location permission denied. Cannot show nearby alerts.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Checks if an alert is within 1km of the current user's location
    private fun isNearby(locationString: String): Boolean {
        if (currentUserLocation == null) return false

        val parts = locationString.split(",")
        if (parts.size != 2) return false

        val alertLat = parts[0].toDoubleOrNull() ?: return false
        val alertLon = parts[1].toDoubleOrNull() ?: return false

        val alertLoc = Location("").apply {
            latitude = alertLat
            longitude = alertLon
        }

        return currentUserLocation!!.distanceTo(alertLoc) <= 1000 // within 1km (1000 meters)
    }

    // Shows an AlertDialog for nearby SOS alerts
    private fun showNearbyAlertDialog(userName: String, userId: String?) {
        // Dismiss previous dialog if it's showing to avoid multiple dialogs
        if (nearbyAlertDialog?.isShowing == true) {
            nearbyAlertDialog?.dismiss()
            nearbyAlertDialog = null
        }
        nearbyAlertDialog = AlertDialog.Builder(this)
            .setTitle("New SOS Alert!")
            .setMessage("⚠ $userName needs help nearby.")
            .setPositiveButton("View") { _, _ ->
                // Find the alert and navigate to RescueMapActivity
                alerts.find { it.userId == userId }?.let { alert ->
                    startActivity(Intent(this, RescueMapActivity::class.java).apply {
                        putExtra("TARGET_USER_ID", alert.userId)
                        putExtra("TARGET_LOCATION", alert.location)
                        putExtra("ALERT_STATUS", alert.status)
                    })
                }
            }
            .setNegativeButton("Ignore", null) // "Ignore" button dismisses the dialog
            .show()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001 // Request code for location permission
    }
}