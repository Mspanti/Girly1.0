package com.pant.girly.local_db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: AlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<AlertEntity>) // Optional bulk insert

    @Query("SELECT * FROM sos_alerts WHERE synced = 0")
    suspend fun getUnsyncedAlerts(): List<AlertEntity>

    @Update
    suspend fun update(alert: AlertEntity)

    @Query("DELETE FROM sos_alerts WHERE id = :alertId")
    suspend fun deleteById(alertId: Long)

    @Query("DELETE FROM sos_alerts WHERE synced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT * FROM sos_alerts WHERE user_id = :userId ORDER BY timestamp DESC")
    suspend fun getAlertsByUser(userId: String): List<AlertEntity>

}
