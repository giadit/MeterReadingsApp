package com.example.meterreadingsapp.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.repository.MeterRepository

class S3UploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "S3UploadWorker"

    companion object {
        const val KEY_IMAGE_URI = "image_uri"
        const val KEY_S3_KEY = "s3_key"
        const val KEY_PROJECT_ID = "project_id"
        const val KEY_METER_ID = "meter_id"
    }

    override suspend fun doWork(): Result {
        val imageUriString = inputData.getString(KEY_IMAGE_URI)
        val s3Key = inputData.getString(KEY_S3_KEY)
        val projectId = inputData.getString(KEY_PROJECT_ID)
        val meterId = inputData.getString(KEY_METER_ID)

        if (imageUriString == null || s3Key == null || projectId == null || meterId == null) {
            Log.e(TAG, "Missing required data for S3UploadWorker.")
            return Result.failure()
        }

        val imageUri = Uri.parse(imageUriString)

        return try {
            val database = AppDatabase.getDatabase(applicationContext)
            val apiService = RetrofitClient.getService(ApiService::class.java, applicationContext)

            // Fetch new DAOs
            val obisCodeDao = database.obisCodeDao()
            val meterObisDao = database.meterObisDao()

            // CORRECTED: Update constructor to pass all required DAOs
            val repository = MeterRepository(
                apiService = apiService,
                meterDao = database.meterDao(),
                readingDao = database.readingDao(),
                projectDao = database.projectDao(),
                buildingDao = database.buildingDao(),
                queuedRequestDao = database.queuedRequestDao(),
                // ADDED NEW DAOs
                obisCodeDao = obisCodeDao,
                meterObisDao = meterObisDao,
                appContext = applicationContext
            )

            val uploadSuccess = repository.uploadFileToS3(imageUri, s3Key)

            if (uploadSuccess) {
                Log.d(TAG, "Successfully uploaded image: $s3Key")

                val bucketName = s3Key.substringBefore("/")
                val pathWithinBucket = s3Key.substringAfter("/")
                val fileName = pathWithinBucket.substringAfterLast("/")
                // Note: File size should be calculated from the compressed file if using compression in repo.
                // Using contentResolver for original file length for simplicity here, as that's what was done before.
                val fileSize = applicationContext.contentResolver.openAssetFileDescriptor(imageUri, "r")?.use { it.length } ?: 0L
                val fileMimeType = applicationContext.contentResolver.getType(imageUri) ?: "application/octet-stream"

                val metadataHandled = repository.postFileMetadata(
                    fileName = fileName,
                    bucketName = bucketName,
                    storagePath = pathWithinBucket,
                    fileSize = fileSize,
                    fileMimeType = fileMimeType,
                    entityId = meterId,
                    entityType = "meter",
                    documentType = "other"
                )

                if (metadataHandled) {
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
            Result.retry()
        }
    }
}
