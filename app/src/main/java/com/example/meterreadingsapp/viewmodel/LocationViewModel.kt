package com.example.meterreadingsapp.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import com.example.meterreadingsapp.data.Building
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterWithObisPoints // NEW IMPORT
import com.example.meterreadingsapp.data.NewMeterRequest
import com.example.meterreadingsapp.data.ObisCode // ADDED: Import
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
    val meters: LiveData<List<MeterWithObisPoints>> = selectedBuildingId.flatMapLatest { buildingId -> // CHANGED: Output type
        if (buildingId == null) {
            flowOf(emptyList())
        } else {
            // CHANGED: Call the renamed repository function
            repository.getMetersWithObisByBuildingIdFromDb(buildingId)
        }
    }.combine(_meterSearchQuery) { meterList, query ->
        if (query.isBlank()) {
            meterList
        } else {
            // CHANGED: Filtering logic now uses the nested 'meter' object
            meterList.filter {
                it.meter.number.contains(query, ignoreCase = true) ||
                        it.meter.location?.contains(query, ignoreCase = true) == true
            }
        }
    }.asLiveData()

    // ADDED: Property to expose OBIS codes to the MainActivity
    val allObisCodes: LiveData<List<ObisCode>> = repository.getAllObisCodesFromDb().asLiveData()

    // UPDATED: Function signature now accepts a list of OBIS code IDs
    fun addNewMeter(
        newMeterRequest: NewMeterRequest,
        initialReading: Reading,
        selectedObisCodeIds: List<String> // ADDED
    ) {
        viewModelScope.launch {
            _uiMessage.value = "Creating new meter..."
            // UPDATED: Pass the list of IDs to the repository
            val success = repository.createNewMeter(newMeterRequest, initialReading, selectedObisCodeIds)
            if (success) {
                _uiMessage.value = "New meter created successfully!"
                refreshAllData() // Refresh data to show the new meter
            } else {
                _uiMessage.value = "Failed to create new meter. Please try again."
            }
        }
    }

    fun exchangeMeter(
        oldMeter: Meter,
        oldMeterLastReading: Reading,
        newMeterNumber: String,
        newMeterInitialReading: Reading
    ) {
        viewModelScope.launch {
            _uiMessage.value = "Exchanging meter..."
            val success = repository.performMeterExchange(
                oldMeter,
                oldMeterLastReading,
                newMeterNumber,
                newMeterInitialReading
            )
            if (success) {
                _uiMessage.value = "Meter exchanged successfully!"
                refreshAllData()
            } else {
                _uiMessage.value = "Meter exchange failed. Please try again."
            }
        }
    }

    fun setProjectSearchQuery(query: String) { _projectSearchQuery.value = query }
    fun selectProject(project: Project?) { selectedProjectId.value = project?.id; selectedBuildingId.value = null }
    fun setBuildingSearchQuery(query: String) { _buildingSearchQuery.value = query }
    fun selectBuilding(building: Building?) { selectedBuildingId.value = building?.id }
    fun setMeterSearchQuery(query: String) { _meterSearchQuery.value = query }
    fun refreshAllData() { viewModelScope.launch { Log.d(TAG, "Initiating full data refresh from ViewModel."); try { repository.refreshAllData(); _uiMessage.value = "All data refreshed successfully!" } catch (e: Exception) { _uiMessage.value = "Error refreshing data: ${e.message}"; Log.e(TAG, "Error during data refresh: ${e.message}", e) } } }
    fun postMeterReading(reading: Reading) { viewModelScope.launch { repository.postMeterReading(reading) } }
    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String, meterId: String) { repository.queueImageUpload(imageUri, fullStoragePath, projectId, meterId) }
}
