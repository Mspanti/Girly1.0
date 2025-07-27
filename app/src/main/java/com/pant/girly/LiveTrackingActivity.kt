package com.pant.girly

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import org.tensorflow.lite.Interpreter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class LiveTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var currentLocation: Location? = null
    private var isTracking = false
    private lateinit var stopHandler: Handler
    private lateinit var interpreter: Interpreter
    private var riskThreshold = 0.6f
    private var latMean = 0.0
    private var latStd = 1.0
    private var lonMean = 0.0
    private var lonStd = 1.0
    private var previousAlertLevel = 0.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tracking)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        stopHandler = Handler(Looper.getMainLooper())

        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    currentLocation = location
                    val latLng = LatLng(location.latitude, location.longitude)
                    mMap.clear()
                    mMap.addMarker(
                        MarkerOptions().position(latLng).title("You're here")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    )
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    updateLiveLocationToFirebase(latLng)
                    evaluateRiskAndSendAlert(latLng, previousAlertLevel)
                }
            }
        }

        loadModel()

        findViewById<Button>(R.id.btnStartTracking).setOnClickListener { startLocationUpdates() }
        findViewById<Button>(R.id.btnStopTracking).setOnClickListener { stopLocationUpdates() }
        findViewById<Button>(R.id.btnZoomIn).setOnClickListener { mMap.animateCamera(CameraUpdateFactory.zoomIn()) }
        findViewById<Button>(R.id.btnZoomOut).setOnClickListener { mMap.animateCamera(CameraUpdateFactory.zoomOut()) }
        findViewById<Button>(R.id.btnSendLocation).setOnClickListener { sendLocationToEmergencyContacts() }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }
        mMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = it
                val latLng = LatLng(it.latitude, it.longitude)
                mMap.addMarker(MarkerOptions().position(latLng).title("You're here"))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                updateLiveLocationToFirebase(latLng)
            }
        }
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = assets.openFd("crime_alert_model.tflite")
            val fileInputStream = assetFileDescriptor.createInputStream()
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            interpreter = Interpreter(modelBuffer)


            latMean = 10.9400f.toDouble()
            latStd = 2.5f.toDouble()
            lonMean = 79.8600f.toDouble()
            lonStd = 3.0f.toDouble()

        } catch (e: IOException) {
            Log.e("LiveTracking", "Error loading TFLite model", e)
            Toast.makeText(this, "Failed to load model", Toast.LENGTH_SHORT).show()
            finish() // Consider closing the activity if the model fails to load
        } catch (e: Exception) {
            Log.e("LiveTracking", "Unexpected error during model loading", e)
            Toast.makeText(this, "Error loading model", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun evaluateRiskAndSendAlert(latLng: LatLng, previousAlert: Float) {

        val scaledLat = ((latLng.latitude - latMean) / latStd).toFloat()
        val scaledLon = ((latLng.longitude - lonMean) / lonStd).toFloat()

        val input = ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder())
        input.putFloat(scaledLat)
        input.putFloat(scaledLon)
        input.putFloat(previousAlert)

        val output = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())

        interpreter.run(input, output)
        output.rewind()
        val riskScore = output.float

        Log.d("LiveTracking", "Risk Score: $riskScore")

        if (riskScore > riskThreshold) {
            sendAlertToFirebase(latLng, riskScore)
            showRiskNotification(riskScore, latLng)
            previousAlertLevel = riskScore
        } else {
            Log.d("LiveTracking", "Location considered safe (Risk Score: $riskScore)")
            previousAlertLevel = 0.0f
        }
    }

    private fun sendAlertToFirebase(latLng: LatLng, riskScore: Float) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val alertRef = FirebaseDatabase.getInstance().reference.child("location_alerts")
            .child(user.uid).push()
        val alertData = mapOf(
            "latitude" to latLng.latitude,
            "longitude" to latLng.longitude,
            "risk_score" to riskScore,
            "timestamp" to System.currentTimeMillis()
        )
        alertRef.setValue(alertData).addOnSuccessListener {
            Log.d("Firebase", "Alert data saved successfully")
        }.addOnFailureListener { e ->
            Log.e("Firebase", "Error saving alert data: ${e.message}")
        }
    }

    private fun showRiskNotification(score: Float, latLng: LatLng) {
        val channelId = "sos_risk_alert"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SOS Risk Alert", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🚨 Risk Alert")
            .setContentText(
                "High risk detected (Score: ${"%.2f".format(score)}) at Lat: ${
                    "%.4f".format(
                        latLng.latitude
                    )
                }, Lon: ${"%.4f".format(latLng.longitude)}"
            )
            .setSmallIcon(R.drawable.ic_sos_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Automatically remove the notification when tapped
            .build()

        manager.notify(101, notification)
    }

    private fun startLocationUpdates() {
        if (isTracking) return

        locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L) // 5 seconds interval
                .setMinUpdateIntervalMillis(3000L) // 3 seconds minimum update interval
                .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            return
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        Toast.makeText(this, "Live tracking started", Toast.LENGTH_SHORT).show()
        isTracking = true

        stopHandler.postDelayed({ stopLocationUpdates() }, 60_000)
    }

    private fun stopLocationUpdates() {
        if (!isTracking) return

        fusedLocationClient.removeLocationUpdates(locationCallback)
        Toast.makeText(this, "Live tracking stopped", Toast.LENGTH_SHORT).show()
        isTracking = false
    }

    private fun sendLocationToEmergencyContacts() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val dbRef = FirebaseDatabase.getInstance().reference.child("users").child(user.uid)
            .child("emergency_contacts")

        if (currentLocation == null) {
            Toast.makeText(this, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        val message =
            "🚨 Emergency! I'm here: http://maps.google.com/maps?q=$?q=${currentLocation!!.latitude},${currentLocation!!.longitude}"

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 102)
            return
        }

        dbRef.get().addOnSuccessListener { snapshot ->
            for (contact in snapshot.children) {
                val number = contact.child("number").getValue(String::class.java)
                number?.let {
                    try {
                        SmsManager.getDefault().sendTextMessage(it, null, message, null, null)
                        Toast.makeText(this, "Alert sent to $it", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Failed to send SMS to $it", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load emergency contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLiveLocationToFirebase(latLng: LatLng) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val locationMap = mapOf(
            "latitude" to latLng.latitude,
            "longitude" to latLng.longitude,
            "timestamp" to System.currentTimeMillis()
        )
        FirebaseDatabase.getInstance().reference.child("live_locations").child(user.uid).setValue(locationMap)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            onMapReady(mMap)
        } else if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}