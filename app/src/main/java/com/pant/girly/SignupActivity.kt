package com.pant.girly

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.pant.girly.databinding.ActivitySignupBinding
import java.util.concurrent.TimeUnit

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var mAuth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var isOtpRequired = false
    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false
    private var storedVerificationId: String? = null
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mAuth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Hide OTP views initially
        binding.etOtp.visibility = View.GONE
        binding.btnGetOtp.visibility = View.GONE

        // Toggle OTP checkbox
        binding.chkOtpVerification.setOnCheckedChangeListener { _, isChecked ->
            isOtpRequired = isChecked
            toggleOtpVisibility(isChecked)
        }

        // Get OTP button
        binding.btnGetOtp.setOnClickListener {
            val mobile = binding.etMobile.text.toString().trim()
            if (mobile.length < 10) {
                showToast("Enter valid mobile number")
            } else {
                sendOtpToMobile(mobile)
            }
        }

        // Sign Up button
        binding.btnSignUp.setOnClickListener {
            registerUser()
        }

        // Go to login
        binding.tvLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Password visibility toggles
        binding.imgShowHidePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            togglePasswordVisibility(binding.etPassword, isPasswordVisible)
            binding.imgShowHidePassword.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
            )
        }

        binding.imgShowHideConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            togglePasswordVisibility(binding.etConfirmPassword, isConfirmPasswordVisible)
            binding.imgShowHideConfirmPassword.setImageResource(
                if (isConfirmPasswordVisible) R.drawable.ic_eye_open else R.drawable.ic_eye_closed
            )
        }
    }

    private fun toggleOtpVisibility(isChecked: Boolean) {
        binding.etOtp.visibility = if (isChecked) View.VISIBLE else View.GONE
        binding.btnGetOtp.visibility = if (isChecked) View.VISIBLE else View.GONE
    }

    private fun registerUser() {
        val email = binding.etEmail.text.toString().trim()
        val mobile = binding.etMobile.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()
        val otp = binding.etOtp.text.toString().trim()

        if (email.isEmpty() || mobile.isEmpty() || password.isEmpty()) {
            showToast("All fields are required")
            return
        }

        if (password != confirmPassword) {
            showToast("Passwords do not match")
            return
        }

        if (isOtpRequired) {
            if (otp.isEmpty()) {
                showToast("Enter OTP")
                return
            }
            val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, otp)
            signInWithPhoneAuthCredential(credential, email, mobile, password)
        } else {
            createUserInFirebase(email, mobile, password)
        }
    }

    private fun sendOtpToMobile(mobile: String) {
        val phoneNumber = "+91$mobile"

        val options = PhoneAuthOptions.newBuilder(mAuth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    showToast("OTP Auto-verified")
                    binding.etOtp.setText(credential.smsCode)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    showToast("Verification failed: ${e.message}")
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    storedVerificationId = verificationId
                    resendToken = token
                    showToast("OTP sent successfully")
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(
        credential: PhoneAuthCredential,
        email: String,
        mobile: String,
        password: String
    ) {
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    createUserInFirebase(email, mobile, password)
                } else {
                    showToast("OTP Verification failed: ${task.exception?.message}")
                }
            }
    }

    private fun createUserInFirebase(email: String, mobile: String, password: String) {
        mAuth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task: Task<AuthResult> ->
                if (task.isSuccessful) {
                    saveUserData(email, mobile)
                } else {
                    showToast("Sign-up failed: ${task.exception?.message}")
                }
            }
    }

    private fun saveUserData(email: String, mobile: String) {
        val userId = mAuth.currentUser?.uid ?: return
        val user = User(email, mobile)

        db.collection("users").document(userId)
            .set(user)
            .addOnSuccessListener {
                showToast("Sign-up Successful!")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                showToast("Error: ${e.message}")
            }
    }

    private fun togglePasswordVisibility(editText: EditText, isVisible: Boolean) {
        editText.inputType = if (isVisible) {
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        editText.setSelection(editText.text.length)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    data class User(val email: String, val mobile: String)
}
