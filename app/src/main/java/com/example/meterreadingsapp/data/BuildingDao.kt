package com.example.meterreadingsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the Building entity.
 * Provides methods for interacting with the 'buildings' table in the Room database.
 */
@Dao
interface BuildingDao {
    /**
     * Inserts a list of buildings into the database.
     * If a building with the same primary key already exists, it will be replaced.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(buildings: List<Building>)

    /**
     * Retrieves all buildings associated with a specific project ID from the database.
     */
    @Query("SELECT * FROM buildings WHERE project_id = :projectId ORDER BY name ASC")
    fun getBuildingsByProjectId(projectId: String): Flow<List<Building>>

    /**
     * ADDED: Deletes all buildings associated with a specific project ID.
     * This ensures that buildings removed from the API are also removed locally during a refresh.
     */
    @Query("DELETE FROM buildings WHERE project_id = :projectId")
    suspend fun deleteBuildingsByProjectId(projectId: String)

    /**
     * ADDED: Deletes all buildings from the database.
     * This is used during a full data refresh to clear out old data.
     */
    @Query("DELETE FROM buildings")
    suspend fun deleteAllBuildings()
}