package com.example.meterreadingsapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the FileMetadata entity.
 * Provides methods for interacting with the 'file_metadata' table in the Room database.
 */
@Dao
interface FileMetadataDao {
    /**
     * Inserts a single file metadata entry into the database.
     * If a file metadata entry with the same primary key already exists, it will be replaced.
     * @param fileMetadata The FileMetadata object to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fileMetadata: FileMetadata)

    /**
     * Inserts a list of file metadata entries into the database.
     * If an entry with the same primary key already exists, it will be replaced.
     * @param fileMetadataList The list of FileMetadata objects to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fileMetadataList: List<FileMetadata>)

    /**
     * Retrieves all file metadata entries from the database.
     * @return A Flow emitting a list of all FileMetadata objects.
     */
    @Query("SELECT * FROM file_metadata ORDER BY created_at DESC")
    fun getAllFileMetadata(): Flow<List<FileMetadata>>

    /**
     * Retrieves file metadata entries associated with a specific entity (e.g., a meter or project).
     * This queries the 'metadata' JSON column for 'entity_id'.
     *
     * Note: Querying JSON fields directly in Room can be less performant than dedicated columns.
     * For large datasets, consider normalizing 'entity_id' and 'entity_type' into direct columns
     * in the FileMetadata entity if frequent filtering by these is needed.
     *
     * @param entityId The ID of the associated entity (e.g., meter ID, project ID).
     * @return A Flow emitting a list of FileMetadata objects matching the entity ID.
     */
    @Query("SELECT * FROM file_metadata WHERE json_extract(metadata, '$.entity_id') = :entityId ORDER BY created_at DESC")
    fun getFileMetadataByEntityId(entityId: String): Flow<List<FileMetadata>>

    /**
     * Retrieves a single file metadata entry by its unique ID.
     * @param id The unique ID of the file metadata entry.
     * @return A Flow emitting a single FileMetadata object or null if not found.
     */
    @Query("SELECT * FROM file_metadata WHERE id = :id LIMIT 1")
    fun getFileMetadataById(id: String): Flow<FileMetadata?>

    /**
     * Deletes a single file metadata entry from the database by its ID.
     * @param id The ID of the file metadata entry to delete.
     * @return The number of rows deleted.
     */
    @Query("DELETE FROM file_metadata WHERE id = :id")
    suspend fun delete(id: String): Int

    /**
     * Deletes all file metadata entries from the database.
     */
    @Query("DELETE FROM file_metadata")
    suspend fun deleteAllFileMetadata()
}
