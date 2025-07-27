package com.pant.girly

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class UserFormActivity : AppCompatActivity() {

    private lateinit var nameEditText: EditText
    private lateinit var ageEditText: EditText
    private lateinit var educationEditText: EditText
    private lateinit var cityEditText: EditText
    private lateinit var stateEditText: EditText
    private lateinit var profileImageView: ImageView
    private lateinit var uploadButton: Button
    private lateinit var submitButton: Button

    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var storageRef: StorageReference
    private var imageUri: Uri? = null
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_form)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference.child("Users")
        storageRef = FirebaseStorage.getInstance().reference.child("profile_images")

        nameEditText = findViewById(R.id.editTextName)
        ageEditText = findViewById(R.id.editTextAge)
        educationEditText = findViewById(R.id.editTextEducation)
        cityEditText = findViewById(R.id.editTextCity)
        stateEditText = findViewById(R.id.editTextState)
        profileImageView = findViewById(R.id.profileImageView)
        uploadButton = findViewById(R.id.buttonUpload)
        submitButton = findViewById(R.id.buttonSubmit)

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                imageUri = result.data?.data
                profileImageView.setImageURI(imageUri)
            }
        }

        uploadButton.setOnClickListener {
            openImageChooser()
        }

        submitButton.setOnClickListener {
            saveUserData()
        }
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun saveUserData() {
        val name = nameEditText.text.toString().trim()
        val age = ageEditText.text.toString().trim()
        val education = educationEditText.text.toString().trim()
        val city = cityEditText.text.toString().trim()
        val state = stateEditText.text.toString().trim()

        if (name.isEmpty() || age.isEmpty() || education.isEmpty() || city.isEmpty() || state.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = auth.currentUser?.uid ?: return
        val userRef = database.child(userId)

        if (imageUri != null) {
            val profileImageRef = storageRef.child("$userId.jpg")
            profileImageRef.putFile(imageUri!!)
                .addOnSuccessListener { taskSnapshot ->
                    profileImageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        val userData = mapOf(
                            "name" to name,
                            "age" to age,
                            "education" to education,
                            "city" to city,
                            "state" to state,
                            "profileImageUrl" to downloadUri.toString()
                        )
                        userRef.setValue(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
                                goToProfile()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to update data", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
                }
        } else {
            val userData = mapOf(
                "name" to name,
                "age" to age,
                "education" to education,
                "city" to city,
                "state" to state
            )
            userRef.setValue(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show()
                    goToProfile()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update data", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun goToProfile() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}