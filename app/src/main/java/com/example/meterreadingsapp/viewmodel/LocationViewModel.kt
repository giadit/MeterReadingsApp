package com.example.meterreadingsapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
// import androidx.lifecycle.MutableLiveData // Not used directly, can be removed if not needed elsewhere
import androidx.lifecycle.ViewModel
// import androidx.lifecycle.ViewModelProvider // Not used directly in this file
import androidx.lifecycle.asLiveData // Extension function to convert Flow to LiveData
import androidx.lifecycle.viewModelScope // Coroutine scope for ViewModels
import com.example.meterreadingsapp.data.Location // Corrected import
import com.example.meterreadingsapp.data.Meter // Corrected import
import com.example.meterreadingsapp.data.Reading // Corrected import
import com.example.meterreadingsapp.repository.MeterRepository // Corrected import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch // For launching coroutines
// import kotlinx.coroutines.withContext // Not used directly, can be removed if not needed elsewhere
import retrofit2.Response
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first // Import the 'first' extension function for Flow
// import androidx.lifecycle.switchMap // Not used directly, can be removed if not needed elsewhere

/**
 * ViewModel for managing and providing Location and Meter data to the UI.
 * It interacts with the MeterRepository to fetch and store data.
 */
class LocationViewModel(private val repository: MeterRepository) : ViewModel() {

    private val TAG = "LocationViewModel"

    // StateFlow to hold the current search query for locations. Initially empty.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // NEW: MutableStateFlow to hold the search query for meters within a location
    private val _meterSearchQuery = MutableStateFlow("")
    val meterSearchQuery: StateFlow<String> = _meterSearchQuery.asStateFlow()

    // MutableStateFlow to hold the current filter settings for meter types
    private val _meterTypeFilter = MutableStateFlow<Set<String>>(setOf("All"))
    val meterTypeFilter: StateFlow<Set<String>> = _meterTypeFilter.asStateFlow()

    // LiveData for UI messages (e.g., success/error toasts for offline queueing)
    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage

    // CORRECTED PLACEMENT: _selectedLocationId and selectedLocationId
    // StateFlow to hold the currently selected location's ID.
    // This will be the composite ID: "address|postal_code|city"
    private val _selectedLocationId = MutableStateFlow<String?>(null)
    val selectedLocationId: StateFlow<String?> = _selectedLocationId.asStateFlow()

    // LiveData to expose the list of all unique locations (addresses) to the UI.
    // It observes changes from the local Room database via the repository and filters them by search query.
    val locations: LiveData<List<Location>> = repository.getUniqueLocations()
        .combine(_searchQuery) { locations, query ->
            // Filter the locations based on the current search query
            if (query.isBlank()) {
                locations // If query is empty, return all locations
            } else {
                locations.filter { location ->
                    // Corrected to handle nullable fields from the Location data class definition.
                    location.address.contains(query, ignoreCase = true) ||
                            (location.city?.contains(query, ignoreCase = true) ?: false) || // Handle nullable city
                            (location.postal_code?.contains(query, ignoreCase = true) ?: false) // Handle nullable postal_code
                }
            }
        }.asLiveData()


    // LiveData for meters, which combines data from the repository with filtering and searching logic
    val meters: LiveData<List<Meter>> = combine(
        _selectedLocationId.filterNotNull().distinctUntilChanged(), // Now _selectedLocationId is declared before use
        _meterTypeFilter,
        _meterSearchQuery
    ) { locationId, typeFilter, meterQuery ->
        // This lambda runs when any of the combined flows emit a new value.
        // It will be executed on the Dispatchers.IO context due to .asLiveData's parameter.

        val parts = locationId.split("|")
        // Ensure to handle potential nulls when extracting parts for API call/filtering
        val address = parts.getOrNull(0) ?: ""
        val postalCode = parts.getOrNull(1) // Can be null
        val city = parts.getOrNull(2) // Can be null

        // If any part is missing, return empty list (shouldn't happen with generateLocationId logic)
        if (address.isBlank()) {
            return@combine emptyList<Meter>()
        }

        // Get meters from the repository (which uses local DB first).
        // .first() will get the current list emitted by the Flow.
        // Ensure postalCode and city are correctly passed as nullable strings
        val allMeters = repository.getMetersForLocation(address, postalCode, city).first()

        // Apply type filtering
        val filteredByType = if ("All" in typeFilter) {
            allMeters
        } else {
            allMeters.filter { meter ->
                typeFilter.any { selectedType ->
                    meter.energy_type.equals(selectedType, ignoreCase = true)
                }
            }
        }

        // Apply meter number search filtering
        val finalFilteredList = if (meterQuery.isNotBlank()) {
            filteredByType.filter { meter ->
                meter.number.contains(meterQuery, ignoreCase = true)
            }
        } else {
            filteredByType
        }
        finalFilteredList // Return the final filtered list
    }.asLiveData(viewModelScope.coroutineContext + Dispatchers.IO) // Convert the combined flow to LiveData


    init {
        // When the ViewModel is initialized, refresh all meters from the API.
        refreshAllMeters()
    }

    /**
     * Triggers a refresh of all meters data from the API and updates the local database.
     * Runs in a coroutine within the ViewModel's scope.
     */
    fun refreshAllMeters() { // Renamed from loadLocations to refreshAllMeters for clarity
        viewModelScope.launch {
            repository.refreshAllMeters()
        }
    }

    /**
     * Sets the currently selected location. This will trigger a refresh of the meters list.
     * @param location The Location object whose ID is to be selected, or null to clear selection.
     */
    fun selectLocation(location: Location?) {
        _selectedLocationId.value = location?.id
        // Reset meter search and type filter when a new location is selected or deselected
        _meterSearchQuery.value = ""
        _meterTypeFilter.value = setOf("All")
    }

    /**
     * Sets the search query for locations.
     * @param query The search string.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        // The `locations` LiveData will automatically react to this change
    }

    /**
     * NEW: Sets the search query for meters within the selected location.
     * @param query The search string for meter number.
     */
    fun setMeterSearchQuery(query: String) {
        _meterSearchQuery.value = query
        // The `meters` LiveData will automatically react to this change
    }


    /**
     * Updates the active meter type filters.
     * @param filters A set of strings representing the active energy types (e.g., "Electricity", "Heat", "All").
     */
    fun setMeterTypeFilter(filters: Set<String>) {
        _meterTypeFilter.value = filters
        // The `meters` LiveData will automatically react to this change
    }

    /**
     * Posts a new meter reading to the API or queues it for later.
     * @param reading The Reading object to post.
     */
    fun postMeterReading(reading: Reading) {
        viewModelScope.launch {
            // Changed from repository.postReading to repository.postMeterReading based on repository
            val response: Response<Unit> = repository.postMeterReading(reading)
            if (response.isSuccessful) {
                Log.d(TAG, "Meter reading POSTed successfully. Status: ${response.code()}")
                _uiMessage.emit("Reading added successfully!") // Emit success message
            } else {
                val errorMessage = "Failed to add reading: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMessage)
                _uiMessage.emit(errorMessage) // Emit error message
            }
        }
    }
}