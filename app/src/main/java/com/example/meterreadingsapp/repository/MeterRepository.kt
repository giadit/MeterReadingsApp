package com.example.meterreadingsapp.repository

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.data.Location // Ensure Location is imported!
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.data.LocationDao // Ensure LocationDao is imported!
import com.example.meterreadingsapp.data.QueuedRequest // Import QueuedRequest
import com.example.meterreadingsapp.data.QueuedRequestDao // Import QueuedRequestDao
import com.example.meterreadingsapp.workers.SyncWorker // Import SyncWorker
import com.google.gson.Gson // For serializing Reading objects to JSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import okhttp3.ResponseBody // Still needed for error response fallback if postReading fails
import java.io.IOException // For network error handling
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale // Required for SimpleDateFormat

/**
 * Repository class that abstracts the data sources (API and local database) for meters and readings.
 * It provides a clean API for the ViewModel to interact with data.
 *
 * @param apiService The Retrofit service for making API calls.
 * @param meterDao The Room DAO for accessing meter data in the local database.
 * @param readingDao The Room DAO for accessing reading data in the local database.
 * @param locationDao The Room DAO for accessing location data in the local database.
 * @param queuedRequestDao The Room DAO for accessing queued requests in the local database.
 * @param appContext The application context, needed for WorkManager.
 */
class MeterRepository(
    private val apiService: ApiService,
    private val meterDao: MeterDao,
    private val readingDao: ReadingDao,
    private val locationDao: LocationDao, // Added LocationDao to constructor
    private val queuedRequestDao: QueuedRequestDao, // FIX: Added QueuedRequestDao
    private val appContext: Context // FIX: Added Application Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson() // Gson instance for serialization
    private val workManager = WorkManager.getInstance(appContext) // WorkManager instance

    /**
     * Private helper function to generate a unique ID for a Location.
     * This logic is now within the repository where it's used.
     */
    private fun generateLocationId(address: String, postalCode: String?, city: String?): String {
        val safePostalCode = postalCode ?: ""
        val safeCity = city ?: ""
        return "$address|$safePostalCode|$safeCity"
    }

    /**
     * Provides a Flow of all meters from the local database.
     * This Flow will emit new lists of meters whenever the database changes.
     * @return A Flow emitting a list of Meter objects.
     */
    fun getAllMetersFromDb(): Flow<List<Meter>> {
        return meterDao.getAllMeters()
    }

    /**
     * Retrieves a Flow of unique Location objects (addresses) from the locally stored locations.
     * This function now directly fetches from the local LocationDao.
     * @return A Flow emitting a list of unique Location objects.
     */
    fun getUniqueLocations(): Flow<List<Location>> {
        return locationDao.getAllLocations() // FIX: Directly get locations from local DB
    }

    /**
     * Retrieves a Flow of meters associated with a specific location (address, postal code, city).
     * @param address The street address to filter by.
     * @param postalCode The postal code to filter by (now nullable String?).
     * @param city The city to filter by (now nullable String?).
     * @return A Flow emitting a list of Meter objects matching the given address details.
     */
    fun getMetersForLocation(address: String, postalCode: String?, city: String?): Flow<List<Meter>> { // FIX: Changed signature to accept nullable String?
        // Handle potential nullability for database query by converting null to empty string
        val safePostalCode = postalCode ?: ""
        val safeCity = city ?: ""
        return meterDao.getMetersByAddress(address, safePostalCode, safeCity)
    }

    /**
     * Refreshes all meters from the API and updates the local database.
     * This method clears existing meters and then inserts the newly fetched ones.
     * It runs on the IO dispatcher to prevent blocking the main thread.
     */
    suspend fun refreshAllMeters() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to fetch all meters from API...")
                val response = apiService.getAllMeters()
                if (response.isSuccessful && response.body() != null) {
                    val meters = response.body()
                    Log.d(TAG, "Successfully fetched ${meters?.size} meters from API.")

                    // First, process and save locations derived from meters
                    meters?.let { meterList ->
                        val locations = meterList.map { meter ->
                            // Ensure postal_code and city are handled as nullable
                            val safePostalCode = meter.postal_code ?: ""
                            val safeCity = meter.city ?: ""
                            Location(
                                id = generateLocationId(meter.address, safePostalCode, safeCity), // FIX: Calling local generateLocationId
                                name = meter.address, // Using address as display name
                                project_id = meter.project_id ?: "", // Default if null
                                address = meter.address,
                                postal_code = safePostalCode,
                                city = safeCity,
                                created_at = meter.created_at, // Pass created_at from meter
                                updated_at = meter.updated_at // Pass updated_at from meter
                            )
                        }.distinctBy { it.id }
                        locationDao.deleteAllLocations() // Clear existing locations
                        locationDao.insertAll(locations) // Insert new locations
                        Log.d(TAG, "Locations refreshed in local database.")
                    }

                    // Then, save meters
                    meterDao.deleteAllMeters()
                    meters?.let { meterDao.insertAll(it) }
                    Log.d(TAG, "All meters refreshed in local database.")

                } else {
                    Log.e(TAG, "Failed to fetch all meters: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing all meters: ${e.message}", e)
            }
        }
    }

    /**
     * Posts a new meter reading to the API.
     * If offline, it queues the request for later.
     * @param reading The Reading object to be sent to the API.
     * @return A Retrofit Response object with Unit type (204 No Content) or a custom success/failure indicator.
     */
    suspend fun postMeterReading(reading: Reading): Response<Unit> { // Changed to Response<Unit> as per ApiService
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to POST new meter reading for meter ID: ${reading.meter_id}. Value: ${reading.value}, Date: ${reading.date}")
                val response = apiService.postReading(reading)
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully POSTed reading. Response status: ${response.code()}")
                    refreshAllMeters() // Refresh UI after successful POST
                    Response.success(Unit) // Return success response
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to POST meter reading: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                    // If it's not successful, we might still want to queue it if it's a network issue.
                    // For now, if the server explicitly rejects (e.g., 400, 500), we don't queue.
                    Response.error(response.code(), ResponseBody.create(null, "Error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: IOException) { // Catch IOException specifically for network issues
                Log.e(TAG, "Network error during POST, queuing request: ${e.message}", e)
                queueRequest(
                    endpoint = "readings",
                    method = "POST",
                    body = gson.toJson(reading) // Serialize the Reading object to JSON string
                )
                // Indicate that the request was queued, but no immediate API success.
                // You might define a custom sealed class for better status handling.
                Response.success(Unit) // Indicate local success (queued)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during POST: ${e.message}", e)
                Response.error(500, ResponseBody.create(null, "Error: ${e.message}"))
            }
        }
    }

    /**
     * Queues an API request for later sending using WorkManager.
     * @param endpoint The API endpoint.
     * @param method The HTTP method.
     * @param body The JSON string body of the request.
     */
    private suspend fun queueRequest(endpoint: String, method: String, body: String) {
        val queuedRequest = QueuedRequest(endpoint = endpoint, method = method, body = body)
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Request queued locally: ${queuedRequest.id}")

        // Schedule the SyncWorker to send the queued requests
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // Only run when connected to network
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(syncWorkRequest)
        Log.d(TAG, "SyncWorker scheduled.")
    }
}
