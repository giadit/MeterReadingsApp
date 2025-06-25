package com.example.meterreadingsapp.repository

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.data.LocationDao
import com.example.meterreadingsapp.data.QueuedRequest
import com.example.meterreadingsapp.data.QueuedRequestDao
import com.example.meterreadingsapp.workers.SyncWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private val locationDao: LocationDao,
    private val queuedRequestDao: QueuedRequestDao,
    private val appContext: Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(appContext)

    /**
     * Private helper function to generate a unique ID for a Location, now including house_number and house_number_addition.
     * Ensure all components of the ID are non-null for this composite key.
     */
    private fun generateLocationId(
        address: String?,
        postalCode: String?,
        city: String?,
        houseNumber: String?, // NEW parameter
        houseNumberAddition: String? // NEW parameter
    ): String {
        val safeAddress = address ?: ""
        val safePostalCode = postalCode ?: ""
        val safeCity = city ?: ""
        val safeHouseNumber = houseNumber ?: "" // NEW
        val safeHouseNumberAddition = houseNumberAddition ?: "" // NEW
        return "${safeAddress}|${safePostalCode}|${safeCity}|${safeHouseNumber}|${safeHouseNumberAddition}" // Updated composite key
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
        return locationDao.getAllLocations()
    }

    /**
     * Retrieves a Flow of meters associated with a specific location (address, postal code, city, house_number, house_number_addition).
     * @param address The street address to filter by.
     * @param postalCode The postal code to filter by (nullable).
     * @param city The city to filter by (nullable).
     * @param houseNumber The house number to filter by (NEW, nullable).
     * @param houseNumberAddition The house number addition to filter by (NEW, nullable).
     * @return A Flow emitting a list of Meter objects matching the given address details.
     */
    fun getMetersForLocation(
        address: String,
        postalCode: String?,
        city: String?,
        houseNumber: String?, // FIX: Added houseNumber parameter
        houseNumberAddition: String? // FIX: Added houseNumberAddition parameter
    ): Flow<List<Meter>> {
        // Pass all parameters directly to MeterDao.getMetersByAddress
        return meterDao.getMetersByAddress(address, postalCode, city, houseNumber, houseNumberAddition)
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
                            val meterAddress = meter.address
                            val meterPostalCode = meter.postal_code
                            val meterCity = meter.city
                            val meterHouseNumber = meter.house_number // Get house_number
                            val meterHouseNumberAddition = meter.house_number_addition // Get house_number_addition

                            // FIX: Ensure 'name' is always non-null by building it
                            val locationDisplayName = buildLocationDisplayName(
                                meterAddress,
                                meterHouseNumber,
                                meterHouseNumberAddition
                                // consumer is intentionally NOT passed here, as per user request
                            ) ?: "No Address Provided" // Fallback if building fails

                            Location(
                                id = generateLocationId(
                                    meterAddress,
                                    meterPostalCode,
                                    meterCity,
                                    meterHouseNumber,
                                    meterHouseNumberAddition
                                ),
                                name = locationDisplayName, // FIX: Use the built non-null display name
                                project_id = meter.project_id, // Nullable now in Location.kt
                                address = meterAddress, // Nullable now in Location.kt
                                postal_code = meterPostalCode,
                                city = meterCity,
                                house_number = meterHouseNumber, // FIX: Store house_number
                                house_number_addition = meterHouseNumberAddition, // FIX: Store house_number_addition
                                created_at = meter.created_at,
                                updated_at = meter.updated_at
                            )
                        }.distinctBy { it.id }
                        locationDao.deleteAllLocations()
                        locationDao.insertAll(locations)
                        Log.d(TAG, "Locations refreshed in local database. Count: ${locations.size}")
                    }

                    // Then, save meters
                    meterDao.deleteAllMeters()
                    meters?.let { meterDao.insertAll(it) }
                    Log.d(TAG, "All meters refreshed in local database.")

                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to fetch all meters: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing all meters: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Helper function to build a user-friendly display name for a location,
     * including street, house number, and addition. Consumer is explicitly excluded.
     */
    private fun buildLocationDisplayName(
        address: String?,
        houseNumber: String?,
        houseNumberAddition: String?
        // consumer: String? // Removed consumer from parameters, as per user request
    ): String? {
        val parts = mutableListOf<String>()
        address?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        houseNumber?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        houseNumberAddition?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        val combinedAddressDetails = parts.joinToString(" ").trim()

        return combinedAddressDetails.takeIf { it.isNotBlank() } // Return combined string if not blank
    }


    /**
     * Posts a new meter reading to the API.
     * If offline, it queues the request for later.
     * @param reading The Reading object to be sent to the API.
     * @return A Retrofit Response object with Unit type (204 No Content) or a custom success/failure indicator.
     */
    suspend fun postMeterReading(reading: Reading): Response<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to POST new meter reading for meter ID: ${reading.meter_id}. Value: ${reading.value}, Date: ${reading.date}")
                val response = apiService.postReading(reading)
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully POSTed reading. Response status: ${response.code()}")
                    refreshAllMeters()
                    Response.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to POST meter reading: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                    Response.error(response.code(), ResponseBody.create(null, "Error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error during POST, queuing request: ${e.message}", e)
                queueRequest(
                    endpoint = "readings",
                    method = "POST",
                    body = gson.toJson(reading)
                )
                Response.success(Unit)
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

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(syncWorkRequest)
        Log.d(TAG, "SyncWorker scheduled.")
    }
}
