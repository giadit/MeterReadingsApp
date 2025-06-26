package com.example.meterreadingsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update // Import for update operations
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the QueuedRequest entity.
 * Provides methods for interacting with the 'queued_requests' table in the Room database.
 */
@Dao
interface QueuedRequestDao {
    /**
     * Inserts a single queued request into the database.
     * On conflict, it will replace the existing request (though with auto-generate=true for ID,
     * this mostly handles unique constraints if any other were defined).
     * @param request The QueuedRequest object to insert.
     * @return The row ID of the newly inserted request.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(request: QueuedRequest): Long

    /**
     * Updates an existing queued request in the database.
     * This will be used to increment attempt count.
     * @param request The QueuedRequest object to update.
     * @return The number of rows updated.
     */
    @Update
    suspend fun update(request: QueuedRequest): Int

    /**
     * Retrieves all queued requests from the database, ordered by timestamp.
     * This Flow will emit new lists of requests whenever the data changes.
     * @return A Flow emitting a list of all QueuedRequest objects.
     */
    @Query("SELECT * FROM queued_requests ORDER BY timestamp ASC")
    fun getAllQueuedRequests(): Flow<List<QueuedRequest>>

    /**
     * Retrieves a single queued request by its ID.
     * @param requestId The ID of the request to retrieve.
     * @return The QueuedRequest object, or null if not found.
     */
    @Query("SELECT * FROM queued_requests WHERE id = :requestId LIMIT 1")
    suspend fun getQueuedRequestById(requestId: Long): QueuedRequest?

    /**
     * Deletes a single queued request from the database by its ID.
     * @param requestId The ID of the request to delete.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM queued_requests WHERE id = :requestId")
    suspend fun delete(requestId: Long): Int

    /**
     * Deletes all queued requests from the database.
     */
    @Query("DELETE FROM queued_requests")
    suspend fun deleteAllQueuedRequests(): Int
}
