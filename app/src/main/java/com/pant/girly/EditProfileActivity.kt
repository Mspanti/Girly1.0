package com.pant.girly

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

class EditProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private var imageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            imageUri = it
            findViewById<ImageView>(R.id.ivProfile).setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()

        findViewById<ImageView>(R.id.ivProfile).setOnClickListener {
            pickImage.launch("image/*")
        }

        findViewById<Button>(R.id.btnSave).setOnClickListener { updateProfile() }

        loadCurrentData()
    }

    private fun loadCurrentData() {
        val user = auth.currentUser ?: return

        database.reference.child("users").child(user.uid)
            .get().addOnSuccessListener { snapshot ->
                val userData = snapshot.getValue(UserData::class.java)
                userData?.let {
                    findViewById<EditText>(R.id.etName).setText(it.name)
                    findViewById<EditText>(R.id.etPhone).setText(it.phone)
                    findViewById<EditText>(R.id.etAddress).setText(it.address)
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfile() {
        val user = auth.currentUser ?: return

        val userData = UserData(
            name = findViewById<EditText>(R.id.etName).text.toString(),
            phone = findViewById<EditText>(R.id.etPhone).text.toString(),
            address = findViewById<EditText>(R.id.etAddress).text.toString()
        )

        database.reference.child("users").child(user.uid)
            .setValue(userData)
            .addOnSuccessListener {
                imageUri?.let { uri ->
                    uploadProfileImage(uri)
                } ?: run {
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadProfileImage(uri: Uri) {
        val user = auth.currentUser ?: return
        val storageRef = storage.reference
        val imageRef = storageRef.child("profile_images/${user.uid}.jpg")

        imageRef.putFile(uri)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated with image", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
            }
    }
}