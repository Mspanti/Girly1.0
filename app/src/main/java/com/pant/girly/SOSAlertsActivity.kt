package com.pant.girly

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.pant.girly.SOSAlert
import java.text.SimpleDateFormat
import java.util.*

class SOSAlertsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SOSAlertsAdapter
    private val alertList = mutableListOf<SOSAlert>()
    private val emergencyContacts = mutableListOf<EmergencyContact>()
    private val handledAlertTimestamps = mutableSetOf<Long>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val dbRef by lazy { FirebaseDatabase.getInstance().reference }

    private var lastSmsSentTime: Long = 0
    private val SMS_PERMISSION_CODE = 101
    private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sos_alerts)

        setupRecyclerView()
        checkSmsPermission()
        fetchEmergencyContacts()
        observeSOSAlerts()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.rvSOSAlerts)
        adapter = SOSAlertsAdapter(alertList) { alert -> deleteAlert(alert) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun deleteAlert(alert: SOSAlert) {
        auth.currentUser?.uid?.let { uid ->
            dbRef.child("sos_alerts").child(uid).child(alert.timestamp.toString())
                .removeValue()
                .addOnSuccessListener {
                    adapter.removeAlert(alert)
                    toast("Alert deleted")
                }
                .addOnFailureListener {
                    toast("Failed to delete alert")
                }
        }
    }

    private fun checkSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == SMS_PERMISSION_CODE && (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            toast("SMS permission required for alerts")
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun fetchEmergencyContacts() {
        auth.currentUser?.uid?.let { uid ->
            dbRef.child("emergency_contacts").child(uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        emergencyContacts.clear()
                        for (snap in snapshot.children) {
                            snap.getValue(EmergencyContact::class.java)?.let {
                                emergencyContacts.add(it)
                            }
                        }
                        Log.d("SOS", "Fetched contacts: $emergencyContacts")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        toast("Error loading contacts")
                    }
                })
        }
    }

    private fun observeSOSAlerts() {
        auth.currentUser?.uid?.let { uid ->
            dbRef.child("sos_alerts").child(uid)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        alertList.clear()
                        for (snap in snapshot.children) {
                            val alert = snap.getValue(SOSAlert::class.java)
                            alert?.let {
                                alertList.add(it)

                                if (!handledAlertTimestamps.contains(it.timestamp)) {
                                    handledAlertTimestamps.add(it.timestamp)
                                    maybeSendSms(it)
                                }
                            }
                        }
                        alertList.sortByDescending { it.timestamp }
                        adapter.notifyDataSetChanged()
                    }

                    override fun onCancelled(error: DatabaseError) {
                        toast("Error loading alerts")
                    }
                })
        }
    }

    private fun maybeSendSms(alert: SOSAlert) {
        val now = System.currentTimeMillis()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        lastSmsSentTime = now
        val smsManager = SmsManager.getDefault()
        val username = auth.currentUser?.displayName ?: "User"
        val message = """
            🚨 EMERGENCY ALERT 🚨
            $username needs help!
            📍 Location: ${alert.location}
            🕒 Time: ${DATE_FORMAT.format(alert.timestamp)}
        """.trimIndent()

        emergencyContacts.take(5).forEach { contact ->
            try {
                smsManager.sendTextMessage(contact.phoneNumber, null,  message, null, null)
                Log.d("SOS_SMS", "SMS sent to ${contact.phoneNumber}")
            } catch (e: Exception) {
                Log.e("SOS_SMS", "Failed to send SMS to ${contact.phoneNumber}: ${e.message}")
                toast("Failed to send SMS to ${contact.phoneNumber}")
            }
        }

        toast("SMS alert sent to contacts")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
