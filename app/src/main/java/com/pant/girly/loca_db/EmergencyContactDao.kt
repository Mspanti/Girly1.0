
package com.pant.girly.local_db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface EmergencyContactDao {

    @Query("SELECT * FROM emergency_contacts")
    suspend fun getAllContacts(): List<EmergencyContactEntity>

    @Query("SELECT phoneNumber FROM emergency_contacts")
    suspend fun getAllPhoneNumbers(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: EmergencyContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<EmergencyContactEntity>)

    @Update
    suspend fun update(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByPhoneNumber(phoneNumber: String)

    @Query("DELETE FROM emergency_contacts")
    suspend fun deleteAllContacts()
}