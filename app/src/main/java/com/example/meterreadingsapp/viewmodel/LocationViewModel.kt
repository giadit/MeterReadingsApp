package com.example.meterreadingsapp.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Project
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
import java.util.Locale

/**
 * ViewModel for managing UI-related data concerning Projects, Locations, and Meters.
 * It interacts with the MeterRepository to fetch and update data, and provides LiveData streams
 * for UI observation.
 *
 * @param repository The MeterRepository instance for data operations.
 */
class LocationViewModel(private val repository: MeterRepository) : ViewModel() {

    private val TAG = "LocationViewModel"

    // LiveData for UI messages (e.g., success/error toasts)
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    // --- State for Project List View ---
    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery: StateFlow<String> = _projectSearchQuery.asStateFlow()

    // LiveData for the list of projects, enhanced to filter by project properties and associated location properties
    val projects: LiveData<List<Project>> = combine(
        repository.getAllProjectsFromDb(), // Get all projects
        repository.getUniqueLocations(), // Get all locations to check for associated locations
        _projectSearchQuery
    ) { projectList, allLocations, query ->
        val lowerCaseQuery = query.lowercase(Locale.ROOT)

        projectList.filter { project ->
            // Check if project's own properties match the query
            val projectMatches =
                project.name?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                        project.address?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                        project.projectNumber?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true

            if (projectMatches) {
                true // If project itself matches, include it
            } else {
                // Check if any associated location matches the query
                allLocations.any { location ->
                    location.project_id == project.id && // Check if location belongs to this project
                            (location.name?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                                    location.address?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                                    location.city?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                                    location.postal_code?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                                    location.house_number?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                                    location.house_number_addition?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true)
                }
            }
        }.map { project ->
            // For each project, count its associated locations
            val buildingsCount = allLocations.count { it.project_id == project.id }
            project.copy(buildingsCount = buildingsCount) // Create a copy with updated buildingsCount
        }.let {
            if (query.isBlank()) {
                it // If no query, return the list with computed building counts
            } else {
                it // Return filtered and augmented list
            }
        }
    }.asLiveData(viewModelScope.coroutineContext)

    val selectedProjectId: MutableLiveData<String?> = MutableLiveData(null)

    // --- State for Location List View (now dependent on selected project) ---
    private val _locationSearchQuery = MutableStateFlow("")
    val locationSearchQuery: StateFlow<String> = _locationSearchQuery.asStateFlow()


    val locations: LiveData<List<Location>> = combine(
        selectedProjectId.asFlow(),
        _locationSearchQuery
    ) { projectId, locationQuery ->
        repository.getLocationsByProjectId(projectId).first()
            .filter { location ->
                val query = locationQuery
                if (query.isBlank()) {
                    true
                } else {
                    location.name?.contains(query, ignoreCase = true) == true ||
                            location.address?.contains(query, ignoreCase = true) == true ||
                            location.city?.contains(query, ignoreCase = true) == true ||
                            location.postal_code?.contains(query, ignoreCase = true) == true
                }
            }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        emptyList()
    ).asLiveData(viewModelScope.coroutineContext)

    // --- State for Meter List View ---
    val selectedLocationId: MutableLiveData<String?> = MutableLiveData(null)
    private val _meterSearchQuery = MutableStateFlow("")
    val meterSearchQuery: StateFlow<String> = _meterSearchQuery.asStateFlow()

    // Meters for the selected location, filtered only by search query (type filter is in Activity)
    val meters: LiveData<List<Meter>> = combine(
        selectedLocationId.asFlow().filterNotNull(),
        _meterSearchQuery
    ) { locationId, meterQuery ->
        val selectedLocation = repository.getLocationByIdFromDb(locationId).first()
        if (selectedLocation != null) {
            repository.getMetersByAddress(
                address = selectedLocation.address ?: "",
                postalCode = selectedLocation.postal_code,
                city = selectedLocation.city,
                houseNumber = selectedLocation.house_number,
                houseNumberAddition = selectedLocation.house_number_addition
            ).map { meterList ->
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

    // REMOVED: LiveData for FileMetadata associated with the currently selected location's meters
    // val fileMetadataForMeters: LiveData<Map<String, FileMetadata>> = ... (removed)


    /**
     * Sets the search query for filtering the list of projects.
     * @param query The search query string.
     */
    fun setProjectSearchQuery(query: String) {
        _projectSearchQuery.value = query
        Log.d(TAG, "Project search query updated to: $query")
    }

    /**
     * Selects a project, triggering the display of locations for that project.
     * @param project The selected Project object, or null to go back to the project list.
     */
    fun selectProject(project: Project?) {
        selectedProjectId.value = project?.id
        Log.d(TAG, "Selected project ID: ${project?.id ?: "null"}")
        // Reset location and meter specific filters when changing project
        _locationSearchQuery.value = ""
        selectedLocationId.value = null
        _meterSearchQuery.value = ""
    }

    /**
     * Sets the search query for filtering the list of locations within the selected project.
     * @param query The location search query string.
     */
    fun setLocationSearchQuery(query: String) {
        _locationSearchQuery.value = query
        Log.d(TAG, "Location search query updated to: $query")
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
     * Refreshes all project and meter data from the API and updates the local database.
     */
    fun refreshAllProjectsAndMeters() {
        viewModelScope.launch {
            Log.d(TAG, "Initiating refreshAllProjectsAndMeters from ViewModel.")
            try {
                repository.refreshAllProjectsAndMeters()
                _uiMessage.value = "All data refreshed successfully!"
            } catch (e: Exception) {
                _uiMessage.value = "Error refreshing data: ${e.message}"
                Log.e(TAG, "Error refreshing all projects and meters: ${e.message}", e)
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
     * @param meterId The ID of the meter associated with the image.
     */
    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String, meterId: String) {
        viewModelScope.launch {
            try {
                repository.queueImageUpload(imageUri, fullStoragePath, projectId, meterId)
                _uiMessage.value = "Image queued for upload to $fullStoragePath"
            } catch (e: Exception) {
                _uiMessage.value = "Failed to queue image for upload: ${e.message}"
                Log.e(TAG, "Error queuing image for upload: ${e.message}", e)
            }
        }
    }

    // REMOVED: deleteFileAndMetadata as it's no longer needed for server-side metadata deletion
    // fun deleteFileAndMetadata(fileMetadataId: String, storagePath: String, localFileUri: Uri?) { ... }
}
