package com.example.meterreadingsapp.workers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.meterreadingsapp.MainActivity
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.api.SessionManager
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.repository.MeterRepository
import com.example.meterreadingsapp.repository.QueueProcessResult
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

        val obisCodeDao = database.obisCodeDao()
        val meterObisDao = database.meterObisDao()

        val repository = MeterRepository(
            apiService = apiService,
            meterDao = database.meterDao(),
            readingDao = database.readingDao(),
            projectDao = database.projectDao(),
            buildingDao = database.buildingDao(),
            queuedRequestDao = database.queuedRequestDao(),
            obisCodeDao = obisCodeDao,
            meterObisDao = meterObisDao,
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

            // UPDATED: Handle different result types from repository
            when (repository.processQueuedRequest(request)) {
                is QueueProcessResult.Success -> {
                    // If successful, delete from queue
                    database.queuedRequestDao().delete(request.id)
                    Log.d(TAG, "Successfully processed and deleted queued request: ${request.id}")
                }
                is QueueProcessResult.AuthFailure -> {
                    // If Auth failed (401), clear session and notify UI
                    Log.e(TAG, "Authentication failed for request: ${request.id}. Clearing session and notifying user.")

                    val sessionManager = SessionManager(applicationContext)
                    sessionManager.clearAuthToken()

                    // Broadcast to MainActivity to show popup
                    val intent = Intent(MainActivity.ACTION_AUTH_ERROR)
                    // Set package to ensure broadcast stays within app
                    intent.setPackage(applicationContext.packageName)
                    applicationContext.sendBroadcast(intent)

                    // Fail the worker immediately, do not retry
                    return Result.failure()
                }
                is QueueProcessResult.Retry -> {
                    // If network error or other server error, mark for retry
                    request.attemptCount++
                    database.queuedRequestDao().update(request)
                    Log.e(TAG, "Failed to process queued request: ${request.id}. Attempt count: ${request.attemptCount}.")
                    allSuccessful = false
                }
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