package com.example.meterreadingsapp.data

import androidx.room.Entity // ADDED: Import for @Entity
import androidx.room.PrimaryKey // ADDED: Import for @PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data class representing the metadata for a file to be stored in the 'files' table
 * of your regular API. This is posted after a successful upload to Supabase Storage.
 * This class is also used as a Room entity to temporarily store file metadata locally
 * before it's synced to the API.
 *
 * @param id Unique identifier for the file (UUID). Primary key for Room.
 * @param name The original file name.
 * @param bucket The name of the Supabase Storage bucket where the file is stored.
 * @param storage_path The full path within the storage bucket (e.g., "bucket/folder/file.jpg").
 * @param size The size of the file in bytes.
 * @param type The MIME type of the file (e.g., "image/jpeg", "application/pdf").
 * @param metadata A map for additional, flexible metadata (e.g., entity_id, entity_type, document_type).
 * Changed to Map<String, Any?> to accommodate various data types and match MapConverter.
 * @param created_at Timestamp when the record was created (automatically set by API).
 * @param updated_at Timestamp when the record was last updated (automatically set by API).
 */
@Entity(tableName = "file_metadata") // ADDED: Mark as a Room Entity
data class FileMetadata(
    @PrimaryKey // ADDED: Define 'id' as the primary key
    val id: String, // UUID
    val name: String,
    val bucket: String,
    @SerializedName("storage_path") val storage_path: String,
    val size: Long, // Use Long for file size
    val type: String, // MIME type
    val metadata: Map<String, Any?>, // CHANGED: To Map<String, Any?> to match MapConverter and allow flexible data
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("updated_at") val updated_at: String? = null
)
