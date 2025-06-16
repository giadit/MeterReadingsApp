package com.example.mypostsapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mypostsapp.api.ApiService
import com.example.mypostsapp.api.RetrofitClient
import com.example.mypostsapp.data.AppDatabase
import com.google.gson.Gson
import com.example.mypostsapp.data.Reading
import kotlinx.coroutines.flow.first // Import the 'first' extension function

/**
 * A Worker that attempts to synchronize queued API requests with the backend.
 * It retries failed requests with exponential backoff.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncWorker"
    private val queuedRequestDao = AppDatabase.getDatabase(appContext).queuedRequestDao()
    private val apiService = RetrofitClient.getService(ApiService::class.java)
    private val gson = Gson() // Gson instance for body serialization/deserialization

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started. Checking for queued requests...")

        // Retrieve all queued requests from the database
        // FIX: Use .first() to get the current list from the Flow
        val requests = queuedRequestDao.getAllQueuedRequests().first()

        if (requests.isNullOrEmpty()) {
            Log.d(TAG, "No queued requests to sync. Worker finished successfully.")
            return Result.success()
        }

        var allSuccessful = true

        for (request in requests) {
            try {
                Log.d(TAG, "Attempting to send queued request (ID: ${request.id}, Endpoint: ${request.endpoint}, Method: ${request.method})")

                // For simplicity, we're assuming all queued requests are POSTs to /readings
                // You would need more sophisticated logic here if you have different endpoints/methods/body types
                if (request.endpoint == "readings" && request.method == "POST") {
                    val reading = gson.fromJson(request.body, Reading::class.java)
                    val response = apiService.postReading(reading) // Use the postReading method

                    if (response.isSuccessful) {
                        Log.d(TAG, "Successfully sent queued reading (ID: ${request.id}). Deleting from queue.")
                        queuedRequestDao.delete(request.id) // Delete from queue on success
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e(TAG, "Failed to send queued reading (ID: ${request.id}): ${response.code()} - ${response.message()}. Error Body: $errorBody")
                        // Increment attempt count and update the request in the database
                        request.attemptCount++
                        queuedRequestDao.update(request)
                        allSuccessful = false // Mark as not all successful if any request fails
                    }
                } else {
                    Log.w(TAG, "Unsupported queued request type (ID: ${request.id}, Endpoint: ${request.endpoint}, Method: ${request.method}). Deleting from queue.")
                    // If the request type is unsupported, delete it to prevent infinite retries
                    queuedRequestDao.delete(request.id)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exception while sending queued request (ID: ${request.id}): ${e.message}", e)
                // Increment attempt count and update the request in the database for retry
                request.attemptCount++
                queuedRequestDao.update(request)
                allSuccessful = false
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }
}
