package com.example.mypostsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the Location entity.
 * Provides methods for interacting with the 'locations' table in the Room database.
 */
@Dao
interface LocationDao {
    /**
     * Inserts a list of locations into the database.
     * If a location with the same primary key already exists, it will be replaced.
     * @param locations The list of Location objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<Location>)

    /**
     * Retrieves all locations from the database as a Flow.
     * The Flow will emit new lists of locations whenever the data changes.
     * @return A Flow emitting a list of all Location objects.
     */
    @Query("SELECT * FROM locations ORDER BY address ASC") // Order by address for consistent display
    fun getAllLocations(): Flow<List<Location>>

    /**
     * Deletes all locations from the database.
     */
    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations()
}
