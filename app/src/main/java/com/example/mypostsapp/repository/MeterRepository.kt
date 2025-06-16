package com.example.mypostsapp.repository

import android.util.Log
import com.example.mypostsapp.api.ApiService
import com.example.mypostsapp.data.Location
import com.example.mypostsapp.data.Meter
import com.example.mypostsapp.data.MeterDao
import com.example.mypostsapp.data.Reading
import com.example.mypostsapp.data.ReadingDao
import com.example.mypostsapp.data.LocationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import okhttp3.ResponseBody // Still needed for error response fallback if postReading fails
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
 */
class MeterRepository(
    private val apiService: ApiService,
    private val meterDao: MeterDao,
    private val readingDao: ReadingDao,
    private val locationDao: LocationDao
) {
    private val TAG = "MeterRepository"
    private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

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
        return locationDao.getAllLocations()
    }

    /**
     * Retrieves a Flow of meters associated with a specific location (address, postal code, city).
     * @param address The street address to filter by.
     * @param postalCode The postal code to filter by.
     * @param city The city to filter by.
     * @return A Flow emitting a list of Meter objects matching the given location details.
     */
    fun getMetersForLocation(address: String, postalCode: String, city: String): Flow<List<Meter>> {
        // Handle potential nullability for database query
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
                                id = generateLocationId(meter.address, safePostalCode, safeCity),
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
     * This is the primary method for submitting new readings.
     * @param reading The Reading object to be sent to the API.
     * @return A Retrofit Response object with Unit type (204 No Content).
     */
    suspend fun postMeterReading(reading: Reading): Response<Unit> { // FIX: Changed return type to Response<Unit>
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to POST new meter reading for meter ID: ${reading.meter_id}. Value: ${reading.value}, Date: ${reading.date}")
                val response = apiService.postReading(reading)
                if (response.isSuccessful) {
                    // When Prefer: return=minimal is used, a 204 No Content is returned, no body to parse.
                    Log.d(TAG, "Successfully POSTed reading. Response status: ${response.code()}")

                    // After successful POST, refresh all meters to get the latest data.
                    // This assumes the Supabase backend updates 'last_reading' and 'last_reading_date'
                    // on the Meter table based on new entries in the 'readings' table (e.g., via a trigger).
                    refreshAllMeters()

                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to POST meter reading: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                }
                response // Return the original response (which is now Response<Unit>)
            } catch (e: Exception) {
                Log.e(TAG, "Error POSTing meter reading: ${e.message}", e)
                // Return an unsuccessful response in case of network/other exception
                Response.error(500, ResponseBody.create(null, "Error: ${e.message}"))
            }
        }
    }
}

// Removed: Sealed class to represent the different outcomes of a meter update operation.
// Removed: UpdateResult sealed class as patchMeter method is no longer used
/*
sealed class UpdateResult {
    object Success : UpdateResult()
    data class OlderDateError(val message: String) : UpdateResult()
    data class ApiError(val message: String) : UpdateResult()
}
*/
