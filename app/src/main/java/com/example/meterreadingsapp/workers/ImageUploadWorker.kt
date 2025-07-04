package com.example.meterreadingsapp.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.repository.MeterRepository
import com.google.gson.Gson
import java.io.File

/**
 * Worker class responsible for uploading images to Supabase Storage in the background.
 * It receives the local image URI, the full Supabase Storage path, and project ID as input.
 */
class ImageUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "ImageUploadWorker"
    private val database = AppDatabase.getDatabase(appContext)
    private val queuedRequestDao = database.queuedRequestDao()
    private val apiService = RetrofitClient.getService(com.example.meterreadingsapp.api.ApiService::class.java)
    private val gson = Gson()

    // Initialize MeterRepository with all its dependencies
    private val meterRepository: MeterRepository = MeterRepository(
        apiService,
        database.meterDao(),
        database.readingDao(),
        database.locationDao(),
        database.queuedRequestDao(),
        database.projectDao(),
        applicationContext
    )

    companion object {
        const val KEY_FILE_URI = "file_uri"
        const val KEY_STORAGE_PATH = "storage_path" // This is the full path in Supabase Storage
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_METER_ID = "meter_id" // NEW: Key for meter ID
    }

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString(KEY_FILE_URI)
        val storagePath = inputData.getString(KEY_STORAGE_PATH)
        val projectId = inputData.getString(KEY_PROJECT_ID)
        val meterId = inputData.getString(KEY_METER_ID) // NEW: Retrieve meter ID

        if (fileUriString == null || storagePath == null || projectId == null || meterId == null) {
            Log.e(TAG, "Missing required data for ImageUploadWorker (file URI, storage path, project ID, or meter ID).")
            return Result.failure()
        }

        return try {
            Log.d(TAG, "Attempting to upload image: $fileUriString to $storagePath (Project: $projectId, Meter: $meterId)")

            // Corrected: Call uploadImageToStorage from meterRepository
            val success = meterRepository.uploadImageToStorage(fileUriString, storagePath)

            if (success) {
                Log.d(TAG, "Image uploaded successfully to S3: $storagePath. Attempting to post metadata to API.")
                // Delete the local file after successful upload
                val file = File(Uri.parse(fileUriString).path ?: "")
                val fileName = file.name
                val fileSize = file.length()
                val fileMimeType = applicationContext.contentResolver.getType(Uri.parse(fileUriString)) ?: "application/octet-stream"

                // NEW: Post file metadata to the regular API
                val metadataSuccess = meterRepository.postFileMetadata(
                    fileName = fileName,
                    bucketName = storagePath.substringBefore("/"), // Extract bucket from storagePath
                    storagePath = storagePath,
                    fileSize = fileSize,
                    fileMimeType = fileMimeType,
                    entityId = meterId, // Use meterId as entity_id
                    entityType = "meter", // Assuming it's always 'meter' for now
                    documentType = "other" // Default to 'other' for now
                )

                if (metadataSuccess) {
                    Log.d(TAG, "File metadata posted successfully for $storagePath.")
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted local image file after successful upload and metadata post: ${file.name}")
                        } else {
                            Log.w(TAG, "Failed to delete local image file: ${file.name}")
                        }
                    }
                    Result.success()
                } else {
                    Log.e(TAG, "Failed to post file metadata for $storagePath. Retrying upload and metadata post.")
                    Result.retry() // Retry if metadata post fails
                }
            } else {
                Log.e(TAG, "Failed to upload image to S3: $storagePath. Retrying...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during image upload or metadata post for $storagePath: ${e.message}", e)
            Result.retry() // Retry on uncaught exceptions
        }
    }
}
