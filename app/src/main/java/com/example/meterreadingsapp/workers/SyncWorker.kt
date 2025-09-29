package com.example.meterreadingsapp.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.repository.MeterRepository
import kotlinx.coroutines.flow.firstOrNull

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SyncWorker"

    override suspend fun doWork(): Result {
        Log.d(TAG, "SyncWorker started.")

        val database = AppDatabase.getDatabase(applicationContext)
        val apiService = RetrofitClient.getService(ApiService::class.java, applicationContext)

        // CORRECTED: The constructor arguments are now in the correct order
        val repository = MeterRepository(
            apiService = apiService,
            meterDao = database.meterDao(),
            readingDao = database.readingDao(),
            projectDao = database.projectDao(),
            buildingDao = database.buildingDao(),
            queuedRequestDao = database.queuedRequestDao(),
            locationDao = database.locationDao(),
            appContext = applicationContext
        )

        val queuedRequests = database.queuedRequestDao().getAllQueuedRequests().firstOrNull() ?: emptyList()

        if (queuedRequests.isEmpty()) {
            Log.d(TAG, "No queued requests to process. SyncWorker finished.")
            return Result.success()
        }

        var allSuccessful = true
        for (request in queuedRequests) {
            Log.d(TAG, "Processing queued request: ${request.id}")
            val success = repository.processQueuedRequest(request)
            if (success) {
                database.queuedRequestDao().delete(request.id)
                Log.d(TAG, "Successfully processed and deleted queued request: ${request.id}")
            } else {
                request.attemptCount++
                database.queuedRequestDao().update(request)
                Log.e(TAG, "Failed to process queued request: ${request.id}. Attempt count: ${request.attemptCount}.")
                allSuccessful = false
            }
        }

        return if (allSuccessful) {
            Log.d(TAG, "All queued requests processed successfully.")
            Result.success()
        } else {
            Log.d(TAG, "Some queued requests failed. Will retry later.")
            Result.retry()
        }
    }
}

