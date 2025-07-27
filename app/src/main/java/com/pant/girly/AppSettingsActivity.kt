package com.pant.girly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.pant.girly.databinding.AppSettingsActivityBinding

class AppSettingsActivity : AppCompatActivity() {

    private lateinit var binding: AppSettingsActivityBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AppSettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "App Settings"

        // Load settings from Firebase
        loadSettings()

        // Save button
        binding.btnSaveSettings.setOnClickListener { saveSettings() }
        binding.btnViewHistory.setOnClickListener { loadLocationHistory() }

        // Location toggle
        binding.switchLocation.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) enableLocationTracking()
        }

        // Shake detection toggle
        binding.switchShakeDetection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startShakeDetectionService()
            } else {
                stopShakeDetectionService()
            }
        }
        val sharedPrefs = getSharedPreferences("AppSettingsPrefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("DarkMode", false)
        binding.switchTheme.isChecked = isDarkMode
        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPrefs.edit()
            editor.putBoolean("DarkMode", isChecked)
            editor.apply()

            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }

    private fun startShakeDetectionService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 101)
                return
            }
        }

        val serviceIntent = Intent(this, ForegroundShakeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        showToast("Shake detection enabled")
    }

    private fun stopShakeDetectionService() {
        val serviceIntent = Intent(this, ForegroundShakeService::class.java)
        stopService(serviceIntent)
        showToast("Shake detection disabled")
    }

    private fun enableLocationTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            return
        }
        showToast("Location tracking enabled!")
    }

    private fun loadSettings() {
        val currentUser = auth.currentUser ?: return
        val settingsRef = database.reference.child("settings").child(currentUser.uid)

        settingsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    binding.switchNotifications.isChecked = snapshot.child("notifications").getValue(Boolean::class.java) ?: true
                    binding.switchLocation.isChecked = snapshot.child("locationTracking").getValue(Boolean::class.java) ?: false
                    binding.switchShakeDetection.isChecked = snapshot.child("shakeDetection").getValue(Boolean::class.java) ?: false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showToast("Failed to load settings: ${error.message}")
            }
        })
    }

    private fun saveSettings() {
        val currentUser = auth.currentUser ?: return
        val settings = mapOf(
            "notifications" to binding.switchNotifications.isChecked,
            "locationTracking" to binding.switchLocation.isChecked,
            "shakeDetection" to binding.switchShakeDetection.isChecked
        )

        database.reference.child("settings").child(currentUser.uid)
            .setValue(settings)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    showToast("Settings saved successfully!")
                    finish()
                } else {
                    showToast("Error saving settings: ${task.exception?.message}")
                }
            }
    }

    private fun loadLocationHistory() {
        val currentUser = auth.currentUser ?: return
        database.reference.child("location_history").child(currentUser.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val historyList = mutableListOf<String>()
                    snapshot.children.forEach { location ->
                        val locationData = "Lat: ${location.child("latitude").value}, Lng: ${location.child("longitude").value}"
                        historyList.add(locationData)
                    }
                    showToast("Location History: \n${historyList.joinToString("\n")}")
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Failed to load location history: ${error.message}")
                }
            })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
