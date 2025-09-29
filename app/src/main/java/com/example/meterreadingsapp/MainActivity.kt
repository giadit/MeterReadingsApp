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
import com.example.meterreadingsapp.adapter.BuildingAdapter
import com.example.meterreadingsapp.adapter.MeterAdapter
import com.example.meterreadingsapp.adapter.ProjectAdapter
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.*
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
import java.util.*
import android.content.pm.PackageManager
import android.widget.EditText
import androidx.core.content.edit
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var projectAdapter: ProjectAdapter
    private lateinit var buildingAdapter: BuildingAdapter
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
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
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
                        Toast.makeText(this, "Picture saved: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Picture capture failed", Toast.LENGTH_SHORT).show()
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

        val apiService = RetrofitClient.getService(ApiService::class.java, applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val projectDao = database.projectDao()
        val buildingDao = database.buildingDao()
        val meterDao = database.meterDao()
        val readingDao = database.readingDao()
        val queuedRequestDao = database.queuedRequestDao()
        val locationDao = database.locationDao()

        val repository = MeterRepository(
            apiService, meterDao, readingDao, projectDao, buildingDao,
            queuedRequestDao, locationDao, applicationContext
        )

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupProjectRecyclerView()
        setupBuildingRecyclerView()
        setupMeterRecyclerView()
        setupSearchView()
        setupDateSelection()
        setupTypeFilter()
        setupSendButton()
        setupMeterSearchView()

        observeProjects()
        observeBuildings()
        observeMeters()
        observeUiMessages()

        binding.projectsContainer.isVisible = true
        binding.locationsContainer.isVisible = false
        binding.metersContainer.isVisible = false
        binding.sendReadingsFab.isVisible = false
        binding.backButton.isVisible = false

        binding.refreshButton.setOnClickListener {
            locationViewModel.refreshAllData()
            Log.d("MainActivity", "Refreshing all data...")
        }

        binding.backButton.setOnClickListener {
            when {
                binding.metersContainer.isVisible -> {
                    locationViewModel.selectBuilding(null)
                    binding.metersContainer.isVisible = false
                    binding.locationsContainer.isVisible = true
                    binding.sendReadingsFab.isVisible = false
                    binding.backButton.isVisible = true

                    binding.meterSearchView.setQuery("", false)
                    locationViewModel.setMeterSearchQuery("")
                    updateToolbarForBuildings(locationViewModel.selectedProjectId.value)
                }
                binding.locationsContainer.isVisible -> {
                    locationViewModel.selectProject(null)
                    binding.locationsContainer.isVisible = false
                    binding.projectsContainer.isVisible = true
                    binding.backButton.isVisible = false
                    binding.sendReadingsFab.isVisible = false

                    binding.searchView.setQuery("", false)
                    binding.searchView.isIconified = true
                    locationViewModel.setProjectSearchQuery("")
                    binding.meterSearchView.setQuery("", false)
                    locationViewModel.setMeterSearchQuery("")
                    updateToolbarForProjects()
                }
            }
            binding.noDataTextView.isVisible = false
        }

        binding.logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun setupProjectRecyclerView() {
        projectAdapter = ProjectAdapter { project ->
            locationViewModel.selectProject(project)
            binding.projectsContainer.isVisible = false
            binding.locationsContainer.isVisible = true
            binding.backButton.isVisible = true
            updateToolbarForBuildings(project.id)
        }
        binding.projectsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = projectAdapter
        }
    }

    private fun setupBuildingRecyclerView() {
        buildingAdapter = BuildingAdapter { building ->
            locationViewModel.selectBuilding(building)
            binding.locationsContainer.isVisible = false
            binding.metersContainer.isVisible = true
            binding.sendReadingsFab.isVisible = true
            binding.backButton.isVisible = true
            updateToolbarForMeters(building)
        }
        binding.locationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = buildingAdapter
        }
    }

    private fun setupMeterRecyclerView() {
        meterAdapter = MeterAdapter(
            onCameraClicked = { meter, _ ->
                currentMeterForPhoto = meter
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera(meter)
                } else {
                    requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            },
            onViewImageClicked = { _, imageUri ->
                viewImage(imageUri)
            },
            onDeleteImageClicked = { meter, imageUri ->
                confirmAndDeleteImage(meter, imageUri)
            },
            onEditMeterClicked = { meter ->
                Toast.makeText(this, "Edit meter: ${meter.number}", Toast.LENGTH_SHORT).show()
            },
            onDeleteMeterClicked = { meter ->
                Toast.makeText(this, "Delete meter: ${meter.number}", Toast.LENGTH_SHORT).show()
            },
            currentMode = { AppMode.READINGS }
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
                    locationViewModel.setBuildingSearchQuery(newText ?: "")
                }
                return true
            }
        })

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
            Toast.makeText(this, "Error creating image file.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Error viewing image.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmAndDeleteImage(meter: Meter, imageUri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Image?")
            .setMessage("Are you sure you want to delete the image for meter ${meter.number}?")
            .setPositiveButton("Delete") { dialog, _ ->
                imageUri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) {
                            meterAdapter.removeMeterImageUri(meter.id)
                            Toast.makeText(this, "Image deleted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to delete image file.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Image file not found.", Toast.LENGTH_SHORT).show()
                        meterAdapter.removeMeterImageUri(meter.id)
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
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
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
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
            }
        } else {
            selectedMeterTypesFilter.remove("All")
            if (type in selectedMeterTypesFilter) {
                selectedMeterTypesFilter.remove(type)
            } else {
                selectedMeterTypesFilter.add(type)
            }
            if (selectedMeterTypesFilter.isEmpty()) {
                selectedMeterTypesFilter.add("All")
            }
        }
        updateFilterUI()
        locationViewModel.meters.value?.let { applyMeterFilter(it) }
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
                (textView.background as GradientDrawable).apply {
                    setColor(accentColor)
                    setStroke(2, accentColor)
                }
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
            binding.loadingProgressBar.isVisible = false
            binding.noDataTextView.isVisible = projects.isNullOrEmpty()
            if (projects.isNullOrEmpty()) {
                binding.noDataTextView.text = "No projects found."
            } else {
                projectAdapter.submitList(projects)
            }
        }
    }

    private fun observeBuildings() {
        locationViewModel.buildings.observe(this) { buildings ->
            binding.loadingProgressBar.isVisible = false
            binding.noDataTextView.isVisible = buildings.isNullOrEmpty()
            if (buildings.isNullOrEmpty()) {
                binding.noDataTextView.text = "No buildings found for this project."
                buildingAdapter.submitList(emptyList())
            } else {
                buildingAdapter.submitList(buildings)
            }
        }
    }

    private fun observeMeters() {
        locationViewModel.meters.observe(this) { meters ->
            binding.loadingProgressBar.isVisible = false
            binding.noDataTextView.isVisible = meters.isNullOrEmpty()
            if (meters.isNullOrEmpty()) {
                binding.noDataTextView.text = "No meters found for this building."
                meterAdapter.submitList(emptyList())
            } else {
                applyMeterFilter(meters)
            }
        }
    }

    private fun applyMeterFilter(meters: List<Meter>) {
        val filteredMeters = if ("All" in selectedMeterTypesFilter) {
            meters
        } else {
            meters.filter { meter ->
                selectedMeterTypesFilter.any { it.equals(meter.energyType, ignoreCase = true) } // CORRECTED
            }
        }
        meterAdapter.submitList(filteredMeters)
    }

    private fun sendAllMeterReadingsAndPictures() {
        val readingsToSend = mutableListOf<Reading>()
        val enteredValues = meterAdapter.getEnteredReadings()
        val readingDateString = apiDateFormat.format(selectedReadingDate.time)

        meterAdapter.currentList.forEach { meter ->
            enteredValues[meter.id]?.takeIf { it.isNotBlank() }?.let { readingValue ->
                readingsToSend.add(
                    Reading(
                        id = UUID.randomUUID().toString(),
                        meter_id = meter.id,
                        value = readingValue,
                        date = readingDateString,
                        read_by = "App User"
                    )
                )
            }
        }

        val imagesToUpload = meterAdapter.getMeterImages()
        if (readingsToSend.isEmpty() && imagesToUpload.isEmpty()) {
            Toast.makeText(this, "No readings or pictures entered.", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Send Data")
            .setMessage("Send ${readingsToSend.size} readings and ${imagesToUpload.size} pictures for date ${uiDateFormat.format(selectedReadingDate.time)}?")
            .setPositiveButton("Send") { dialog, _ ->
                readingsToSend.forEach { locationViewModel.postMeterReading(it) }

                imagesToUpload.forEach { (meterId, imageUri) ->
                    val meter = meterAdapter.currentList.find { it.id == meterId }
                    if (meter != null) {
                        val projectId = meter.projectId ?: "unknown_project" // CORRECTED
                        val currentTime = timeFormat.format(Date())
                        val fileName = "${s3KeyDateFormat.format(selectedReadingDate.time)}_${currentTime}_${meter.number.replace("/", "_").replace(".", "_")}.jpg"
                        val fullStoragePath = "meter-documents/meter/${meter.id}/$fileName"
                        locationViewModel.queueImageUpload(imageUri, fullStoragePath, projectId, meter.id)
                    }
                }

                meterAdapter.clearEnteredReadings()
                meterAdapter.clearMeterImages()

                Toast.makeText(this, "${readingsToSend.size} readings and ${imagesToUpload.size} pictures have been queued for sending.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeUiMessages() {
        lifecycleScope.launch {
            locationViewModel.uiMessage.collectLatest { message ->
                message?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
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
        val sessionManager = com.example.meterreadingsapp.api.SessionManager(this)
        sessionManager.clearAuthToken()

        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun updateToolbarForProjects() {
        binding.toolbarTitle.text = "Projects"
        binding.searchView.queryHint = "Search Projects..."
        binding.toolbarTitle.isVisible = true
        binding.searchView.isVisible = true
        binding.meterSearchView.isVisible = false
        binding.dateSelectionLayout.isVisible = false
        binding.filterButtonsLayout.isVisible = false
    }

    private fun updateToolbarForBuildings(projectId: String?) {
        val projectName = locationViewModel.projects.value?.find { it.id == projectId }?.name ?: "Buildings"
        binding.toolbarTitle.text = projectName
        binding.searchView.queryHint = "Search Buildings..."
        binding.toolbarTitle.isVisible = true
        binding.searchView.isVisible = true
        binding.meterSearchView.isVisible = false
        binding.dateSelectionLayout.isVisible = false
        binding.filterButtonsLayout.isVisible = false
    }

    private fun updateToolbarForMeters(building: Building) {
        binding.toolbarTitle.text = building.name
        binding.searchView.isVisible = false
        binding.meterSearchView.isVisible = true
        binding.dateSelectionLayout.isVisible = true
        binding.filterButtonsLayout.isVisible = true
        binding.toolbarTitle.isVisible = true
    }

    override fun onResume() {
        super.onResume()
        when {
            binding.projectsContainer.isVisible -> updateToolbarForProjects()
            binding.locationsContainer.isVisible -> updateToolbarForBuildings(locationViewModel.selectedProjectId.value)
            binding.metersContainer.isVisible -> locationViewModel.selectedBuildingId.value?.let { buildingId ->
                locationViewModel.buildings.value?.find { it.id == buildingId }?.let { building ->
                    updateToolbarForMeters(building)
                }
            }
            else -> updateToolbarForProjects()
        }
    }

    enum class AppMode {
        READINGS,
        EDITING
    }
}

