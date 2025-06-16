package com.example.mypostsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the Reading entity.
 * Provides methods for interacting with the 'readings' table in the Room database.
 * Note: For simplicity, this DAO focuses on basic CRUD. More complex queries might be needed.
 */
@Dao
interface ReadingDao {
    /**
     * Inserts a single reading into the database.
     * If a reading with the same primary key already exists, it will be replaced.
     * @param reading The Reading object to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: Reading)

    /**
     * Retrieves all readings from the database as a Flow.
     * @return A Flow emitting a list of all Reading objects.
     */
    @Query("SELECT * FROM readings")
    fun getAllReadings(): Flow<List<Reading>>

    /**
     * Retrieves readings for a specific meter ID from the database as a Flow.
     * @param meterId The ID of the meter to filter readings by.
     * @return A Flow emitting a list of Reading objects for the given meter ID.
     */
    @Query("SELECT * FROM readings WHERE meter_id = :meterId ORDER BY date DESC")
    fun getReadingsByMeterId(meterId: String): Flow<List<Reading>>

    /**
     * Deletes all readings from the database.
     */
    @Query("DELETE FROM readings")
    suspend fun deleteAllReadings()

    /**
     * Deletes readings associated with a specific meter ID from the database.
     * @param meterId The ID of the meter whose readings should be deleted.
     */
    @Query("DELETE FROM readings WHERE meter_id = :meterId")
    suspend fun deleteReadingsByMeterId(meterId: String)
}
