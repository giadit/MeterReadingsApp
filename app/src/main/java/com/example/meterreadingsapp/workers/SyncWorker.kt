package com.example.meterreadingsapp.workers

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.QueuedRequest
import com.example.meterreadingsapp.repository.MeterRepository
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * A Worker that attempts to synchronize queued API requests (readings and image uploads) with the backend.
 * It retries failed requests with exponential backoff.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncWorker"
    private val database = AppDatabase.getDatabase(appContext)
    private val queuedRequestDao = database.queuedRequestDao()
    private val apiService = RetrofitClient.getService(ApiService::class.java)
    private val gson = Gson()

    // Initialize MeterRepository with all its dependencies, including the new ProjectDao
    private val meterRepository: MeterRepository = MeterRepository(
        apiService,
        database.meterDao(),
        database.readingDao(),
        database.locationDao(),
        database.queuedRequestDao(),
        database.projectDao(), // FIX: Added ProjectDao dependency
        applicationContext
    )

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started. Checking for queued requests...")

        val requests = queuedRequestDao.getAllQueuedRequests().first()

        if (requests.isNullOrEmpty()) {
            Log.d(TAG, "No queued requests to sync. Worker finished successfully.")
            return Result.success()
        }

        var allSuccessful = true

        for (request in requests) {
            try {
                Log.d(TAG, "Attempting to send queued request (ID: ${request.id}, Type: ${request.type}, Endpoint: ${request.endpoint}, Method: ${request.method})")

                when (request.type) {
                    "reading" -> {
                        val reading = gson.fromJson(request.body, Reading::class.java)
                        val response = apiService.postReading(reading)

                        if (response.isSuccessful) {
                            Log.d(TAG, "Successfully sent queued reading (ID: ${request.id}). Deleting from queue.")
                            queuedRequestDao.delete(request.id)
                        } else {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "Failed to send queued reading (ID: ${request.id}): ${response.code()} - ${response.message()}. Error Body: $errorBody")
                            request.attemptCount++
                            queuedRequestDao.update(request)
                            allSuccessful = false
                        }
                    }
                    "image_upload" -> {
                        try {
                            val imageUploadData = gson.fromJson(request.body, ImageUploadData::class.java)
                            val imageUri = Uri.parse(imageUploadData.imageUriString)
                            val s3Key = imageUploadData.s3Key

                            meterRepository.uploadFileToS3(imageUri, s3Key)

                            Log.d(TAG, "Successfully uploaded queued image (ID: ${request.id}, S3 Key: $s3Key). Deleting from queue and local file.")
                            queuedRequestDao.delete(request.id)

                            val file = File(imageUri.path ?: "")
                            if (file.exists()) {
                                if (file.delete()) {
                                    Log.d(TAG, "Deleted local image file: ${file.name}")
                                } else {
                                    Log.w(TAG, "Failed to delete local image file: ${file.name}")
                                }
                            }

                        } catch (e: JsonSyntaxException) {
                            Log.e(TAG, "Error parsing image upload data JSON (ID: ${request.id}): ${e.message}. Deleting from queue.", e)
                            queuedRequestDao.delete(request.id)
                            allSuccessful = false
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to upload queued image (ID: ${request.id}): ${e.message}", e)
                            request.attemptCount++
                            queuedRequestDao.update(request)
                            allSuccessful = false
                        }
                    }
                    else -> {
                        Log.w(TAG, "Unsupported queued request type (ID: ${request.id}, Type: ${request.type}). Deleting from queue.")
                        queuedRequestDao.delete(request.id)
                        allSuccessful = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Uncaught exception while processing queued request (ID: ${request.id}): ${e.message}", e)
                request.attemptCount++
                queuedRequestDao.update(request)
                allSuccessful = false
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private data class ImageUploadData(
        val imageUriString: String,
        val s3Key: String,
        val projectId: String
    )
}
