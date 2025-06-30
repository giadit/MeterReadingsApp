package com.example.meterreadingsapp.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.repository.MeterRepository

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
        // KEY_S3_KEY now represents the full path within Supabase Storage bucket, e.g., "project-documents/projects/projectId/image.jpg"
        const val KEY_S3_KEY = "s3_key"
        const val KEY_PROJECT_ID = "project_id"
    }

    override suspend fun doWork(): Result {
        val imageUriString = inputData.getString(KEY_IMAGE_URI)
        val s3Key = inputData.getString(KEY_S3_KEY) // This key contains the bucket name + path for Supabase
        val projectId = inputData.getString(KEY_PROJECT_ID)

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
            val queuedRequestDao = database.queuedRequestDao()
            val projectDao = database.projectDao() // FIX: Get ProjectDao instance
            val apiService = RetrofitClient.getService(com.example.meterreadingsapp.api.ApiService::class.java)

            // Initialize MeterRepository with all its dependencies, including projectDao
            val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, projectDao, applicationContext) // FIX: Pass projectDao

            // Perform the Supabase Storage upload via MeterRepository
            repository.uploadFileToS3(imageUri, s3Key)

            Log.d(TAG, "Successfully processed upload request for image: $s3Key (Project: $projectId)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading $s3Key via S3UploadWorker: ${e.message}", e)
            Result.retry() // Retry if there's a network or transient error
        }
    }
}
