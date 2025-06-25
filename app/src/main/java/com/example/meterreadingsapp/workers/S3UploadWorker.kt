package com.example.meterreadingsapp.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.api.RetrofitClient // Not directly used by this worker for S3, but often present
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.repository.MeterRepository // Import MeterRepository for upload logic

/**
 * Worker class responsible for uploading images to AWS S3 in the background.
 * It receives the image URI, S3 key, and project ID as input.
 */
class S3UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "S3UploadWorker"

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_S3_KEY = "s3_key"
        const val KEY_PROJECT_ID = "project_id" // Currently not directly used by worker, but good to pass for context/future
    }

    override suspend fun doWork(): Result {
        val imageUriString = inputData.getString(KEY_IMAGE_URI)
        val s3Key = inputData.getString(KEY_S3_KEY)
        val projectId = inputData.getString(KEY_PROJECT_ID) // Retrieve projectId

        if (imageUriString == null || s3Key == null) {
            Log.e(TAG, "Missing image URI or S3 key for S3UploadWorker.")
            return Result.failure()
        }

        val imageUri = Uri.parse(imageUriString)

        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val meterDao = database.meterDao()
            val readingDao = database.readingDao()
            val locationDao = database.locationDao()
            val queuedRequestDao = database.queuedRequestDao() // Pass QueuedRequestDao
            val apiService = RetrofitClient.getService(com.example.meterreadingsapp.api.ApiService::class.java) // Main API service

            // Initialize MeterRepository with all its dependencies
            val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, applicationContext)

            // Perform the S3 upload
            repository.uploadFileToS3(imageUri, s3Key)

            Log.d(TAG, "Successfully uploaded $s3Key to S3 for project: $projectId")
            // Optional: If you want to delete the local file after successful upload
            // applicationContext.contentResolver.delete(imageUri, null, null)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading $s3Key to S3: ${e.message}", e)
            Result.retry() // Retry if there's a network or transient error
        }
    }
}
