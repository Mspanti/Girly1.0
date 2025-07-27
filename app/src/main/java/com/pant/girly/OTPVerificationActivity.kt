package com.pant.girly

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class OTPVerificationActivity : AppCompatActivity() {

    private lateinit var etOtp: EditText
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnVerifyEmail: Button
    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var mobile: String? = null
    private var email: String? = null
    private var verificationId: String? = null

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_verification)

        etOtp = findViewById(R.id.etOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnVerifyEmail = findViewById(R.id.btnVerifyEmail)

        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        email = intent.getStringExtra("email")
        mobile = intent.getStringExtra("mobile")

        mobile?.let { sendOtpToMobile(it) }

        btnVerifyOtp.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            if (TextUtils.isEmpty(otp)) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyOtp(otp)
        }

        btnVerifyEmail.setOnClickListener {
            createUserInDatabase(email, mobile)
        }
    }

    private fun sendOtpToMobile(mobile: String) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(
            "+91$mobile",
            60,
            TimeUnit.SECONDS,
            this,
            object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Toast.makeText(this@OTPVerificationActivity, "Auto-verification completed", Toast.LENGTH_SHORT).show()
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@OTPVerificationActivity, "OTP Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                }
            }
        )
    }

    private fun verifyOtp(otp: String) {
        verificationId?.let { id ->
            val credential = PhoneAuthProvider.getCredential(id, otp)
            mAuth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        createUserInDatabase(email, mobile)
                    } else {
                        Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun createUserInDatabase(email: String?, mobile: String?) {
        val user = mAuth.currentUser
        user?.let {
            val userId = it.uid
            val userData = hashMapOf(
                "email" to email,
                "mobile" to mobile
            )

            db.collection("users").document(userId).set(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Sign-up Successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}