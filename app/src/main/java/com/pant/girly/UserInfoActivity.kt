package com.pant.girly

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class UserInfoActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        findViewById<Button>(R.id.btnSave).setOnClickListener { saveUserData() }

        loadUserData()
    }

    private fun loadUserData() {
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
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserData() {
        val user = auth.currentUser ?: return

        val userData = UserData(
            name = findViewById<EditText>(R.id.etName).text.toString(),
            phone = findViewById<EditText>(R.id.etPhone).text.toString(),
            address = findViewById<EditText>(R.id.etAddress).text.toString()
        )

        database.reference.child("users").child(user.uid)
            .setValue(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }
}