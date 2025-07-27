package com.pant.girly

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.gson.Gson


class EmergencyContactsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EmergencyContactsAdapter
    private val contactList = mutableListOf<EmergencyContact>()
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText

    private val gson = Gson()

    @SuppressLint("Range")
    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.PickContact()) { uri: Uri? ->
        uri?.let {
            val cursor = contentResolver.query(it, null, null, null, null)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME))
                    val id = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID))

                    val hasPhone = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                    var phone: String? = null

                    if (hasPhone > 0) {
                        val phonesCursor = contentResolver.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                            arrayOf(id),
                            null
                        )
                        phonesCursor?.use {
                            if (it.moveToFirst()) {
                                phone = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            }
                        }
                    }

                    etName.setText(name)
                    etPhone.setText(phone)
                }
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_contacts)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        recyclerView = findViewById(R.id.rvEmergencyContacts)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EmergencyContactsAdapter(contactList,
            onDelete = { contact -> deleteContact(contact) },
            onEdit = { contact -> editContact(contact) }
        )
        recyclerView.adapter = adapter

        etName = findViewById(R.id.etContactName)
        etPhone = findViewById(R.id.etContactPhone)
        etEmail = findViewById(R.id.etContactEmail)

        findViewById<Button>(R.id.btnAddContact).setOnClickListener { addContact() }
        findViewById<Button>(R.id.btnPickContacts).setOnClickListener { pickContactFromPhone() }

        loadContacts()
    }

    private fun loadContacts() {
        val user = auth.currentUser ?: return

        database.child("emergency_contacts").child(user.uid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    contactList.clear()
                    val numberList = mutableListOf<String>()
                    for (contactSnapshot in snapshot.children) {
                        val contact = contactSnapshot.getValue(EmergencyContact::class.java)
                        contact?.let {
                            it.id = contactSnapshot.key ?: ""
                            contactList.add(it)
                            numberList.add(it.phoneNumber)
                        }
                    }
                    adapter.notifyDataSetChanged()
                    saveContactsToPrefs(numberList)
                }

                override fun onCancelled(error: DatabaseError) {
                    showToast("Failed to load contacts: ${error.message}")
                }
            })
    }

    private fun saveContactsToPrefs(numbers: List<String>) {
        val json = gson.toJson(numbers)
        getSharedPreferences("emergency_prefs", MODE_PRIVATE).edit()
            .putString("contacts", json)
            .apply()
    }

    private fun addContact() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()

        if (name.isEmpty() || phone.isEmpty()) {
            showToast("Please enter name and phone number")
            return
        }

        if (!isValidPhoneNumber(phone)) {
            showToast("Please enter a valid phone number")
            return
        }

        val user = auth.currentUser ?: run {
            showToast("User not logged in")
            return
        }

        val contactRef = database.child("emergency_contacts").child(user.uid).push()
        val contact = EmergencyContact(
            id = contactRef.key ?: "",
            name = name,
            phoneNumber = phone,
            email = email
        )

        contactRef.setValue(contact)
            .addOnSuccessListener {
                clearInputFields()
                showToast("Contact added successfully")
            }
            .addOnFailureListener {
                showToast("Failed to add contact")
            }
    }

    private fun pickContactFromPhone() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 1)
        } else {
            contactPickerLauncher.launch(null)
        }
    }

    private fun editContact(contact: EmergencyContact) {
        showToast("Edit feature coming soon")
    }

    private fun deleteContact(contact: EmergencyContact) {
        val user = auth.currentUser ?: return

        database.child("emergency_contacts")
            .child(user.uid)
            .child(contact.id)
            .removeValue()
            .addOnSuccessListener {
                showToast("Contact deleted")
            }
            .addOnFailureListener {
                showToast("Failed to delete contact")
            }
    }

    private fun clearInputFields() {
        etName.text.clear()
        etPhone.text.clear()
        etEmail.text.clear()
    }

    private fun isValidPhoneNumber(phone: String): Boolean {
        return phone.matches(Regex("^[+]?[0-9]{10,13}$"))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
