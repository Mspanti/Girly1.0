package com.pant.girly

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.pant.girly.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private lateinit var user: FirebaseUser

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase services
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()
        user = auth.currentUser ?: run {
            redirectToLogin()
            return
        }

        setupToolbar()
        setupProfileOptions()
        loadUserData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = ""
        }
        binding.toolbarTitle.text = "My Profile"
    }

    private fun setupProfileOptions() {
        val options = listOf(
            ProfileOption("Emergency SOS", "Configure emergency contacts", R.drawable.ic_sos, ProfileOptionType.SOS_ALERTS),
            ProfileOption("App Settings", "Notifications, Location", R.drawable.ic_settings, ProfileOptionType.APP_SETTINGS),
            ProfileOption("Personal Info", "View your details", R.drawable.ic_person, ProfileOptionType.USER_INFO),
            ProfileOption("Emergency Contacts", "Manage contacts", R.drawable.ic_contacts, ProfileOptionType.EMERGENCY_CONTACTS),
            ProfileOption("Registered Address", "Safety location", R.drawable.ic_home, ProfileOptionType.REGISTERED_ADDRESS),
            ProfileOption("Logout", "Sign out", R.drawable.ic_logout, ProfileOptionType.LOGOUT)
        )

        binding.rvProfileOptions.apply {
            layoutManager = LinearLayoutManager(this@ProfileActivity)
            adapter = ProfileOptionsAdapter(options) { handleOptionClick(it) }
            setHasFixedSize(true)
        }
    }

    private fun handleOptionClick(option: ProfileOption) {
        when (option.type) {
            ProfileOptionType.SOS_ALERTS -> {
                startActivity(Intent(this, SOSAlertsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            ProfileOptionType.APP_SETTINGS -> {
                startActivity(Intent(this, AppSettingsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            ProfileOptionType.USER_INFO -> {
                startActivity(Intent(this, UserInfoActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            ProfileOptionType.EMERGENCY_CONTACTS -> {
                startActivity(Intent(this, EmergencyContactsActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            ProfileOptionType.REGISTERED_ADDRESS -> {
                startActivity(Intent(this, RegisteredAddressActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
            ProfileOptionType.LOGOUT -> {
                showLogoutConfirmationDialog()
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> logoutUser() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun loadUserData() {
        // Set email from Firebase Auth
        binding.tvUserEmail.text = user.email ?: "No email"

        // Load additional profile data
        database.reference.child("Users").child(user.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        redirectToUserForm()
                        return
                    }

                    // Set user name
                    binding.tvUserName.text = snapshot.child("name").value?.toString() ?: "User"

                    // Load profile image
                    loadProfileImage(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@ProfileActivity, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadProfileImage(snapshot: DataSnapshot) {
        val imageUrl = snapshot.child("profileImageUrl").value?.toString()

        if (!imageUrl.isNullOrEmpty()) {
            loadImageFromUrl(imageUrl)
        } else {
            loadImageFromStorage()
        }
    }

    private fun loadImageFromUrl(url: String) {
        Glide.with(this)
            .load(url)
            .addListener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    loadImageFromStorage()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }
            })
            .placeholder(R.drawable.default_profile)
            .error(R.drawable.error_profile)
            .circleCrop()
            .into(binding.ivProfileImage)
    }

    private fun loadImageFromStorage() {
        storage.reference.child("profile_images/${user.uid}.jpg")
            .downloadUrl
            .addOnSuccessListener { uri ->
                // Save URL to database for future use
                database.reference.child("Users").child(user.uid)
                    .child("profileImageUrl").setValue(uri.toString())

                // Load the image
                Glide.with(this)
                    .load(uri)
                    .into(binding.ivProfileImage)
            }
            .addOnFailureListener { e ->
                Log.w("Profile", "Failed to load profile image", e)
                binding.ivProfileImage.setImageResource(R.drawable.default_profile)
            }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun redirectToUserForm() {
        startActivity(Intent(this, UserFormActivity::class.java))
        finish()
    }

    private fun logoutUser() {
        auth.signOut()
        startActivity(Intent(this, LoginActivity::class.java))
        finishAffinity()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_edit -> {
                startActivity(Intent(this, EditProfileActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserData() // Refresh when returning from other activities
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}