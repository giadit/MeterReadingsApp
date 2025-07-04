package com.example.meterreadingsapp.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.repository.MeterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow // ADDED: Import for flow builder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * ViewModel for managing location and meter data.
 * Provides data to the UI and handles data operations via [MeterRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LocationViewModel(private val repository: MeterRepository) : ViewModel() {

    // --- UI State Flows ---
    private val _selectedProjectId = MutableStateFlow<String?>(null)
    val selectedProjectId: StateFlow<String?> = _selectedProjectId.asStateFlow()

    private val _selectedLocationId = MutableStateFlow<String?>(null)
    val selectedLocationId: StateFlow<String?> = _selectedLocationId.asStateFlow()

    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery: StateFlow<String> = _projectSearchQuery.asStateFlow()

    private val _locationSearchQuery = MutableStateFlow("")
    val locationSearchQuery: StateFlow<String> = _locationSearchQuery.asStateFlow()

    private val _meterSearchQuery = MutableStateFlow("")
    val meterSearchQuery: StateFlow<String> = _meterSearchQuery.asStateFlow()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _uiMessage = MutableLiveData<String?>(null)
    val uiMessage: LiveData<String?> get() = _uiMessage

    // --- Data Flows and LiveData ---

    // Projects filtered by search query
    val projects: LiveData<List<Project>> =
        _projectSearchQuery
            .debounce(300) // Debounce for search input
            .distinctUntilChanged()
            .flatMapLatest { query ->
                flow { // Correctly use flow builder
                    _isLoading.postValue(true)
                    repository.getAllProjects().collect { allProjects ->
                        val filtered = if (query.isBlank()) {
                            allProjects
                        } else {
                            allProjects.filter { it.name?.contains(query, ignoreCase = true) == true }
                        }
                        _isLoading.postValue(false)
                        emit(filtered) // Emit the filtered list inside the flow builder
                    }
                }
            }.asLiveData()

    // Locations for the selected project, filtered by search query
    val locations: LiveData<List<Location>> =
        _selectedProjectId
            .combine(_locationSearchQuery.debounce(300).distinctUntilChanged()) { projectId, query ->
                Pair(projectId, query)
            }
            .flatMapLatest { (projectId, query) ->
                flow { // Correctly use flow builder
                    _isLoading.postValue(true)
                    if (projectId == null) {
                        _isLoading.postValue(false)
                        emit(emptyList()) // No project selected, no locations
                    } else {
                        repository.getLocationsForProject(projectId).collect { allLocations ->
                            val filtered = if (query.isBlank()) {
                                allLocations
                            } else {
                                allLocations.filter { it.name?.contains(query, ignoreCase = true) == true ||
                                        it.address?.contains(query, ignoreCase = true) == true ||
                                        it.city?.contains(query, ignoreCase = true) == true ||
                                        it.postal_code?.contains(query, ignoreCase = true) == true
                                }
                            }
                            _isLoading.postValue(false)
                            emit(filtered) // Emit the filtered list inside the flow builder
                        }
                    }
                }
            }.asLiveData()

    // Meters for the selected location, filtered by search query
    val meters: LiveData<List<Meter>> =
        _selectedLocationId
            .combine(_meterSearchQuery.debounce(300).distinctUntilChanged()) { locationId, query ->
                Pair(locationId, query)
            }
            .flatMapLatest { (locationId, query) ->
                flow { // Correctly use flow builder
                    _isLoading.postValue(true)
                    if (locationId == null) {
                        _isLoading.postValue(false)
                        emit(emptyList()) // No location selected, no meters
                    } else {
                        repository.getMetersForLocation(locationId).collect { allMeters ->
                            val filtered = if (query.isBlank()) {
                                allMeters
                            } else {
                                allMeters.filter { it.number.contains(query, ignoreCase = true) ||
                                        it.energy_type.contains(query, ignoreCase = true) ||
                                        it.address.contains(query, ignoreCase = true) ||
                                        it.city?.contains(query, ignoreCase = true) == true ||
                                        it.postal_code?.contains(query, ignoreCase = true) == true
                                }
                            }
                            _isLoading.postValue(false)
                            emit(filtered) // Emit the filtered list inside the flow builder
                        }
                    }
                }
            }.asLiveData()

    // --- Initialization ---
    init {
        refreshAllProjectsAndMeters()
    }

    // --- Public Functions for UI Interaction ---

    fun selectProject(project: Project?) {
        _selectedProjectId.value = project?.id
        _selectedLocationId.value = null // Reset selected location when project changes
        _locationSearchQuery.value = "" // Clear location search when project changes
        _meterSearchQuery.value = "" // Clear meter search when project changes
    }

    fun selectLocation(location: Location?) {
        _selectedLocationId.value = location?.id
        _meterSearchQuery.value = "" // Clear meter search when location changes
    }

    fun setProjectSearchQuery(query: String) {
        _projectSearchQuery.value = query
    }

    fun setLocationSearchQuery(query: String) {
        _locationSearchQuery.value = query
    }

    fun setMeterSearchQuery(query: String) {
        _meterSearchQuery.value = query
    }

    fun refreshAllProjectsAndMeters() {
        viewModelScope.launch {
            _isLoading.postValue(true)
            try {
                repository.refreshProjects()
                repository.refreshMeters()
                _uiMessage.postValue("Data refreshed successfully.")
            } catch (e: Exception) {
                _uiMessage.postValue("Error refreshing data: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    fun postMeterReading(reading: Reading) {
        viewModelScope.launch {
            try {
                repository.postReading(reading)
                _uiMessage.postValue("Reading for ${reading.meter_id} sent.")
            } catch (e: Exception) {
                _uiMessage.postValue("Error sending reading: ${e.message}")
            }
        }
    }

    fun queueImageUpload(fileUri: Uri, storagePath: String, projectId: String, meterId: String) {
        viewModelScope.launch {
            repository.queueImageUpload(fileUri, storagePath, projectId, meterId)
            _uiMessage.postValue("Image for meter $meterId queued for upload.")
        }
    }

    /**
     * Retrieves a project by its ID.
     * @param projectId The ID of the project to retrieve.
     * @return A Flow emitting the Project object or null if not found.
     */
    fun getProjectById(projectId: String) = repository.getProjectById(projectId)

    /**
     * NEW: Adds a new meter to the database and syncs with API.
     * @param meter The Meter object to add.
     */
    fun addMeter(meter: Meter) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                repository.addMeter(meter)
                _uiMessage.postValue("Meter ${meter.number} added successfully.")
            } catch (e: Exception) {
                _uiMessage.postValue("Error adding meter: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * NEW: Updates an existing meter in the database and syncs with API.
     * @param meter The Meter object with updated fields.
     */
    fun patchMeter(meter: Meter) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                repository.patchMeter(meter)
                _uiMessage.postValue("Meter ${meter.number} updated successfully.")
            } catch (e: Exception) {
                _uiMessage.postValue("Error updating meter: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * NEW: Deletes a meter from the database and syncs with API.
     * @param meterId The ID of the meter to delete.
     */
    fun deleteMeter(meterId: String) {
        viewModelScope.launch {
            try {
                _isLoading.postValue(true)
                repository.deleteMeter(meterId)
                _uiMessage.postValue("Meter deleted successfully.")
            } catch (e: Exception) {
                _uiMessage.postValue("Error deleting meter: ${e.message}")
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
}
