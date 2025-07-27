package com.pant.girly

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var loginOptions: RadioGroup
    private lateinit var emailPasswordOption: RadioButton
    private lateinit var mobileOtpOption: RadioButton
    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var passwordLayout: RelativeLayout
    private lateinit var otpField: EditText
    private lateinit var loginButton: Button
    private lateinit var sendOtpButton: Button
    private lateinit var verifyOtpButton: Button
    private lateinit var textSignUp: TextView
    private lateinit var forgotPassword: TextView
    private lateinit var progressBar: ProgressBar

    private var verificationId: String? = null
    private var isPasswordVisible = false

    @SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Initialize views
        loginOptions = findViewById(R.id.loginOptions)
        emailPasswordOption = findViewById(R.id.emailPasswordOption)
        mobileOtpOption = findViewById(R.id.mobileOtpOption)
        emailField = findViewById(R.id.emailField)
        passwordField = findViewById(R.id.passwordField)
        passwordLayout = findViewById(R.id.passwordLayout)
        otpField = findViewById(R.id.otpField)
        loginButton = findViewById(R.id.loginButton)
        sendOtpButton = findViewById(R.id.sendOtpButton)
        verifyOtpButton = findViewById(R.id.verifyOtpButton)
        textSignUp = findViewById(R.id.textSignUp)
        forgotPassword = findViewById(R.id.forgotPassword)
        progressBar = findViewById(R.id.progressBar)

        // Password visibility toggle using drawableEnd
        passwordField.setOnTouchListener { _, event ->
            val DRAWABLE_END = 2
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = passwordField.compoundDrawables[DRAWABLE_END]
                if (drawableEnd != null && event.rawX >= (passwordField.right - drawableEnd.bounds.width() - passwordField.paddingEnd)) {
                    isPasswordVisible = !isPasswordVisible
                    if (isPasswordVisible) {
                        passwordField.inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        passwordField.setCompoundDrawablesWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.ic_eye_open,
                            0
                        )
                    } else {
                        passwordField.inputType =
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        passwordField.setCompoundDrawablesWithIntrinsicBounds(
                            0,
                            0,
                            R.drawable.ic_eye_closed,
                            0
                        )
                    }
                    passwordField.setSelection(passwordField.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }

        // Auto login if already authenticated
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // Initially show email/password fields and hide OTP related fields
        passwordLayout.visibility = View.VISIBLE
        loginButton.visibility = View.VISIBLE
        sendOtpButton.visibility = View.GONE
        verifyOtpButton.visibility = View.GONE
        otpField.visibility = View.GONE
        emailField.hint = "Email"
        emailField.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

        // Handle login option changes
        loginOptions.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.emailPasswordOption -> {
                    passwordLayout.visibility = View.VISIBLE
                    loginButton.visibility = View.VISIBLE
                    sendOtpButton.visibility = View.GONE
                    verifyOtpButton.visibility = View.GONE
                    otpField.visibility = View.GONE
                    emailField.hint = "Email"
                    emailField.inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                }
                R.id.mobileOtpOption -> {
                    passwordLayout.visibility = View.GONE
                    loginButton.visibility = View.GONE
                    sendOtpButton.visibility = View.VISIBLE
                    verifyOtpButton.visibility = View.VISIBLE
                    otpField.visibility = View.VISIBLE
                    emailField.hint = "Mobile Number"
                    emailField.inputType = InputType.TYPE_CLASS_PHONE
                }
            }
        }


        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    progressBar.visibility = View.GONE
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Login Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }


        sendOtpButton.setOnClickListener {
            val mobile = emailField.text.toString().trim()
            if (mobile.isEmpty()) {
                Toast.makeText(this, "Enter Mobile Number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            sendMobileOTP(mobile)
        }


        verifyOtpButton.setOnClickListener {
            val otp = otpField.text.toString().trim()
            if (otp.isEmpty() || verificationId == null) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progressBar.visibility = View.VISIBLE
            verifyOTP(otp)
        }


        textSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

       
        forgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }


    private fun sendEmailOTP(email: String) {
        progressBar.visibility = View.VISIBLE

        auth.fetchSignInMethodsForEmail(email).addOnCompleteListener { task ->
            progressBar.visibility = View.GONE

            if (task.isSuccessful) {
                val signInMethods = task.result?.signInMethods
                if (signInMethods.isNullOrEmpty()) {
                    Toast.makeText(this, "Email not registered!", Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }

                FirebaseAuth.getInstance().sendSignInLinkToEmail(email, getActionCodeSettings())
                    .addOnCompleteListener { emailTask ->
                        if (emailTask.isSuccessful) {
                            Toast.makeText(this, "Sign-in link sent to your email!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to send sign-in link!", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Error checking email!", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun getActionCodeSettings(): ActionCodeSettings {
        return ActionCodeSettings.newBuilder()
            .setUrl("https://yourapp.page.link/verify") // Replace with your actual dynamic link
            .setHandleCodeInApp(true)
            .setAndroidPackageName("com.pant.girly", true, null)
            .build()
    }


    private fun sendMobileOTP(mobile: String) {
        progressBar.visibility = View.VISIBLE
        val phoneNumber = "+91$mobile"

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    progressBar.visibility = View.GONE
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Verification Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    progressBar.visibility = View.GONE
                    this@LoginActivity.verificationId = verificationId
                    Toast.makeText(this@LoginActivity, "OTP sent to mobile!", Toast.LENGTH_SHORT).show()
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }


    private fun verifyOTP(otp: String) {
        progressBar.visibility = View.VISIBLE
        val credential = PhoneAuthProvider.getCredential(verificationId!!, otp)
        signInWithCredential(credential)
    }


    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Login Failed!", Toast.LENGTH_SHORT).show()
                }
            }
    }
}