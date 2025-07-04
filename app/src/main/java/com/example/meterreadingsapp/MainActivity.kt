package com.example.meterreadingsapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.DatePicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meterreadingsapp.adapter.LocationAdapter
import com.example.meterreadingsapp.adapter.MeterAdapter
import com.example.meterreadingsapp.adapter.ProjectAdapter
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.Reading
// REMOVED: import com.example.meterreadingsapp.data.FileMetadata // No longer directly used in MainActivity
import com.example.meterreadingsapp.databinding.ActivityMainBinding
import com.example.meterreadingsapp.repository.MeterRepository
import com.example.meterreadingsapp.viewmodel.LocationViewModel
import com.example.meterreadingsapp.viewmodel.LocationViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.content.pm.PackageManager
import android.widget.EditText
import androidx.core.content.edit
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var projectAdapter: ProjectAdapter
    private lateinit var locationAdapter: LocationAdapter
    private lateinit var meterAdapter: MeterAdapter

    private val uiDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val s3KeyDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timeFormat = SimpleDateFormat("HHmm", Locale.US)

    private var selectedReadingDate: Calendar = Calendar.getInstance()
    private val selectedMeterTypesFilter: MutableSet<String> = mutableSetOf("All")

    private lateinit var filterTextViews: Map<String, TextView>

    private val PREFS_NAME = "LoginPrefs"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER_ME = "rememberMe"

    private var currentPhotoUri: Uri? = null
    private var currentMeterForPhoto: Meter? = null

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                currentMeterForPhoto?.let { meter ->
                    launchCamera(meter)
                }
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show()
                currentMeterForPhoto = null
                currentPhotoUri = null
            }
        }

    private val takePictureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                currentPhotoUri?.let { uri ->
                    currentMeterForPhoto?.let { meter ->
                        meterAdapter.updateMeterImageUri(meter.id, uri)
                        Toast.makeText(this, getString(R.string.picture_saved_success, uri.lastPathSegment), Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, getString(R.string.picture_capture_failed), Toast.LENGTH_SHORT).show()
                currentPhotoUri = null
                currentMeterForPhoto = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    )
        }

        setSupportActionBar(binding.toolbar)
        // No title initially, will be set by navigation state

        val apiService = RetrofitClient.getService(ApiService::class.java)
        val database = AppDatabase.getDatabase(applicationContext)
        val locationDao = database.locationDao()
        val meterDao = database.meterDao()
        val readingDao = database.readingDao()
        val queuedRequestDao = database.queuedRequestDao()
        val projectDao = database.projectDao()
        // REMOVED: val fileMetadataDao = database.fileMetadataDao() // No longer needed for this simplified flow

        // UPDATED: Pass MeterRepository constructor without fileMetadataDao
        val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, projectDao, applicationContext)

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupProjectRecyclerView()
        setupLocationRecyclerView()
        setupMeterRecyclerView()
        setupSearchView() // This search view will dynamically control project or location search
        setupDateSelection()
        setupTypeFilter()
        setupSendButton()
        setupMeterSearchView() // This will now be specific to meters

        observeProjects()
        observeLocations()
        observeMeters()
        observeUiMessages()
        // REMOVED: observeFileMetadata() // No longer observing file metadata from server

        // Initial visibility states: Start with projects visible
        binding.projectsContainer.isVisible = true
        binding.locationsContainer.isVisible = false
        binding.metersContainer.isVisible = false
        binding.sendReadingsFab.isVisible = false
        binding.backButton.isVisible = false // Hidden initially as we start at the top level (projects)

        binding.refreshButton.setOnClickListener {
            locationViewModel.refreshAllProjectsAndMeters()
            Log.d("MainActivity", getString(R.string.log_refreshing_all_data))
        }

        binding.backButton.setOnClickListener {
            when {
                // If currently viewing meters, go back to locations
                binding.metersContainer.isVisible -> {
                    locationViewModel.selectLocation(null) // Deselect location to show all locations for current project
                    binding.metersContainer.isVisible = false
                    binding.locationsContainer.isVisible = true // Show locations container
                    binding.sendReadingsFab.isVisible = false // FAB visible only for meters
                    binding.backButton.isVisible = true // Still need back button to go to projects

                    // Reset meter search, keep location search
                    binding.meterSearchView.setQuery("", false)
                    locationViewModel.setMeterSearchQuery("")
                    updateToolbarForLocations(locationViewModel.selectedProjectId.value) // Update toolbar title for locations
                }
                // If currently viewing locations, go back to projects
                binding.locationsContainer.isVisible -> {
                    locationViewModel.selectProject(null) // Deselect project to show all projects
                    binding.locationsContainer.isVisible = false
                    binding.projectsContainer.isVisible = true // Show projects container
                    binding.backButton.isVisible = false // No back button needed on project list (top level)
                    binding.sendReadingsFab.isVisible = false // FAB visible only for meters

                    // Reset all search views
                    binding.searchView.setQuery("", false)
                    binding.searchView.isIconified = true
                    locationViewModel.setProjectSearchQuery("") // Reset project search query
                    binding.meterSearchView.setQuery("", false)
                    locationViewModel.setMeterSearchQuery("")
                    updateToolbarForProjects() // Update toolbar title for projects
                }
            }
            binding.noDataTextView.isVisible = false // Hide no data message on back navigation
        }

        binding.logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun setupProjectRecyclerView() {
        projectAdapter = ProjectAdapter { project ->
            locationViewModel.selectProject(project)
            binding.projectsContainer.isVisible = false // Hide projects
            binding.locationsContainer.isVisible = true // Show locations for selected project
            binding.backButton.isVisible = true // Show back button
            updateToolbarForLocations(project.id) // Update toolbar title for locations
        }
        binding.projectsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = projectAdapter
        }
    }

    private fun setupLocationRecyclerView() {
        locationAdapter = LocationAdapter { location ->
            locationViewModel.selectLocation(location)
            binding.locationsContainer.isVisible = false
            binding.metersContainer.isVisible = true
            binding.sendReadingsFab.isVisible = true
            binding.backButton.isVisible = true // Still show back button when going to meters
            updateToolbarForMeters(location) // Update toolbar for meters
        }
        binding.locationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = locationAdapter
        }
    }

    private fun setupMeterRecyclerView() {
        meterAdapter = MeterAdapter(
            onCameraClicked = { meter, currentUri ->
                currentMeterForPhoto = meter
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera(meter)
                } else {
                    requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            },
            onViewImageClicked = { meter, imageUri ->
                viewImage(imageUri)
            },
            onDeleteImageClicked = { meter, imageUri -> // UPDATED: Removed FileMetadata parameter
                confirmAndDeleteImage(meter, imageUri) // UPDATED: Removed FileMetadata parameter
            },
            onEditMeterClicked = { meter ->
                // TODO: Implement edit meter dialog
                Toast.makeText(this, "Edit meter: ${meter.number}", Toast.LENGTH_SHORT).show()
            },
            onDeleteMeterClicked = { meter ->
                // TODO: Implement delete meter confirmation
                Toast.makeText(this, "Delete meter: ${meter.number}", Toast.LENGTH_SHORT).show()
            },
            currentMode = { AppMode.READINGS } // Default to READINGS mode for now
        )
        binding.metersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = meterAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                if (binding.projectsContainer.isVisible) {
                    locationViewModel.setProjectSearchQuery(newText ?: "")
                } else if (binding.locationsContainer.isVisible) {
                    locationViewModel.setLocationSearchQuery(newText ?: "")
                }
                return true
            }
        })

        // Adjust toolbar title visibility based on current active container
        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            binding.toolbarTitle.isVisible = !hasFocus && binding.searchView.query.isNullOrEmpty()
        }
    }

    private fun setupMeterSearchView() {
        binding.meterSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                locationViewModel.setMeterSearchQuery(newText ?: "")
                return true
            }
        })

        val searchEditText: EditText? = binding.meterSearchView.findViewById(androidx.appcompat.R.id.search_src_text)
        searchEditText?.let {
            it.background = null
            it.setTextColor(ContextCompat.getColor(this, R.color.black))
            it.setHintTextColor(ContextCompat.getColor(this, R.color.dark_gray_text))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.textCursorDrawable = ContextCompat.getDrawable(this, R.drawable.text_cursor_orange)
            }
        }
    }

    private fun launchCamera(meter: Meter) {
        currentPhotoUri = createImageFileForMeter(meter)
        currentPhotoUri?.let { uri ->
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                putExtra("android.intent.extra.FLASH_MODE", "on")
            }
            takePictureLauncher.launch(cameraIntent)
        } ?: run {
            Toast.makeText(this, getString(R.string.error_creating_image_file), Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(IOException::class)
    private fun createImageFileForMeter(meter: Meter): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFileName = "${timeStamp}_${meter.number.replace("/", "_").replace(".", "_")}"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            imageFile
        )
    }

    private fun viewImage(imageUri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(imageUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error viewing image: ${e.message}", e)
            Toast.makeText(this, getString(R.string.error_viewing_image), Toast.LENGTH_SHORT).show()
        }
    }

    // UPDATED: Simplified confirmAndDeleteImage to only handle local image deletion
    private fun confirmAndDeleteImage(meter: Meter, imageUri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_delete_image_title))
            .setMessage(getString(R.string.confirm_delete_image_message, meter.number))
            .setPositiveButton(getString(R.string.delete_button_text)) { dialog, _ ->
                // Only delete the local image file and update adapter
                imageUri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) {
                            Log.d("MainActivity", "Local image file deleted: $path")
                            meterAdapter.removeMeterImageUri(meter.id) // Update UI
                            Toast.makeText(this, getString(R.string.image_deleted_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Log.w("MainActivity", "Failed to delete local image file: $path")
                            Toast.makeText(this, getString(R.string.image_deleted_failed), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w("MainActivity", "Local image file not found for deletion: $path")
                        Toast.makeText(this, getString(R.string.image_file_not_found), Toast.LENGTH_SHORT).show()
                        meterAdapter.removeMeterImageUri(meter.id) // Still update UI if file not found
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun setupDateSelection() {
        updateSelectedDateText()

        binding.selectedDateTextView.setOnClickListener { showDatePicker() }
        binding.selectDateButton.setOnClickListener { showDatePicker() }
    }

    private fun updateSelectedDateText() {
        binding.selectedDateTextView.text = uiDateFormat.format(selectedReadingDate.time)
    }

    private fun showDatePicker() {
        val year = selectedReadingDate.get(Calendar.YEAR)
        val month = selectedReadingDate.get(Calendar.MONTH)
        val day = selectedReadingDate.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            R.style.AppDatePickerDialogTheme,
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDayOfMonth: Int ->
                selectedReadingDate.set(selectedYear, selectedMonth, selectedDayOfMonth)
                updateSelectedDateText()
            },
            year, month, day
        )
        datePickerDialog.show()

        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setBackgroundResource(R.drawable.button_orange_background)
        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)

        datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.bright_orange))
    }

    private fun setupTypeFilter() {
        filterTextViews = mapOf(
            "All" to binding.filterAll,
            "Electricity" to binding.filterElectricity,
            "Heat" to binding.filterHeat,
            "Gas" to binding.filterGas
        )

        filterTextViews.forEach { (type, textView) ->
            textView.setOnClickListener {
                toggleFilter(type)
            }
        }
        updateFilterUI()
    }

    private fun toggleFilter(type: String) {
        if (type == "All") {
            if ("All" !in selectedMeterTypesFilter) {
                selectedMeterTypesFilter.clear()
                selectedMeterTypesFilter.add("All")
            } else if (selectedMeterTypesFilter.size == 1 && "All" in selectedMeterTypesFilter) {
                return
            } else {
                selectedMeterTypesFilter.remove("All")
            }
        } else {
            if (type in selectedMeterTypesFilter) {
                selectedMeterTypesFilter.remove("All")
                selectedMeterTypesFilter.remove(type) // Remove the specific type if already selected
                if (selectedMeterTypesFilter.isEmpty()) { // If no types selected, default to All
                    selectedMeterTypesFilter.add("All")
                }
            } else {
                selectedMeterTypesFilter.remove("All") // If selecting a specific type, remove "All"
                selectedMeterTypesFilter.add(type)
            }
        }
        updateFilterUI()
        locationViewModel.meters.value?.let { currentMeters ->
            applyMeterFilter(currentMeters)
        }
    }

    private fun updateFilterUI() {
        filterTextViews.forEach { (type, textView) ->
            val isSelected = type in selectedMeterTypesFilter
            val colorResId = when (type) {
                "Electricity" -> R.color.electric_blue
                "Heat" -> R.color.heat_orange
                "Gas" -> R.color.gas_green
                else -> R.color.black
            }
            val accentColor = if (type == "All") {
                if (isSelected) ContextCompat.getColor(this, R.color.black) else Color.TRANSPARENT
            } else {
                ContextCompat.getColor(this, colorResId)
            }

            if (isSelected) {
                textView.setBackgroundResource(R.drawable.filter_button_selected_background)
                val drawable = textView.background as GradientDrawable
                drawable.setColor(accentColor)
                drawable.setStroke(2, accentColor)

                textView.setTextColor(ContextCompat.getColor(this, R.color.white))
                textView.setTypeface(null, Typeface.BOLD)
            } else {
                textView.setBackgroundResource(R.drawable.filter_button_background)
                textView.setTextColor(ContextCompat.getColor(this, R.color.black))
                textView.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun setupSendButton() {
        binding.sendReadingsFab.setOnClickListener {
            sendAllMeterReadingsAndPictures()
        }
    }

    private fun observeProjects() {
        locationViewModel.projects.observe(this) { projects ->
            projects?.let {
                binding.loadingProgressBar.isVisible = false
                if (it.isEmpty()) {
                    if (locationViewModel.projectSearchQuery.value.isEmpty()) {
                        binding.noDataTextView.text = getString(R.string.no_projects_found)
                        binding.noDataTextView.isVisible = true
                    } else {
                        binding.noDataTextView.text = getString(R.string.no_matching_projects_found, locationViewModel.projectSearchQuery.value)
                        binding.noDataTextView.isVisible = true
                    }
                } else {
                    binding.noDataTextView.isVisible = false
                    projectAdapter.submitList(it)
                }
            } ?: run {
                binding.loadingProgressBar.isVisible = false
                binding.noDataTextView.text = getString(R.string.no_projects_found) // Default error
                binding.noDataTextView.isVisible = true
            }
        }
    }


    private fun observeLocations() {
        locationViewModel.locations.observe(this) { locations ->
            locations?.let {
                binding.loadingProgressBar.isVisible = false
                if (it.isEmpty() && locationViewModel.selectedProjectId.value != null) {
                    binding.noDataTextView.text = getString(R.string.no_locations_for_project)
                    binding.noDataTextView.isVisible = true
                    locationAdapter.submitList(emptyList()) // Clear list if no locations
                } else if (it.isNotEmpty()) {
                    binding.noDataTextView.isVisible = false
                    locationAdapter.submitList(it)
                }
            } ?: run {
                binding.loadingProgressBar.isVisible = false
                binding.noDataTextView.text = getString(R.string.failed_to_load_locations)
                binding.noDataTextView.isVisible = true
                locationAdapter.submitList(emptyList())
            }
        }
    }

    private fun observeMeters() {
        locationViewModel.meters.observe(this) { meters ->
            meters?.let {
                binding.loadingProgressBar.isVisible = false
                if (it.isEmpty() && locationViewModel.selectedLocationId.value != null) {
                    binding.noDataTextView.text = getString(R.string.no_meters_for_location)
                    binding.noDataTextView.isVisible = true
                    meterAdapter.submitList(emptyList())
                } else if (it.isNotEmpty()) {
                    binding.noDataTextView.isVisible = false
                    applyMeterFilter(it)
                }
            } ?: run {
                binding.loadingProgressBar.isVisible = false
                binding.noDataTextView.text = getString(R.string.failed_to_load_meters)
                binding.noDataTextView.isVisible = true
                meterAdapter.submitList(emptyList())
            }
        }
    }

    // REMOVED: observeFileMetadata() // No longer needed

    private fun applyMeterFilter(meters: List<Meter>) {
        val filteredMeters = if ("All" in selectedMeterTypesFilter) {
            meters
        } else {
            meters.filter { meter ->
                selectedMeterTypesFilter.any { selectedType ->
                    meter.energy_type.equals(selectedType, ignoreCase = true)
                }
            }
        }
        meterAdapter.submitList(filteredMeters)
    }

    private fun sendAllMeterReadingsAndPictures() {
        val readingsToSend = mutableListOf<Reading>()
        val enteredValues = meterAdapter.getEnteredReadings()
        val readingDateString = apiDateFormat.format(selectedReadingDate.time)

        for (meter in meterAdapter.currentList) {
            val readingValue = enteredValues[meter.id]
            if (!readingValue.isNullOrBlank()) {
                val newReading = Reading(
                    id = java.util.UUID.randomUUID().toString(),
                    meter_id = meter.id,
                    value = readingValue,
                    date = readingDateString,
                    created_by = null,
                    read_by = "App User"
                )
                readingsToSend.add(newReading)
            }
        }

        val imagesToUpload = meterAdapter.getMeterImages()
        val imagesQueuedCount = imagesToUpload.size

        if (readingsToSend.isEmpty() && imagesToUpload.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_readings_or_pictures_entered), Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_send_data_title))
            .setMessage(getString(R.string.confirm_send_data_message, readingsToSend.size, imagesToUpload.size, uiDateFormat.format(selectedReadingDate.time)))
            .setPositiveButton(getString(R.string.send_button_text)) { dialog, _ ->
                readingsToSend.forEach { reading ->
                    locationViewModel.postMeterReading(reading)
                }

                imagesToUpload.forEach { (meterId, imageUri) ->
                    val meter = meterAdapter.currentList.find { it.id == meterId }
                    if (meter != null) {
                        val projectId = meter.project_id ?: "unknown_project"
                        val currentTime = timeFormat.format(Date())
                        val fileName = "${s3KeyDateFormat.format(selectedReadingDate.time)}_${currentTime}_${meter.number.replace("/", "_").replace(".", "_")}.jpg"
                        val fullStoragePath = "meter-documents/meter/${meter.id}/${fileName}"
                        locationViewModel.queueImageUpload(imageUri, fullStoragePath, projectId, meter.id)
                    } else {
                        Log.e("MainActivity", "Meter not found for image with ID: $meterId. Skipping upload.")
                    }
                }

                meterAdapter.clearEnteredReadings()
                meterAdapter.clearMeterImages() // This will now only clear locally taken images

                Toast.makeText(this, getString(R.string.data_sent_queued, readingsToSend.size, imagesQueuedCount), Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button_text)) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun observeUiMessages() {
        lifecycleScope.launch {
            locationViewModel.uiMessage.collectLatest { message ->
                if (!message.isNullOrBlank()) {
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun performLogout() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_REMEMBER_ME, false)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
        }

        finish()
    }

    private fun updateToolbarForProjects() {
        binding.toolbarTitle.text = getString(R.string.app_name)
        binding.searchView.queryHint = getString(R.string.search_project_hint)
        binding.toolbarTitle.isVisible = true
        binding.searchView.isVisible = true
        binding.meterSearchView.isVisible = false
        binding.dateSelectionLayout.isVisible = false
        binding.filterButtonsLayout.isVisible = false
    }

    private fun updateToolbarForLocations(projectId: String?) {
        val projectName = projectId?.let {
            locationViewModel.projects.value?.find { p -> p.id == it }?.name
        } ?: getString(R.string.app_name)
        binding.toolbarTitle.text = projectName
        binding.searchView.queryHint = getString(R.string.search_location_hint)
        binding.toolbarTitle.isVisible = true
        binding.searchView.isVisible = true
        binding.meterSearchView.isVisible = false
        binding.dateSelectionLayout.isVisible = false
        binding.filterButtonsLayout.isVisible = false
    }

    private fun updateToolbarForMeters(location: Location) {
        binding.toolbarTitle.text = location.name ?: location.address ?: getString(R.string.app_name)
        binding.searchView.isVisible = false
        binding.meterSearchView.isVisible = true
        binding.dateSelectionLayout.isVisible = true
        binding.filterButtonsLayout.isVisible = true
        binding.toolbarTitle.isVisible = true
    }

    override fun onResume() {
        super.onResume()
        // Determine current view and set toolbar/search hints accordingly
        when {
            binding.projectsContainer.isVisible -> updateToolbarForProjects()
            binding.locationsContainer.isVisible -> updateToolbarForLocations(locationViewModel.selectedProjectId.value)
            binding.metersContainer.isVisible -> locationViewModel.selectedLocationId.value?.let { locationId ->
                locationViewModel.locations.value?.find { it.id == locationId }?.let { location ->
                    updateToolbarForMeters(location)
                }
            }
            else -> updateToolbarForProjects() // Default to projects view if no state is active (e.g., initial launch)
        }
    }

    // Enum to represent the current mode of the app (Readings or Editing)
    enum class AppMode {
        READINGS,
        EDITING
    }
}
