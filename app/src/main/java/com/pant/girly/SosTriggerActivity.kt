package com.pant.girly

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.pant.girly.local_db.AlertEntity
import com.pant.girly.local_db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SosTriggerActivity : AppCompatActivity() {

    private val PERMISSION_CODE = 1001
    private lateinit var db: AppDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val activityScope = CoroutineScope(Dispatchers.Main + Job())
    private var callIndex = 0
    private val CALL_DELAY = 15000L
    private var emergencyNumbers: List<String> = emptyList()

    private val networkChangeReceiver = NetworkChangeReceiver()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        db = AppDatabase.getDatabase(applicationContext)
        auth = FirebaseAuth.getInstance()
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val intentFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkChangeReceiver, intentFilter)

        if (intent?.action == NetworkChangeReceiver.ACTION_SYNC_UNSYNCED_ALERTS) {
            syncUnsyncedAlerts()
        } else {
            if (checkPermissions()) {
                triggerSOS()
            } else {
                requestPermissions()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == NetworkChangeReceiver.ACTION_SYNC_UNSYNCED_ALERTS) {
            syncUnsyncedAlerts()
        }
    }

    private fun checkPermissions(): Boolean {
        return listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS
        ).all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS
            ),
            PERMISSION_CODE
        )
    }

    private fun triggerSOS() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in. Please log in first.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        getLocationAndProcess(user.uid, user.displayName ?: user.email ?: "Unknown User")
    }

    @SuppressLint("MissingPermission")
    private fun getLocationAndProcess(userId: String, userName: String) {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMaxUpdates(1)
            .build()

        if (!checkPermissions()) {
            Toast.makeText(this, "Required permissions missing for SOS.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                fusedLocationProviderClient.removeLocationUpdates(this)
                val location = result.lastLocation
                if (location != null) {
                    handleLocation(userId, userName, location)
                } else {
                    Toast.makeText(this@SosTriggerActivity, "❌ Location unavailable. Try again.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }, Looper.getMainLooper())
    }

    private fun handleLocation(userId: String, userName: String, location: Location) {
        val latLng = "${location.latitude},${location.longitude}"
        val timestamp = System.currentTimeMillis()

        if (isOnline()) {
            saveSosToFirebase(userId, userName, latLng, timestamp)
            Toast.makeText(this, "🚨 SOS Triggered (Online)", Toast.LENGTH_SHORT).show()
        } else {
            saveSosToLocalDatabase(userId, userName, latLng, timestamp)
            Toast.makeText(this, "📴 Offline: SOS saved locally. Will sync when online.", Toast.LENGTH_LONG).show()
        }

        sendAlertToEmergencyContacts(latLng)

        val videoIntent = Intent(this, LiveVideoActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(videoIntent)
        finish()
    }

    private fun saveSosToFirebase(uid: String, userName: String, location: String, timestamp: Long) {
        val sosData = mapOf(
            "timestamp" to timestamp,
            "location" to location,
            "status" to "Pending",
            "userName" to userName
        )
        FirebaseDatabase.getInstance().reference
            .child("sos_alerts").child(uid).child(timestamp.toString())
            .setValue(sosData)
            .addOnSuccessListener {
                Log.d("SosTriggerActivity", "SOS saved to Firebase for $uid")
            }
            .addOnFailureListener {
                Log.e("SosTriggerActivity", "Firebase error saving SOS: ${it.message}")
                Toast.makeText(this, "❌ Firebase error: ${it.message}", Toast.LENGTH_SHORT).show()
                saveSosToLocalDatabase(uid, userName, location, timestamp)
            }
    }

    private fun saveSosToLocalDatabase(userId: String, userName: String, location: String, timestamp: Long) {
        activityScope.launch(Dispatchers.IO) {
            try {
                val alertEntity = AlertEntity(
                    userId = userId,
                    userName = userName,
                    timestamp = timestamp,
                    location = location,
                    status = "Pending",
                    synced = false
                )
                db.alertDao().insert(alertEntity)
                Log.d("SosTriggerActivity", "SOS saved locally for $userId")
            } catch (e: Exception) {
                Log.e("SosTriggerActivity", "Error saving SOS locally: ${e.message}")
                launch(Dispatchers.Main) {
                    Toast.makeText(this@SosTriggerActivity, "Error saving SOS locally: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun syncUnsyncedAlerts() {
        Log.d("SosTriggerActivity", "Attempting to sync unsynced alerts...")
        if (!isOnline()) {
            Toast.makeText(this, "Not online, cannot sync unsynced alerts.", Toast.LENGTH_SHORT).show()
            return
        }

        activityScope.launch(Dispatchers.IO) {
            try {
                val unsyncedAlerts = db.alertDao().getUnsyncedAlerts()
                if (unsyncedAlerts.isEmpty()) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(this@SosTriggerActivity, "No unsynced alerts found.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                for (alert in unsyncedAlerts) {
                    val sosData = mapOf(
                        "timestamp" to alert.timestamp,
                        "location" to alert.location,
                        "status" to alert.status,
                        "userName" to alert.userName
                    )
                    FirebaseDatabase.getInstance().reference
                        .child("sos_alerts").child(alert.userId).child(alert.timestamp.toString())
                        .setValue(sosData)
                        .addOnSuccessListener {
                            activityScope.launch(Dispatchers.IO) {
                                alert.synced = true
                                db.alertDao().update(alert)
                            }
                        }
                }

                launch(Dispatchers.Main) {
                    Toast.makeText(this@SosTriggerActivity, "✅ All unsynced alerts synced!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("SosTriggerActivity", "Error during sync: ${e.message}")
                launch(Dispatchers.Main) {
                    Toast.makeText(this@SosTriggerActivity, "❌ Error syncing alerts: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendAlertToEmergencyContacts(location: String) {
        val user = auth.currentUser ?: return

        val message = "⚠️ SOS Triggered!\nLocation: $location\nPlease respond immediately!"

        activityScope.launch(Dispatchers.IO) {
            try {
                val localContacts = db.emergencyContactDao().getAllContacts().map { it.phoneNumber }

                if (localContacts.isNotEmpty()) {
                    emergencyNumbers = localContacts
                    launch(Dispatchers.Main) {
                        sendSmsToContacts(emergencyNumbers, message)
                        makeNextCall()
                    }
                }

                if (isOnline()) {
                    FirebaseDatabase.getInstance().reference
                        .child("emergency_contacts").child(user.uid)
                        .get().addOnSuccessListener { snapshot ->
                            val firebaseContacts = snapshot.children.mapNotNull {
                                it.child("phoneNumber").getValue(String::class.java)
                            }
                            if (firebaseContacts.isNotEmpty()) {
                                emergencyNumbers = firebaseContacts
                                launch(Dispatchers.Main) {
                                    Toast.makeText(this@SosTriggerActivity, "Updated contacts from Firebase.", Toast.LENGTH_SHORT).show()
                                }
                            }
                            if (localContacts.isEmpty() && firebaseContacts.isNotEmpty()) {
                                launch(Dispatchers.Main) {
                                    sendSmsToContacts(emergencyNumbers, message)
                                    makeNextCall()
                                }
                            }
                        }
                }

            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@SosTriggerActivity, "Error fetching emergency contacts.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendSmsToContacts(numbers: List<String>, message: String) {
        if (numbers.isEmpty()) return
        for (number in numbers) {
            SmsUtil.sendSMS(this, number, message)
        }
        Toast.makeText(this, "SMS sent to emergency contacts.", Toast.LENGTH_SHORT).show()
    }

    private fun makeNextCall() {
        if (emergencyNumbers.isEmpty()) return

        if (callIndex >= emergencyNumbers.size) {
            callIndex = 0
            Toast.makeText(this, "All emergency calls attempted.", Toast.LENGTH_SHORT).show()
            return
        }

        val phoneNumber = emergencyNumbers[callIndex]
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "❌ CALL_PHONE permission denied.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(callIntent)
            Toast.makeText(this, "📞 Calling $phoneNumber...", Toast.LENGTH_SHORT).show()
            callIndex++
            Handler(Looper.getMainLooper()).postDelayed({ makeNextCall() }, CALL_DELAY)
        } catch (e: Exception) {
            Toast.makeText(this, "Call failed: ${e.message}", Toast.LENGTH_SHORT).show()
            callIndex++
            makeNextCall()
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                triggerSOS()
            } else {
                Toast.makeText(this, "Permissions are required to trigger SOS.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(networkChangeReceiver)
        activityScope.cancel()
    }
}
