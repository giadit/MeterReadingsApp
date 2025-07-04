package com.example.meterreadingsapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.repository.MeterRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Worker class responsible for synchronizing queued requests (like meter readings and file metadata)
 * with the backend API when network connectivity is available.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started.")

        val database = AppDatabase.getDatabase(applicationContext)
        val queuedRequestDao = database.queuedRequestDao()
        val meterDao = database.meterDao()
        val readingDao = database.readingDao()
        val locationDao = database.locationDao()
        val projectDao = database.projectDao()
        val apiService = RetrofitClient.getService(com.example.meterreadingsapp.api.ApiService::class.java)

        // UPDATED: MeterRepository constructor no longer needs fileMetadataDao
        val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, projectDao, applicationContext)

        // Fetch all queued requests
        val queuedRequests = queuedRequestDao.getAllQueuedRequests().firstOrNull() ?: emptyList()

        if (queuedRequests.isEmpty()) {
            Log.d(TAG, "No queued requests to process. SyncWorker finished.")
            return Result.success()
        }

        var allSuccessful = true
        for (request in queuedRequests) {
            Log.d(TAG, "Processing queued request: ${request.id} (Type: ${request.type}, Method: ${request.method}, Endpoint: ${request.endpoint})")
            val success = repository.processQueuedRequest(request)
            if (success) {
                queuedRequestDao.delete(request.id)
                Log.d(TAG, "Successfully processed and deleted queued request: ${request.id}")
            } else {
                // If a request fails, increment attempt count and potentially retry
                request.attemptCount++
                queuedRequestDao.update(request) // Update the attempt count in DB
                Log.e(TAG, "Failed to process queued request: ${request.id}. Attempt count: ${request.attemptCount}. Will retry later.")
                allSuccessful = false
            }
        }

        return if (allSuccessful) {
            Log.d(TAG, "All queued requests processed successfully. SyncWorker finished.")
            Result.success()
        } else {
            Log.d(TAG, "Some queued requests failed. SyncWorker will retry later.")
            Result.retry()
        }
    }
}
