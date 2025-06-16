package com.example.mypostsapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.mypostsapp.data.Location
import com.example.mypostsapp.data.Meter
import com.example.mypostsapp.data.Reading
import com.example.mypostsapp.repository.MeterRepository
// Removed: import com.example.mypostsapp.repository.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.switchMap
import retrofit2.Response
import kotlinx.coroutines.flow.MutableSharedFlow // Still used for general UI events if needed
import kotlinx.coroutines.flow.SharedFlow // Still used for general UI events if needed
import kotlinx.coroutines.flow.combine

/**
 * ViewModel for managing and providing Location and Meter data to the UI.
 * It interacts with the MeterRepository to fetch and store data.
 */
class LocationViewModel(private val repository: MeterRepository) : ViewModel() {

    private val TAG = "LocationViewModel"

    // StateFlow to hold the current search query. Initially empty.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // LiveData to expose the list of all unique locations (addresses) to the UI.
    // It observes changes from the local Room database via the repository and filters them by search query.
    val locations: LiveData<List<Location>> = repository.getUniqueLocations()
        .combine(_searchQuery) { locations, query ->
            // Filter the locations based on the current search query
            if (query.isBlank()) {
                locations // If query is empty, return all locations
            } else {
                locations.filter { location ->
                    // Use safe call operator and Elvis operator for nullable fields
                    location.address.contains(query, ignoreCase = true) ||
                            (location.city?.contains(query, ignoreCase = true) ?: false) ||
                            (location.postal_code?.contains(query, ignoreCase = true) ?: false)
                }
            }
        }.asLiveData()


    // StateFlow to hold the currently selected location's ID.
    // This will be the composite ID: "address|postal_code|city"
    private val _selectedLocationId = MutableStateFlow<String?>(null)
    val selectedLocationId: StateFlow<String?> = _selectedLocationId

    // LiveData to expose the list of meters for the currently selected building.
    // This will update automatically when _selectedBuildingId changes.
    val meters: LiveData<List<Meter>> = _selectedLocationId.asLiveData().switchMap { locationId: String? ->
        if (locationId != null) {
            val parts = locationId.split("|")
            if (parts.size == 3) {
                val address = parts[0]
                val postalCode = parts[1]
                val city = parts[2]
                repository.getMetersForLocation(address, postalCode, city).asLiveData()
            } else {
                Log.e(TAG, "Invalid location ID format: $locationId")
                MutableStateFlow<List<Meter>>(emptyList()).asLiveData()
            }
        } else {
            // If no location is selected, return an empty list of meters
            MutableStateFlow<List<Meter>>(emptyList()).asLiveData()
        }
    }

    // A SharedFlow to send one-time UI events (like showing a Toast or Dialog)
    // Keep this if you want to use it for other UI messages. If not, remove.
    private val _uiMessage = MutableSharedFlow<String>() // Changed to String for simple messages
    val uiMessage: SharedFlow<String> = _uiMessage


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
    }

    /**
     * Sets the search query for filtering locations.
     * @param query The text to search for.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Posts a new meter reading to the API.
     * This is the primary method for submitting new readings.
     * It now directly uses repository.postMeterReading which only posts to /readings.
     * @param reading The Reading object to be posted.
     */
    fun postMeterReading(reading: Reading) { // Renamed from updateMeterReading to postMeterReading
        viewModelScope.launch {
            val response = repository.postMeterReading(reading) // Call the POST method
            if (response.isSuccessful) {
                Log.d(TAG, "Meter reading POSTed successfully. Status: ${response.code()}")
                _uiMessage.emit("Reading added successfully!") // Emit success message
            } else {
                val errorMessage = "Failed to add reading: ${response.code()}"
                Log.e(TAG, errorMessage)
                _uiMessage.emit(errorMessage) // Emit error message
            }
        }
    }
}
