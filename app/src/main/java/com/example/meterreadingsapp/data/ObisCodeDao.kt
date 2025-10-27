package com.example.meterreadingsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the ObisCode entity.
 * Provides methods for interacting with the 'obis_codes' table.
 */
@Dao
interface ObisCodeDao {
    /**
     * Inserts a list of ObisCode entries into the database.
     * On conflict, existing entries will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(obisCodeList: List<ObisCode>)

    /**
     * Retrieves all ObisCode entries from the database.
     */
    @Query("SELECT * FROM obis_codes ORDER BY code ASC")
    fun getAllObisCodes(): Flow<List<ObisCode>>

    /**
     * Deletes all ObisCode entries from the database.
     */
    @Query("DELETE FROM obis_codes")
    suspend fun deleteAllObisCodes()
}
