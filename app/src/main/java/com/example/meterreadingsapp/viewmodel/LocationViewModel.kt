package com.example.meterreadingsapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.repository.MeterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import retrofit2.Response

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

    // StateFlow to hold the currently selected location's ID.
    // This will be the composite ID: "address|postal_code|city|house_number|house_number_addition"
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
                    // FIX: Include house_number and house_number_addition in search query
                    (location.address?.contains(query, ignoreCase = true) ?: false) ||
                            (location.city?.contains(query, ignoreCase = true) ?: false) ||
                            (location.postal_code?.contains(query, ignoreCase = true) ?: false) ||
                            (location.house_number?.contains(query, ignoreCase = true) ?: false) || // NEW
                            (location.house_number_addition?.contains(query, ignoreCase = true) ?: false) // NEW
                }
            }
        }.asLiveData()


    // LiveData for meters, which combines data from the repository with filtering and searching logic
    val meters: LiveData<List<Meter>> = combine(
        _selectedLocationId.filterNotNull().distinctUntilChanged(),
        _meterTypeFilter,
        _meterSearchQuery
    ) { locationId, typeFilter, meterQuery ->
        // This lambda runs when any of the combined flows emit a new value.
        // It will be executed on the Dispatchers.IO context due to .asLiveData's parameter.

        val parts = locationId.split("|")
        // Ensure to handle potential nulls when extracting parts for API call/filtering
        val address = parts.getOrNull(0) ?: "" // Address is assumed non-null for location ID base
        val postalCode = parts.getOrNull(1) // Can be null
        val city = parts.getOrNull(2) // Can be null
        val houseNumber = parts.getOrNull(3) // NEW: Extract house_number
        val houseNumberAddition = parts.getOrNull(4) // NEW: Extract house_number_addition


        // If address is blank, return empty list (shouldn't happen with generateLocationId logic)
        if (address.isBlank()) {
            return@combine emptyList<Meter>()
        }

        // Get meters from the repository (which uses local DB first).
        // .first() will get the current list emitted by the Flow.
        val allMeters = repository.getMetersForLocation(
            address,
            postalCode,
            city,
            houseNumber, // NEW: Pass houseNumber
            houseNumberAddition // NEW: Pass houseNumberAddition
        ).first()

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
    }.asLiveData(viewModelScope.coroutineContext + Dispatchers.IO)


    init {
        // When the ViewModel is initialized, refresh all meters from the API.
        refreshAllMeters()
    }

    /**
     * Triggers a refresh of all meters data from the API and updates the local database.
     * Runs in a coroutine within the ViewModel's scope.
     */
    fun refreshAllMeters() {
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
    }

    /**
     * NEW: Sets the search query for meters within the selected location.
     * @param query The search string for meter number.
     */
    fun setMeterSearchQuery(query: String) {
        _meterSearchQuery.value = query
    }


    /**
     * Updates the active meter type filters.
     * @param filters A set of strings representing the active energy types (e.g., "Electricity", "Heat", "All").
     */
    fun setMeterTypeFilter(filters: Set<String>) {
        _meterTypeFilter.value = filters
    }

    /**
     * Posts a new meter reading to the API or queues it for later.
     * @param reading The Reading object to post.
     */
    fun postMeterReading(reading: Reading) {
        viewModelScope.launch {
            val response: Response<Unit> = repository.postMeterReading(reading)
            if (response.isSuccessful) {
                Log.d(TAG, "Meter reading POSTed successfully. Status: ${response.code()}")
                _uiMessage.emit("Reading added successfully!")
            } else {
                val errorMessage = "Failed to add reading: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMessage)
                _uiMessage.emit(errorMessage)
            }
        }
    }
}
