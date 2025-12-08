package com.example.meterreadingsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the Project entity.
 * Provides methods for interacting with the 'projects' table in the Room database.
 */
@Dao
interface ProjectDao {
    /**
     * Inserts a list of projects into the database.
     * If a project with the same primary key already exists, it will be replaced.
     * @param projects The list of Project objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<Project>)

    /**
     * Retrieves all projects from the database as a Flow.
     * The Flow will emit new lists of projects whenever the data changes.
     * @return A Flow emitting a list of all Project objects, ordered by name.
     */
    @Query("SELECT * FROM projects ORDER BY name ASC")
    fun getAllProjects(): Flow<List<Project>>

    /**
     * Retrieves a single project by its unique ID from the database.
     * @param id The unique ID of the project to retrieve.
     * @return A Flow emitting a single Project object or null if not found.
     */
    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProjectById(id: String): Flow<Project?>

    /**
     * Deletes all projects from the database.
     * This is used during a refresh operation to ensure deleted projects are removed locally.
     */
    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()
}