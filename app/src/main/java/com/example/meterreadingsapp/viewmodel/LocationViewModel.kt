package com.example.meterreadingsapp.viewmodel

// Removed: import android.app.Application // No longer needed if not AndroidViewModel
import android.net.Uri
import android.util.Log
// FIX: Changed from AndroidViewModel to ViewModel import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.repository.MeterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.asLiveData
import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.first

/**
 * ViewModel for managing UI-related data concerning Locations and Meters.
 * It interacts with the MeterRepository to fetch and update data, and provides LiveData streams
 * for UI observation.
 *
 * NOTE: This version of ViewModel will *not* apply the meter type filter.
 * The meter type filtering logic will reside in MainActivity.kt as per user's old logic.
 *
 * @param repository The MeterRepository instance for data operations.
 */
// FIX: Explicitly extending ViewModel and removed Application parameter
class LocationViewModel(private val repository: MeterRepository) : ViewModel() {

    private val TAG = "LocationViewModel"

    // LiveData for UI messages (e.g., success/error toasts)
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    // --- State for Location List View ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Locations filtered by search query
    val locations: LiveData<List<Location>> = repository.getUniqueLocations()
        .map { locationList ->
            val query = _searchQuery.value
            if (query.isBlank()) {
                locationList
            } else {
                locationList.filter {
                    it.name?.contains(query, ignoreCase = true) == true ||
                            it.address?.contains(query, ignoreCase = true) == true ||
                            it.city?.contains(query, ignoreCase = true) == true ||
                            it.postal_code?.contains(query, ignoreCase = true) == true
                }
            }
        }.asLiveData(viewModelScope.coroutineContext)

    // --- State for Meter List View ---
    val selectedLocationId: MutableLiveData<String?> = MutableLiveData(null)
    private val _meterSearchQuery = MutableStateFlow("")
    val meterSearchQuery: StateFlow<String> = _meterSearchQuery.asStateFlow()

    // Meters for the selected location, filtered only by search query (type filter is in Activity)
    val meters: LiveData<List<Meter>> = combine(
        selectedLocationId.asFlow().filterNotNull(),
        _meterSearchQuery
    ) { locationId, meterQuery ->
        // Fetch the location details from the repository (which uses LocationDao)
        val selectedLocation = repository.getLocationByIdFromDb(locationId).first()
        if (selectedLocation != null) {
            repository.getMetersByAddress(
                address = selectedLocation.address ?: "",
                postalCode = selectedLocation.postal_code,
                city = selectedLocation.city,
                houseNumber = selectedLocation.house_number,
                houseNumberAddition = selectedLocation.house_number_addition
            ).map { meterList ->
                // Apply meter search query filter only, type filter is handled in Activity
                if (meterQuery.isBlank()) {
                    meterList
                } else {
                    meterList.filter {
                        it.number.contains(meterQuery, ignoreCase = true) ||
                                it.location?.contains(meterQuery, ignoreCase = true) == true
                    }
                }
            }.first()
        } else {
            emptyList()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    ).asLiveData(viewModelScope.coroutineContext)

    /**
     * Sets the search query for filtering the list of locations.
     * @param query The search query string.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        Log.d(TAG, "Search query updated to: $query")
    }

    /**
     * Selects a location, triggering the display of meters for that location.
     * @param location The selected Location object, or null to go back to the location list.
     */
    fun selectLocation(location: Location?) {
        selectedLocationId.value = location?.id
        Log.d(TAG, "Selected location ID: ${location?.id ?: "null"}")
        // Reset meter specific filters when changing location
        _meterSearchQuery.value = ""
    }

    /**
     * Sets the search query for filtering the list of meters within the selected location.
     * @param query The meter search query string.
     */
    fun setMeterSearchQuery(query: String) {
        _meterSearchQuery.value = query
        Log.d(TAG, "Meter search query updated to: $query")
    }

    /**
     * Refreshes all meter data from the API and updates the local database.
     */
    fun refreshAllMeters() {
        viewModelScope.launch {
            Log.d(TAG, "Initiating refreshAllMeters from ViewModel.")
            try {
                repository.refreshAllMeters()
                _uiMessage.value = "Meters refreshed successfully!"
            } catch (e: Exception) {
                _uiMessage.value = "Error refreshing meters: ${e.message}"
                Log.e(TAG, "Error refreshing meters: ${e.message}", e)
            }
        }
    }

    /**
     * Posts a new meter reading to the API.
     * @param reading The Reading object to be posted.
     */
    fun postMeterReading(reading: Reading) {
        viewModelScope.launch {
            try {
                val response = repository.postMeterReading(reading)
                if (response.isSuccessful) {
                    _uiMessage.value = "Reading for meter ${reading.meter_id} posted successfully!"
                } else {
                    val errorMessage = response.errorBody()?.string() ?: response.message()
                    _uiMessage.value = "Failed to post reading for meter ${reading.meter_id}: $errorMessage"
                }
            } catch (e: Exception) {
                _uiMessage.value = "Error posting reading for meter ${reading.meter_id}: ${e.message}"
                Log.e(TAG, "Error posting reading: ${e.message}", e)
            }
        }
    }

    /**
     * Queues an image for upload to Supabase Storage.
     * @param imageUri The local URI of the image file.
     * @param fullStoragePath The full path in Supabase Storage (including bucket and file name).
     * @param projectId The ID of the project associated with the meter (for worker data).
     */
    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String) {
        viewModelScope.launch {
            try {
                // The actual upload logic is now handled by WorkManager via MeterRepository
                repository.queueImageUpload(imageUri, fullStoragePath, projectId)
                _uiMessage.value = "Image queued for upload to $fullStoragePath"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to queue image for upload: ${e.message}"
                Log.e(TAG, "Error queuing image for upload: ${e.message}", e)
            }
        }
    }
}
