package com.example.meterreadingsapp.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.example.meterreadingsapp.data.Building
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterWithObisPoints
import com.example.meterreadingsapp.data.NewMeterRequest
import com.example.meterreadingsapp.data.ObisCode
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.repository.MeterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class LocationViewModel(private val repository: MeterRepository) : ViewModel() {

    private val TAG = "MainViewModel"

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _projectSearchQuery = MutableStateFlow("")
    val projectSearchQuery: StateFlow<String> = _projectSearchQuery.asStateFlow()

    val projects: LiveData<List<Project>> = repository.getAllProjectsFromDb()
        .combine(_projectSearchQuery) { projectList, query ->
            if (query.isBlank()) {
                projectList
            } else {
                val lowerCaseQuery = query.lowercase(Locale.ROOT)
                projectList.filter { project ->
                    project.name?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true ||
                            project.projectNumber?.lowercase(Locale.ROOT)?.contains(lowerCaseQuery) == true
                }
            }
        }.asLiveData()

    val selectedProjectId = MutableStateFlow<String?>(null)

    private val _buildingSearchQuery = MutableStateFlow("")
    val buildingSearchQuery: StateFlow<String> = _buildingSearchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val buildings: LiveData<List<Building>> = selectedProjectId.flatMapLatest { projectId ->
        if (projectId == null) {
            flowOf(emptyList())
        } else {
            repository.getBuildingsByProjectIdFromDb(projectId)
        }
    }.combine(_buildingSearchQuery) { buildingList, query ->
        if (query.isBlank()) {
            buildingList
        } else {
            val lowerCaseQuery = query.lowercase(Locale.ROOT)
            buildingList.filter { building ->
                building.name.lowercase(Locale.ROOT).contains(lowerCaseQuery) ||
                        building.street.lowercase(Locale.ROOT).contains(lowerCaseQuery)
            }
        }
    }.asLiveData()

    val selectedBuildingId = MutableStateFlow<String?>(null)

    private val _meterSearchQuery = MutableStateFlow("")
    val meterSearchQuery: StateFlow<String> = _meterSearchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val meters: LiveData<List<MeterWithObisPoints>> = selectedBuildingId.flatMapLatest { buildingId ->
        if (buildingId == null) {
            flowOf(emptyList())
        } else {
            repository.getMetersWithObisByBuildingIdFromDb(buildingId)
        }
    }.combine(_meterSearchQuery) { meterList, query ->
        if (query.isBlank()) {
            meterList
        } else {
            meterList.filter {
                it.meter.number.contains(query, ignoreCase = true) ||
                        it.meter.location?.contains(query, ignoreCase = true) == true
            }
        }
    }.asLiveData()

    val allObisCodes: LiveData<List<ObisCode>> = repository.getAllObisCodesFromDb().asLiveData()

    fun addNewMeter(
        newMeterRequest: NewMeterRequest,
        initialReadingsMap: Map<String, Reading>
    ) {
        viewModelScope.launch {
            _uiMessage.value = "Creating new meter..."
            val success = repository.createNewMeter(newMeterRequest, initialReadingsMap)
            if (success) {
                _uiMessage.value = "New meter created successfully!"
                // We don't need a full refresh here anymore since createNewMeter inserts locally
                // but we might want to refresh current view if needed.
                // refreshAllData() call removed to stay consistent with lazy loading
            } else {
                _uiMessage.value = "Failed to create new meter. Please try again."
            }
        }
    }

    fun exchangeMeter(
        oldMeter: Meter,
        oldMeterReadings: List<Reading>,
        newMeterNumber: String,
        newMeterReadingsMap: Map<String, Reading> // Key: ObisCodeId
    ) {
        viewModelScope.launch {
            _uiMessage.value = "Exchanging meter..."
            val success = repository.performMeterExchange(
                oldMeter,
                oldMeterReadings,
                newMeterNumber,
                newMeterReadingsMap
            )
            if (success) {
                _uiMessage.value = "Meter exchanged successfully!"
                // Refresh local meter list for current building
                oldMeter.buildingId?.let { repository.refreshMetersForBuilding(it) }
            } else {
                _uiMessage.value = "Meter exchange failed. Please try again."
            }
        }
    }

    fun setProjectSearchQuery(query: String) { _projectSearchQuery.value = query }

    // UPDATED: Trigger building fetch when project is selected
    fun selectProject(project: Project?) {
        selectedProjectId.value = project?.id
        selectedBuildingId.value = null
        project?.id?.let { projectId ->
            viewModelScope.launch {
                try {
                    // _uiMessage.value = "Loading buildings..." // Optional: show loading feedback
                    repository.refreshBuildingsForProject(projectId)
                } catch (e: Exception) {
                    _uiMessage.value = "Error loading buildings: ${e.message}"
                }
            }
        }
    }

    fun setBuildingSearchQuery(query: String) { _buildingSearchQuery.value = query }

    // UPDATED: Trigger meter fetch when building is selected
    fun selectBuilding(building: Building?) {
        selectedBuildingId.value = building?.id
        building?.id?.let { buildingId ->
            viewModelScope.launch {
                try {
                    _uiMessage.value = "Loading meters..."
                    repository.refreshMetersForBuilding(buildingId)
                    _uiMessage.value = null // Clear loading message on success
                } catch (e: Exception) {
                    _uiMessage.value = "Error loading meters: ${e.message}"
                }
            }
        }
    }

    fun setMeterSearchQuery(query: String) { _meterSearchQuery.value = query }

    // UPDATED: Now context-aware. Refreshes data based on the current view hierarchy.
    fun refreshAllData() {
        viewModelScope.launch {
            Log.d(TAG, "Initiating data refresh based on context.")
            try {
                val currentBuildingId = selectedBuildingId.value
                val currentProjectId = selectedProjectId.value

                if (currentBuildingId != null) {
                    // Case 1: We are inside a Building, looking at Meters
                    _uiMessage.value = "Refreshing meters..."
                    repository.refreshMetersForBuilding(currentBuildingId)
                    _uiMessage.value = "Meters updated!"
                } else if (currentProjectId != null) {
                    // Case 2: We are inside a Project, looking at Buildings
                    _uiMessage.value = "Refreshing buildings..."
                    repository.refreshBuildingsForProject(currentProjectId)
                    _uiMessage.value = "Buildings updated!"
                } else {
                    // Case 3: We are at the root, looking at Projects
                    _uiMessage.value = "Refreshing projects..."
                    repository.refreshProjectsAndMetadata()
                    _uiMessage.value = "Projects and metadata updated!"
                }
            } catch (e: Exception) {
                _uiMessage.value = "Error refreshing data: ${e.message}"
                Log.e(TAG, "Error during data refresh: ${e.message}", e)
            }
        }
    }

    fun postMeterReading(reading: Reading) { viewModelScope.launch { repository.postMeterReading(reading) } }
    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String, meterId: String) { repository.queueImageUpload(imageUri, fullStoragePath, projectId, meterId) }
}