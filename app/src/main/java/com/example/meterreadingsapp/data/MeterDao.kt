package com.example.meterreadingsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the Meter entity.
 * Defines methods for interacting with the 'meters' table in the Room database.
 */
@Dao
interface MeterDao {
    /**
     * Inserts a list of meters into the database.
     * On conflict, existing meters with the same primary key will be replaced.
     * @param meters The list of Meter objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(meters: List<Meter>)

    /**
     * Retrieves all meters from the database.
     * @return A Flow emitting a list of all Meter objects from the 'meters' table.
     */
    @Query("SELECT * FROM meters")
    fun getAllMeters(): Flow<List<Meter>>

    /**
     * ADDED: Retrieves meters for a specific building ID.
     * This is the key function for our new navigation hierarchy.
     * @param buildingId The ID of the building to filter meters by.
     * @return A Flow emitting a list of Meter objects for the given building ID.
     */
    @Query("SELECT * FROM meters WHERE building_id = :buildingId")
    fun getMetersByBuildingId(buildingId: String): Flow<List<Meter>>

    /**
     * Deletes all meters from the database.
     */
    @Query("DELETE FROM meters")
    suspend fun deleteAllMeters()

    // The old getMetersByAddress and deleteMetersByAddress functions are now obsolete
    // for the main navigation flow, but we can leave them for now to avoid breaking
    // any other potential usages. They can be cleaned up in a later refactor.
}

