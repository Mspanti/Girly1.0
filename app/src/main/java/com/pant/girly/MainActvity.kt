package com.pant.girly

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.util.Timer
import java.util.TimerTask

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAG = "GirlyApp"
    }

    private lateinit var sosButtonContainer: FloatingActionButton
    private lateinit var safetyModeSwitch: Switch
    private lateinit var welcomeTextView: TextView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var cardLiveTracking: CardView
    private lateinit var cardAnonymousReport: CardView
    private lateinit var cardWatch: CardView
    private lateinit var cardEmergencyContacts: CardView
    private var isVoiceDetectionActive = false
    private lateinit var voiceDetectionToggleIcon: ImageView

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private val timer = Timer()
    private val RECORD_AUDIO_PERMISSION_CODE = 101
    private val REQUEST_BATTERY_OPTIMIZATION = 102

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }


        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()


        welcomeTextView = findViewById(R.id.welcomeTextView)
        val sosButtonContainer: FrameLayout = findViewById(R.id.sosButtonContainer)
        safetyModeSwitch = findViewById(R.id.safetyModeSwitch)
        bottomNavigation = findViewById(R.id.bottom_navigation)
        cardLiveTracking = findViewById(R.id.cardLiveTracking)
        cardAnonymousReport = findViewById(R.id.cardAnonymousReport)
        cardWatch = findViewById(R.id.cardWatch)
        cardEmergencyContacts = findViewById(R.id.cardEmergencyContacts)
        voiceDetectionToggleIcon = findViewById(R.id.voiceDetectionToggleIcon)
        val headerLayout: LinearLayout = findViewById(R.id.header)
        headerLayout.setOnClickListener {
            isVoiceDetectionActive = !isVoiceDetectionActive
            if (isVoiceDetectionActive) {
                Log.d("VoiceDetection", "Voice detection ON (Tap)")
                startVoiceDetectionService()
            } else {
                Log.d("VoiceDetection", "Voice detection OFF (Tap)")
                stopService(Intent(this, VoiceDetectionService::class.java))
            }


            val fadeOut = ObjectAnimator.ofFloat(voiceDetectionToggleIcon, "alpha", 1f, 0f)
            val fadeIn = ObjectAnimator.ofFloat(voiceDetectionToggleIcon, "alpha", 0f, 1f)
            fadeOut.duration = 200
            fadeIn.duration = 200

            fadeOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val newIcon = if (isVoiceDetectionActive) R.drawable.ic_mic else R.drawable.ic_mic_off
                    voiceDetectionToggleIcon.setImageResource(newIcon)
                    fadeIn.start()
                }
            })

            fadeOut.start()
        }



        if (auth.currentUser == null) {
            redirectToLogin()
        } else {
            checkIfUserFormIsFilled()
        }


        sosButtonContainer.setOnClickListener {
            startActivity(Intent(this, SosTriggerActivity::class.java))
        }





        safetyModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            Log.d("SafetyModeSwitch", "Switch state: $isChecked")
            if (isChecked) {
                startSafetyFeatures()
            } else {
                stopSafetyFeatures()
            }
            val message = if (isChecked) "Safety Mode ON" else "Safety Mode OFF"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }


        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_community -> {
                    startActivity(Intent(this, CommunityActivity::class.java))
                    true
                }
                R.id.nav_me -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                else -> false
            }
        }

        cardLiveTracking.setOnClickListener {
            startActivity(Intent(this, LiveTrackingActivity::class.java))
        }

        cardAnonymousReport.setOnClickListener {
            startActivity(Intent(this, AnonymousReportActivity::class.java))
        }

        cardWatch.setOnClickListener {
            startActivity(Intent(this, WatchActivity::class.java))
        }

        cardEmergencyContacts.setOnClickListener {
            startActivity(Intent(this, EmergencyContactsActivity::class.java))
        }

        startSensorPredictionLoop()
        requestBatteryOptimizationExemption()
    }

    private fun startSafetyFeatures() {
        Log.d("SafetyMode", "Starting safety features")
        startService(Intent(this, VoiceDetectionService::class.java))
        startService(Intent(this, WearListenerService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, ForegroundShakeService::class.java))
        }

    }

    private fun stopSafetyFeatures() {
        Log.d("SafetyMode", "Stopping safety features")
        stopService(Intent(this, VoiceDetectionService::class.java))
        stopService(Intent(this, WearListenerService::class.java))
        stopService(Intent(this, ForegroundShakeService::class.java))

    }

    private fun checkIfUserFormIsFilled() {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("Users").child(userId)

        userRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val name = snapshot.child("name").value?.toString() ?: "User"
                welcomeTextView.text = "Welcome, $name!"
            } else {
                startActivity(Intent(this, UserFormActivity::class.java))
                finish()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error checking profile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun checkAudioPermissionAndStartVoiceService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            startVoiceDetectionService()
        }
    }

    private fun startVoiceDetectionService() {
        val serviceIntent = Intent(this, VoiceDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        Toast.makeText(this, "Voice detection service toggled", Toast.LENGTH_SHORT).show()
    }

    private fun startSensorPredictionLoop() {
        timer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    val (heartRate, motionLevel) = WearListenerService.getSensorData()
                    WearListenerService.predict(applicationContext, heartRate, motionLevel)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in sensor prediction loop: ${e.message}")
                }
            }
        }, 0, 5000)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceDetectionService()
        } else {
            Toast.makeText(this, "Audio record permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivityForResult(intent, REQUEST_BATTERY_OPTIMIZATION)
                } catch (e: ActivityNotFoundException) {
                    Log.e(TAG, "Battery optimization setting not found: ${e.message}")
                    Toast.makeText(this, "Battery optimization setting not found.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer.cancel()
    }
}