package com.pant.girly

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.pant.girly.databinding.ActivityRegisteredAddressBinding

class RegisteredAddressActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityRegisteredAddressBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisteredAddressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase & Location Services
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Setup Toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Registered Address"

        // Initialize Map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Click listeners
        binding.btnUseCurrentLocation.setOnClickListener { getCurrentLocation() }
        binding.btnSaveAddress.setOnClickListener { saveAddress() }

        // Load previously saved address
        loadAddress()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLocation = location
                val latLng = LatLng(location.latitude, location.longitude)

                googleMap.clear() // Clear previous markers
                googleMap.addMarker(MarkerOptions().position(latLng).title("Current Location"))
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                showToast("Location updated successfully!")
            } else {
                showToast("Failed to get location!")
            }
        }.addOnFailureListener {
            showToast("Error fetching location: ${it.message}")
        }
    }

    private fun loadAddress() {
        val currentUser = auth.currentUser ?: return
        database.reference.child("addresses").child(currentUser.uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    binding.etAddressLine1.setText(snapshot.child("line1").getValue(String::class.java))
                    binding.etAddressLine2.setText(snapshot.child("line2").getValue(String::class.java))
                    binding.etCity.setText(snapshot.child("city").getValue(String::class.java))
                    binding.etState.setText(snapshot.child("state").getValue(String::class.java))
                    binding.etPostalCode.setText(snapshot.child("postalCode").getValue(String::class.java))
                    binding.etCountry.setText(snapshot.child("country").getValue(String::class.java))

                    val lat = snapshot.child("latitude").getValue(Double::class.java)
                    val lng = snapshot.child("longitude").getValue(Double::class.java)

                    if (lat != null && lng != null) {
                        val latLng = LatLng(lat, lng)
                        googleMap.clear()
                        googleMap.addMarker(MarkerOptions().position(latLng).title("Saved Address"))
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showToast("Failed to load address: ${error.message}")
            }
        })
    }

    private fun saveAddress() {
        val currentUser = auth.currentUser ?: return
        val address = hashMapOf(
            "line1" to binding.etAddressLine1.text.toString(),
            "line2" to binding.etAddressLine2.text.toString(),
            "city" to binding.etCity.text.toString(),
            "state" to binding.etState.text.toString(),
            "postalCode" to binding.etPostalCode.text.toString(),
            "country" to binding.etCountry.text.toString(),
            "latitude" to currentLocation?.latitude,
            "longitude" to currentLocation?.longitude
        )

        database.reference.child("addresses").child(currentUser.uid).setValue(address).addOnCompleteListener {
            if (it.isSuccessful) {
                showToast("Address saved successfully!")
                finish()
            } else {
                showToast("Error saving address: ${it.exception?.message}")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation()
        } else {
            showToast("Location permission denied.")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
