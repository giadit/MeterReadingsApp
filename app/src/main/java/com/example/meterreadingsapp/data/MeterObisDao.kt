package com.example.meterreadingsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the MeterObis entity.
 * This table links Meters to their specific OBIS code data points.
 */
@Dao
interface MeterObisDao {
    /**
     * Inserts a list of MeterObis entries into the database.
     * On conflict, existing entries will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meterObisList: List<MeterObis>)

    /**
     * Retrieves all MeterObis entries for a specific meter ID.
     */
    @Query("SELECT * FROM meter_obis WHERE meter_id = :meterId ORDER BY created_at ASC")
    fun getMeterObisByMeterId(meterId: String): Flow<List<MeterObis>>

    /**
     * Deletes all MeterObis entries from the database.
     */
    @Query("DELETE FROM meter_obis")
    suspend fun deleteAllMeterObis()
}