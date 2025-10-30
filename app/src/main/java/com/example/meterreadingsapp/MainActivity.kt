package com.example.meterreadingsapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meterreadingsapp.adapter.BuildingAdapter
import com.example.meterreadingsapp.adapter.MeterAdapter
import com.example.meterreadingsapp.adapter.ProjectAdapter
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.* // Ensure all DAOs and Entities are imported
import com.example.meterreadingsapp.databinding.ActivityMainBinding
import com.example.meterreadingsapp.databinding.DialogAddMeterBinding
import com.example.meterreadingsapp.databinding.DialogChangeMeterBinding
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var projectAdapter: ProjectAdapter
    private lateinit var buildingAdapter: BuildingAdapter
    private lateinit var meterAdapter: MeterAdapter

    private val uiDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val s3KeyDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timeFormat = SimpleDateFormat("HHmm", Locale.US)

    private var selectedReadingDate: Calendar = Calendar.getInstance()
    private val selectedMeterTypesFilter: MutableSet<String> = mutableSetOf("All")
    private var showExchangedMeters = false

    private lateinit var filterTextViews: Map<String, TextView>

    private val PREFS_NAME = "LoginPrefs"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"
    private val KEY_REMEMBER_ME = "rememberMe"

    private var currentPhotoUri: Uri? = null
    private var currentMeterForPhoto: Meter? = null
    // UPDATED: Add a variable to store the specific OBIS key for the photo
    private var currentObisKeyForPhoto: String? = null
    // UPDATED: Add a variable to store the OBIS code string (e.g., "1.8.0")
    private var currentObisCodeForPhoto: String? = null

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // UPDATED: Check for all properties
                if (currentMeterForPhoto != null && currentObisKeyForPhoto != null) {
                    // UPDATED: Pass the code string to launchCamera
                    launchCamera(currentMeterForPhoto!!, currentObisCodeForPhoto)
                }
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
                currentMeterForPhoto = null
                currentPhotoUri = null
                currentObisKeyForPhoto = null // UPDATED
                currentObisCodeForPhoto = null // UPDATED
            }
        }

    private val takePictureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // UPDATED: Check for all three properties
                if (currentPhotoUri != null && currentMeterForPhoto != null && currentObisKeyForPhoto != null) {
                    // UPDATED: Use the obisKey (UUID) to update the adapter's map
                    meterAdapter.updateMeterImageUri(currentMeterForPhoto!!.id, currentObisKeyForPhoto!!, currentPhotoUri!!)
                    Toast.makeText(this, "Picture saved: ${currentPhotoUri!!.lastPathSegment}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Picture capture failed", Toast.LENGTH_SHORT).show()
            }
            // UPDATED: Always clear all properties
            currentPhotoUri = null
            currentMeterForPhoto = null
            currentObisKeyForPhoto = null
            currentObisCodeForPhoto = null // UPDATED
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
        // START NEW DAO DECLARATIONS
        val obisCodeDao = database.obisCodeDao()
        val meterObisDao = database.meterObisDao()
        // END NEW DAO DECLARATIONS

        val repository = MeterRepository(
            apiService,
            meterDao,
            readingDao,
            projectDao,
            buildingDao,
            queuedRequestDao,
            // START REPOSITORY CONSTRUCTOR UPDATE
            obisCodeDao,
            meterObisDao,
            // END REPOSITORY CONSTRUCTOR UPDATE
            applicationContext
        )

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupProjectRecyclerView()
        setupBuildingRecyclerView()
        setupMeterRecyclerView()
        setupSearchView()
        setupDateSelection()
        setupSendButton()
        setupTypeFilter()
        setupMeterSearchView()

        observeProjects()
        observeBuildings()
        observeMeters()
        observeUiMessages()

        // ADDED: Observe OBIS codes and pass them to the adapter
        locationViewModel.allObisCodes.observe(this) { codes ->
            if (codes != null) {
                meterAdapter.setObisCodes(codes)
            }
        }

        locationViewModel.refreshAllData()

        binding.projectsContainer.isVisible = true
        binding.locationsContainer.isVisible = false
        binding.metersContainer.isVisible = false
        binding.bottomBar.isVisible = false
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
                    binding.bottomBar.isVisible = false
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
                    binding.bottomBar.isVisible = false
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

        binding.bottomBarAddMeterButton.setOnClickListener {
            showAddMeterDialog()
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
            binding.bottomBar.isVisible = true
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
            // UPDATED: Adapter now sends (meter, obisKey, obisCodeString, currentUri)
            onCameraClicked = { meter, obisKey, obisCodeString, _ ->
                currentMeterForPhoto = meter
                currentObisKeyForPhoto = obisKey // UPDATED: Store the key (UUID)
                currentObisCodeForPhoto = obisCodeString // UPDATED: Store the code string ("1.8.0")
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera(meter, obisCodeString) // UPDATED: Pass code string
                } else {
                    requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            },
            // UPDATED: Adapter now sends (meter, obisKey, uri)
            onViewImageClicked = { _, _, imageUri -> viewImage(imageUri) }, // obisKey not needed here
            // UPDATED: Adapter now sends (meter, obisKey, obisCodeString, uri)
            onDeleteImageClicked = { meter, obisKey, obisCodeString, imageUri -> confirmAndDeleteImage(meter, obisKey, obisCodeString, imageUri) },
            onEditMeterClicked = { meter -> Toast.makeText(this, "Edit meter: ${meter.number}", Toast.LENGTH_SHORT).show() },
            onDeleteMeterClicked = { meter -> Toast.makeText(this, "Delete meter: ${meter.number}", Toast.LENGTH_SHORT).show() },
            onExchangeMeterClicked = { meter -> showChangeMeterDialog(meter) },
            currentMode = { AppMode.READINGS }
        )
        binding.metersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = meterAdapter
        }
    }

    private fun showChangeMeterDialog(oldMeter: Meter) {
        val dialogBinding = DialogChangeMeterBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Bestätigen", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setBackgroundColor(ContextCompat.getColor(this, R.color.bright_orange))
            positiveButton.setTextColor(Color.WHITE)

            positiveButton.setOnClickListener {
                val oldReadingValue = dialogBinding.oldMeterLastReading.text.toString()
                val newMeterNumber = dialogBinding.newMeterNumber.text.toString()
                val newReadingValue = dialogBinding.newMeterInitialReading.text.toString()
                val datePicker = dialogBinding.oldMeterLastReadingDate
                val calendar = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                }
                val oldReadingDate = apiDateFormat.format(calendar.time)

                if (oldReadingValue.isBlank() || newMeterNumber.isBlank() || newReadingValue.isBlank()) {
                    Toast.makeText(this, "Bitte alle Felder ausfüllen.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val oldMeterLastReading = Reading(
                    id = UUID.randomUUID().toString(),
                    meter_id = oldMeter.id,
                    value = oldReadingValue,
                    date = oldReadingDate,
                    read_by = "App User",
                    meterObisId = null, // This is a simple reading
                    migrationStatus = null
                )
                val newMeterInitialReading = Reading(
                    id = UUID.randomUUID().toString(),
                    meter_id = "",
                    value = newReadingValue,
                    date = oldReadingDate,
                    read_by = "App User",
                    meterObisId = null, // This is a simple reading
                    migrationStatus = null
                )

                locationViewModel.exchangeMeter(
                    oldMeter,
                    oldMeterLastReading,
                    newMeterNumber,
                    newMeterInitialReading
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showAddMeterDialog() {
        val currentProjectId = locationViewModel.selectedProjectId.value
        val currentBuilding = locationViewModel.buildings.value?.find { it.id == locationViewModel.selectedBuildingId.value }

        if (currentProjectId == null || currentBuilding == null) {
            Toast.makeText(this, "Error: No project or building selected.", Toast.LENGTH_LONG).show()
            return
        }

        val dialogBinding = DialogAddMeterBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Erstellen", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        val energyTypes = listOf("Strom", "Wärme", "Gas")
        val meterTypes = listOf("Summenzähler","Wohnung","Gewerbe", "Hausstrom", "Unterzähler",
            "Erzeugungszähler PV", "Eigenbedarf PV", "Eigenbedarf KWK", "Erzeugungszähler KWK",
            "BEA Eigenbedarf", "Elektromobilität", "Zwischenzähler","Abgrenzungszähler","Baustrom",
            "Balkon-PV Einspeisung", "Eigenbedarf WP", "unbekannt")

        val energyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, energyTypes)
        val meterAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, meterTypes)

        energyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        meterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        dialogBinding.spinnerEnergyType.adapter = energyAdapter
        dialogBinding.spinnerMeterType.adapter = meterAdapter

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setBackgroundColor(ContextCompat.getColor(this, R.color.bright_orange))
            positiveButton.setTextColor(Color.WHITE)

            positiveButton.setOnClickListener {
                val newMeterNumber = dialogBinding.newMeterNumber.text.toString()
                val initialReadingValue = dialogBinding.newMeterInitialReading.text.toString()
                val datePicker = dialogBinding.newMeterReadingDate
                val calendar = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                }
                val readingDate = apiDateFormat.format(calendar.time)
                val energyType = dialogBinding.spinnerEnergyType.selectedItem.toString()
                val meterType = dialogBinding.spinnerMeterType.selectedItem.toString()

                if (newMeterNumber.isBlank() || initialReadingValue.isBlank()) {
                    Toast.makeText(this, "Bitte alle Felder ausfüllen.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newMeterRequest = NewMeterRequest(
                    number = newMeterNumber,
                    projectId = currentProjectId,
                    buildingId = currentBuilding.id,
                    energyType = energyType,
                    type = meterType,
                    replacedOldMeterId = null,
                    street = currentBuilding.street,
                    postalCode = currentBuilding.postal_code,
                    city = currentBuilding.city,
                    houseNumber = currentBuilding.house_number,
                    houseNumberAddition = currentBuilding.house_number_addition
                )

                val initialReading = Reading(
                    id = UUID.randomUUID().toString(),
                    meter_id = "",
                    value = initialReadingValue,
                    date = readingDate,
                    read_by = "App User",
                    meterObisId = null, // This is a simple reading
                    migrationStatus = null
                )

                locationViewModel.addNewMeter(newMeterRequest, initialReading)
                dialog.dismiss()
            }
        }
        dialog.show()
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
            override fun onQueryTextSubmit(query: String?): Boolean { return false }
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

    // UPDATED: Signature now requires obisCode string
    private fun launchCamera(meter: Meter, obisCode: String?) {
        currentPhotoUri = createImageFileForMeter(meter, obisCode) // UPDATED
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
    // UPDATED: Signature now requires obisCode string
    private fun createImageFileForMeter(meter: Meter, obisCode: String?): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // UPDATED: Make filename unique using obisCode string
        val safeObisKey = obisCode?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "reading" // Allow dots in code
        val imageFileName = "${timeStamp}_${meter.number.replace("/", "_").replace(".", "_")}_$safeObisKey"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        return FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", imageFile)
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

    // UPDATED: Signature now requires obisKey (UUID) and obisCode (string)
    private fun confirmAndDeleteImage(meter: Meter, obisKey: String, obisCode: String?, imageUri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Image?")
            // UPDATED: Show the human-readable obisCode in the message
            .setMessage("Are you sure you want to delete the image for meter ${meter.number} (Reading: ${obisCode ?: "N/A"})?")
            .setPositiveButton("Delete") { dialog, _ ->
                imageUri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) {
                            meterAdapter.removeMeterImageUri(meter.id, obisKey) // UPDATED: Use obisKey (UUID)
                            Toast.makeText(this, "Image deleted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to delete image file.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "Image file not found.", Toast.LENGTH_SHORT).show()
                        meterAdapter.removeMeterImageUri(meter.id, obisKey) // UPDATED: Use obisKey (UUID)
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
        binding.bottomBarCalendarButton.setOnClickListener { showDatePicker() }
    }

    private fun updateSelectedDateText() {
        binding.toolbarDateTextView.text = uiDateFormat.format(selectedReadingDate.time)
    }

    private fun showDatePicker() {
        val year = selectedReadingDate.get(Calendar.YEAR)
        val month = selectedReadingDate.get(Calendar.MONTH)
        val day = selectedReadingDate.get(Calendar.DAY_OF_MONTH)
        val datePickerDialog = DatePickerDialog(
            this, R.style.AppDatePickerDialogTheme,
            { _, selectedYear, selectedMonth, selectedDayOfMonth ->
                selectedReadingDate.set(selectedYear, selectedMonth, selectedDayOfMonth)
                updateSelectedDateText()
            }, year, month, day
        )
        datePickerDialog.show()
        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setBackgroundResource(R.drawable.button_orange_background)
        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.setTextColor(Color.WHITE)
        datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.setTextColor(ContextCompat.getColor(this, R.color.bright_orange))
    }

    private fun setupTypeFilter() {
        filterTextViews = mapOf(
            "All" to binding.filterAll,
            "Strom" to binding.filterElectricity,
            "Wärme" to binding.filterHeat,
            "Gas" to binding.filterGas,
            "getauscht" to binding.filterGetauscht
        )
        filterTextViews.forEach { (type, textView) ->
            textView.setOnClickListener {
                toggleFilter(type)
            }
        }
        updateFilterUI()
    }

    private fun toggleFilter(type: String) {
        if (type == "getauscht") {
            showExchangedMeters = !showExchangedMeters
        } else {
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
        }
        updateFilterUI()
        // UPDATED: Check for null before filtering
        locationViewModel.meters.value?.let { applyMeterFilter(it) }
    }

    private fun updateFilterUI() {
        filterTextViews.forEach { (type, textView) ->
            val isSelected = if (type == "getauscht") {
                showExchangedMeters
            } else {
                type in selectedMeterTypesFilter
            }
            val colorResId = when (type) {
                "Strom" -> R.color.electric_blue
                "Wärme" -> R.color.heat_orange
                "Gas" -> R.color.gas_green
                "getauscht" -> android.R.color.holo_red_dark
                else -> R.color.black
            }
            val accentColor = ContextCompat.getColor(this, colorResId)
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
        binding.bottomBarSendButton.setOnClickListener {
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
        // The 'meters' list is now of type List<MeterWithObisPoints>
        locationViewModel.meters.observe(this) { meters ->
            binding.loadingProgressBar.isVisible = false
            if (meters != null) {
                applyMeterFilter(meters) // Pass the new list type
            } else {
                binding.noDataTextView.text = "No meters found for this building."
                binding.noDataTextView.isVisible = true
                meterAdapter.submitList(emptyList())
            }
        }
    }

    // UPDATED: Change parameter type to List<MeterWithObisPoints>
    private fun applyMeterFilter(meters: List<MeterWithObisPoints>) {
        val statusFilteredMeters = if (showExchangedMeters) {
            // Access the nested 'meter' property
            meters.filter { it.meter.status.equals("Exchanged", ignoreCase = true) }
        } else {
            // Access the nested 'meter' property
            meters.filter { it.meter.status.equals("Valid", ignoreCase = true) }
        }
        val finalFilteredMeters = if ("All" in selectedMeterTypesFilter) {
            statusFilteredMeters
        } else {
            statusFilteredMeters.filter { meterWithObis ->
                selectedMeterTypesFilter.any {
                    // Access the nested 'meter' property
                    it.equals(meterWithObis.meter.energyType, ignoreCase = true)
                }
            }
        }
        meterAdapter.submitList(finalFilteredMeters)
        binding.noDataTextView.isVisible = finalFilteredMeters.isEmpty()
        if (finalFilteredMeters.isEmpty()) {
            binding.noDataTextView.text = "No meters match the current filters."
        }
    }

    private fun sendAllMeterReadingsAndPictures() {
        val readingsToSend = mutableListOf<Reading>()
        // UPDATED: Get the new nested map structure from the adapter
        val enteredValues = meterAdapter.getEnteredReadings()
        val readingDateString = apiDateFormat.format(selectedReadingDate.time)
        // UPDATED: Get the List<MeterWithObisPoints>
        val allMetersWithObis = locationViewModel.meters.value ?: emptyList()

        // --- VALIDATION LOGIC (UPDATED) ---
        var invalidInputFound = false
        for ((meterId, readingsMap) in enteredValues) {
            for ((_, readingValue) in readingsMap) {
                if (readingValue.isNotBlank()) {
                    if (readingValue == "." || readingValue.startsWith(".") || readingValue.endsWith(".")) {
                        // UPDATED: Access meter number correctly
                        val meterNumber = allMetersWithObis.find { it.meter.id == meterId }?.meter?.number ?: "Unknown"
                        Toast.makeText(this, "Ungültige Eingabe für Zähler $meterNumber: '$readingValue'", Toast.LENGTH_LONG).show()
                        invalidInputFound = true
                        break // Stop checking this meter
                    }
                }
            }
            if (invalidInputFound) break // Stop checking all meters
        }
        if (invalidInputFound) return // Stop the sending process
        // --- END VALIDATION ---

        // UPDATED: Loop through the new nested map structure
        enteredValues.forEach { (meterId, readingsMap) ->
            readingsMap.forEach { (obisOrKey, readingValue) ->
                if (readingValue.isNotBlank()) {
                    // Check if the key is our special key for simple readings
                    val meterObisId = if (obisOrKey == MeterAdapter.SINGLE_READING_KEY) {
                        null // This is a simple reading
                    } else {
                        obisOrKey // This is a meter_obis_id (the UUID)
                    }

                    readingsToSend.add(
                        Reading(
                            id = UUID.randomUUID().toString(),
                            meter_id = meterId,
                            value = readingValue,
                            date = readingDateString,
                            read_by = "App User",
                            meterObisId = meterObisId, // <-- CRUCIAL CHANGE
                            migrationStatus = null
                        )
                    )
                }
            }
        }

        // UPDATED: Get the new nested map structure
        val imagesToUpload = meterAdapter.getMeterImages()
        // UPDATED: Calculate total number of images
        val totalImageCount = imagesToUpload.values.sumOf { it.size }

        if (readingsToSend.isEmpty() && totalImageCount == 0) {
            Toast.makeText(this, "No readings or pictures entered.", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Daten Senden Bestätigen")
            // UPDATED: Show correct image count
            .setMessage("Sollen ${readingsToSend.size} Zählerstände und $totalImageCount Bilder für das Datum ${uiDateFormat.format(selectedReadingDate.time)} gesendet werden?")
            .setPositiveButton("Senden") { dialog, _ ->
                readingsToSend.forEach { locationViewModel.postMeterReading(it) }

                // UPDATED: Loop through the new nested map structure
                imagesToUpload.forEach { (meterId, obisMap) ->
                    // UPDATED: Loop through inner map (obisKey is the UUID)
                    obisMap.forEach { (obisKey, imageUri) ->
                        // UPDATED: Access the nested 'meter' property
                        val meter = allMetersWithObis.find { it.meter.id == meterId }?.meter
                        if (meter != null) {
                            val projectId = meter.projectId
                            val currentTime = timeFormat.format(Date())

                            // UPDATED: Create a unique name including the OBIS code string
                            val obisSuffix: String
                            if (obisKey == MeterAdapter.SINGLE_READING_KEY) {
                                obisSuffix = "main"
                            } else {
                                // Find the obisCode.code from the obisKey (which is meter_obis.id)
                                val meterWithObis = allMetersWithObis.find { it.meter.id == meterId }
                                val meterObisPoint = meterWithObis?.obisPoints?.find { it.id == obisKey }
                                // Use the full list of obis codes from the viewmodel
                                val obisCode = meterObisPoint?.let { locationViewModel.allObisCodes.value?.find { c -> c.id == it.obisCodeId } }
                                val obisCodeString = obisCode?.code // This is "1.8.0", etc.

                                // Use the code string, clean it, or fallback to the obisKey (UUID)
                                obisSuffix = obisCodeString?.replace(Regex("[^a-zA-Z0-9.-]"), "_") // Allow dots
                                    ?: obisKey.substring(0, 8) // Fallback to part of the UUID
                            }
                            val fileName = "${s3KeyDateFormat.format(selectedReadingDate.time)}_${currentTime}_${meter.number.replace("/", "_").replace(".", "_")}_$obisSuffix.jpg"
                            val fullStoragePath = "meter-documents/meter/${meter.id}/$fileName"
                            locationViewModel.queueImageUpload(imageUri, fullStoragePath, projectId, meter.id)
                        }
                    }
                }

                // UPDATED: Call the renamed clear function
                meterAdapter.clearEnteredObisReadings()
                meterAdapter.clearMeterImages()
                // UPDATED: Show correct image count
                Toast.makeText(this, "${readingsToSend.size} readings and $totalImageCount pictures have been queued for sending.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("Abbrechen", null)
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
        binding.logoutButton.isVisible = true
        binding.meterSearchView.isVisible = false
        binding.toolbarDateTextView.isVisible = false
    }

    private fun updateToolbarForBuildings(projectId: String?) {
        val projectName = locationViewModel.projects.value?.find { it.id == projectId }?.name ?: "Buildings"
        binding.toolbarTitle.text = projectName
        binding.searchView.queryHint = "Search Buildings..."
        binding.toolbarTitle.isVisible = true
        binding.searchView.isVisible = true
        binding.logoutButton.isVisible = false
        binding.meterSearchView.isVisible = false
        binding.toolbarDateTextView.isVisible = false
    }

    private fun updateToolbarForMeters(building: Building) {
        binding.toolbarTitle.text = building.name
        binding.searchView.isVisible = false
        binding.meterSearchView.isVisible = true
        binding.logoutButton.isVisible = false
        binding.toolbarDateTextView.isVisible = true
        updateSelectedDateText()
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


