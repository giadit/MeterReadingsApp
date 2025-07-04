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
import com.google.gson.JsonSyntaxException
import java.io.File

/**
 * Worker class responsible for uploading images to Storage (now Supabase Storage via Retrofit)
 * in the background. It receives the image URI, the full storage path (s3Key),
 * and project ID as input.
 */
class S3UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "S3UploadWorker"

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        // KEY_S3_KEY now represents the full path within Supabase Storage bucket, e.g., "project-documents/meter/meterId/fileName.jpg"
        const val KEY_S3_KEY = "s3_key"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_METER_ID = "meter_id" // Added for passing meter ID
    }

    override suspend fun doWork(): Result {
        val imageUriString = inputData.getString(KEY_IMAGE_URI)
        val s3Key = inputData.getString(KEY_S3_KEY) // This key contains the bucket name + path for Supabase
        val projectId = inputData.getString(KEY_PROJECT_ID)
        val meterId = inputData.getString(KEY_METER_ID) // Retrieve meter ID

        if (imageUriString == null || s3Key == null || projectId == null || meterId == null) {
            Log.e(TAG, "Missing required data for S3UploadWorker (image URI, S3 key, project ID, or meter ID).")
            return Result.failure()
        }

        val imageUri = Uri.parse(imageUriString)

        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val meterDao = database.meterDao()
            val readingDao = database.readingDao()
            val locationDao = database.locationDao()
            val queuedRequestDao = database.queuedRequestDao()
            val projectDao = database.projectDao()
            val apiService = RetrofitClient.getService(com.example.meterreadingsapp.api.ApiService::class.java)

            val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, projectDao, applicationContext)

            // Perform the Supabase Storage upload via MeterRepository
            val uploadSuccess = repository.uploadFileToS3(imageUri, s3Key)

            if (uploadSuccess) {
                Log.d(TAG, "Successfully processed upload request for image: $s3Key (Project: $projectId, Meter: $meterId)")

                // Extract bucket name and file name from the s3Key
                val bucketName = s3Key.substringBefore("/") // e.g., "meter-documents"
                val pathWithinBucket = s3Key.substringAfter("/") // FIX: This is the path *within* the bucket, e.g., "meter/meterId/fileName.jpg"
                val fileName = pathWithinBucket.substringAfterLast("/") // e.g., "fileName.jpg"

                // Get file size and MIME type from the local URI
                val fileSize = applicationContext.contentResolver.openAssetFileDescriptor(imageUri, "r")?.use {
                    it.length
                } ?: 0L
                val fileMimeType = applicationContext.contentResolver.getType(imageUri) ?: "application/octet-stream"


                // Post file metadata to the regular API (will be queued by repository if network is down)
                val metadataHandled = repository.postFileMetadata(
                    fileName = fileName, // Use the actual file name extracted from pathWithinBucket
                    bucketName = bucketName, // Use the extracted bucket name
                    storagePath = pathWithinBucket, // FIX: Send only the path *within* the bucket
                    fileSize = fileSize,
                    fileMimeType = fileMimeType,
                    entityId = meterId, // Use meterId as entity_id
                    entityType = "meter", // Assuming it's always 'meter' for now
                    documentType = "other" // Default to 'other' for now, as discussed
                )

                if (metadataHandled) { // If metadata was successfully sent OR queued
                    Log.d(TAG, "File metadata sent or queued successfully for $s3Key.")
                    Result.success()
                } else {
                    Log.e(TAG, "Failed to send or queue file metadata for $s3Key. Retrying.")
                    Result.retry()
                }
            } else {
                Log.e(TAG, "Failed to upload image to S3: $s3Key. Retrying...")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during S3UploadWorker execution for $s3Key: ${e.message}", e)
            Result.retry() // Retry on uncaught exceptions
        }
    }
}
