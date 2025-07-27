package com.pant.girly

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions


class RescueMapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private var targetLocationString: String? = null
    private var targetUserId: String? = null
    private var alertStatus: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rescue_map) // Create this layout file

        targetUserId = intent.getStringExtra("TARGET_USER_ID")
        targetLocationString = intent.getStringExtra("TARGET_LOCATION")
        alertStatus = intent.getStringExtra("ALERT_STATUS")

        // Example of displaying details in this activity
        findViewById<TextView>(R.id.map_details_text_view).text = "User ID: $targetUserId\nStatus: $alertStatus"

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true

        targetLocationString?.let { locString ->
            val parts = locString.split(",")
            if (parts.size == 2) {
                val latitude = parts[0].toDoubleOrNull()
                val longitude = parts[1].toDoubleOrNull()
                if (latitude != null && longitude != null) {
                    val targetLatLng = LatLng(latitude, longitude)
                    googleMap.addMarker(MarkerOptions().position(targetLatLng).title("Alert Location"))
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLatLng, 15f))
                } else {
                    Toast.makeText(this, "Invalid location data.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Invalid location format.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No target location provided.", Toast.LENGTH_SHORT).show()
        }
    }
}