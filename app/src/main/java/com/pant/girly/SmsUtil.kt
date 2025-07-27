package com.pant.girly


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.core.app.ActivityCompat

object SmsUtil {

    private const val SMS_PERMISSION = Manifest.permission.SEND_SMS
    private const val REQUEST_SMS_PERMISSION_CODE = 2001

    fun sendSMS(context: Context, phoneNumber: String, message: String) {
        if (ActivityCompat.checkSelfPermission(context, SMS_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            // Optional: request permission if in an Activity
            if (context is Activity) {
                ActivityCompat.requestPermissions(
                    context,
                    arrayOf(SMS_PERMISSION),
                    REQUEST_SMS_PERMISSION_CODE
                )
            }
            Toast.makeText(context, "SMS permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            Toast.makeText(context, "✅ SMS sent to $phoneNumber", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Failed to send SMS: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
}
