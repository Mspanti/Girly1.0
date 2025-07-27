package com.pant.girly.local_db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // Unique ID for each contact
    val phoneNumber: String, // The actual phone number
    val name: String? = null // Optional: store contact name if you wish
)