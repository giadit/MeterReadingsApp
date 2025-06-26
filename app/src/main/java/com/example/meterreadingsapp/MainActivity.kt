package com.example.meterreadingsapp

import android.app.Activity
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
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Reading
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var locationAdapter: LocationAdapter
    private lateinit var meterAdapter: MeterAdapter

    private val uiDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val s3KeyDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US) // For file naming/path for Supabase (Date part)
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

    // ActivityResultLauncher for requesting camera permission
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

    private val takePictureLauncher: ActivityResultLauncher<Uri> =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            if (success) {
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
        binding.toolbarTitle.text = getString(R.string.app_name)

        val apiService = RetrofitClient.getService(ApiService::class.java)
        val database = AppDatabase.getDatabase(applicationContext)
        val locationDao = database.locationDao()
        val meterDao = database.meterDao()
        val readingDao = database.readingDao()
        val queuedRequestDao = database.queuedRequestDao()
        val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, applicationContext)

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupLocationRecyclerView()
        setupMeterRecyclerView()
        setupSearchView()
        setupDateSelection()
        setupTypeFilter()
        setupSendButton()
        setupMeterSearchView()

        observeLocations()
        observeMeters()
        observeUiMessages()

        binding.backButton.visibility = View.GONE
        binding.metersContainer.visibility = View.GONE
        binding.sendReadingsFab.visibility = View.GONE

        binding.refreshButton.setOnClickListener {
            if (binding.locationsRecyclerView.visibility == View.VISIBLE) {
                locationViewModel.refreshAllMeters()
                Log.d("MainActivity", getString(R.string.log_refreshing_all_meters))
            } else if (binding.metersContainer.visibility == View.VISIBLE && locationViewModel.selectedLocationId.value != null) {
                locationViewModel.refreshAllMeters()
                Log.d("MainActivity", getString(R.string.log_refreshing_meters_for_location, locationViewModel.selectedLocationId.value))
            } else {
                Log.d("MainActivity", getString(R.string.log_no_list_to_refresh))
            }
        }

        binding.backButton.setOnClickListener {
            locationViewModel.selectLocation(null)
            binding.locationsRecyclerView.visibility = View.VISIBLE
            binding.metersContainer.visibility = View.GONE
            binding.sendReadingsFab.visibility = View.GONE
            binding.backButton.visibility = View.GONE
            binding.noDataTextView.visibility = View.GONE

            binding.toolbarTitle.visibility = View.VISIBLE
            binding.searchView.visibility = View.VISIBLE
            binding.searchView.setQuery("", false)
            binding.searchView.isIconified = true
            locationViewModel.setSearchQuery("")
            binding.meterSearchView.setQuery("", false)
            locationViewModel.setMeterSearchQuery("")
        }

        binding.logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                locationViewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.toolbarTitle.visibility = View.GONE
            } else {
                if (binding.locationsRecyclerView.visibility == View.VISIBLE && binding.searchView.query.isNullOrEmpty()) {
                    binding.toolbarTitle.visibility = View.VISIBLE
                }
            }
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
            it.textCursorDrawable = ContextCompat.getDrawable(this, R.drawable.text_cursor_orange)
        }
    }

    private fun setupLocationRecyclerView() {
        locationAdapter = LocationAdapter { location ->
            locationViewModel.selectLocation(location)
            binding.locationsRecyclerView.visibility = View.GONE
            binding.metersContainer.visibility = View.VISIBLE
            binding.sendReadingsFab.visibility = View.VISIBLE
            binding.backButton.visibility = View.GONE

            binding.toolbarTitle.text = location.name ?: location.address ?: getString(R.string.app_name)

            binding.toolbarTitle.visibility = View.VISIBLE
            binding.searchView.visibility = View.GONE
            binding.searchView.setQuery("", false)
            locationViewModel.setSearchQuery("")
            binding.meterSearchView.setQuery("", false)
            locationViewModel.setMeterSearchQuery("")
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
            }
        )
        binding.metersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = meterAdapter
        }
    }

    private fun launchCamera(meter: Meter) {
        currentPhotoUri = createImageFileForMeter(meter)
        currentPhotoUri?.let { uri ->
            takePictureLauncher.launch(uri)
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

        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.let { positiveButton ->
            positiveButton.setBackgroundResource(R.drawable.button_orange_background)
            positiveButton.setTextColor(Color.WHITE)
        }
        datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.let { negativeButton ->
            negativeButton.setTextColor(ContextCompat.getColor(this, R.color.bright_orange))
        }
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
                toggleFilter(type, textView)
            }
        }

        updateFilterUI()
    }

    private fun toggleFilter(type: String, textView: TextView) {
        if (type == "All") {
            if ("All" !in selectedMeterTypesFilter) {
                selectedMeterTypesFilter.clear()
                selectedMeterTypesFilter.add("All")
            } else if (selectedMeterTypesFilter.size == 1 && "All" in selectedMeterTypesFilter) {
                return
            } else {
                selectedMeterTypesFilter.remove(type)
            }
        } else {
            if (type in selectedMeterTypesFilter) {
                selectedMeterTypesFilter.remove(type)
                if (selectedMeterTypesFilter.isEmpty()) {
                    selectedMeterTypesFilter.add("All")
                }
            } else {
                selectedMeterTypesFilter.remove("All")
                selectedMeterTypesFilter.add(type)
            }
        }
        updateFilterUI()
        locationViewModel.setMeterTypeFilter(selectedMeterTypesFilter)
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

    private fun observeLocations() {
        locationViewModel.locations.observe(this) { locations ->
            locations?.let {
                binding.loadingProgressBar.visibility = View.GONE
                if (it.isEmpty()) {
                    if (locationViewModel.searchQuery.value.isEmpty()) {
                        binding.noDataTextView.text = getString(R.string.no_data_found)
                        binding.noDataTextView.visibility = View.VISIBLE
                    } else {
                        binding.noDataTextView.text = getString(R.string.no_matching_locations_found, locationViewModel.searchQuery.value)
                        binding.noDataTextView.visibility = View.VISIBLE
                    }
                } else {
                    binding.noDataTextView.visibility = View.GONE
                    locationAdapter.submitList(it)
                }
            } ?: run {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataTextView.text = getString(R.string.no_data_found)
                binding.noDataTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun observeMeters() {
        locationViewModel.meters.observe(this) { meters ->
            meters?.let {
                binding.loadingProgressBar.visibility = View.GONE
                if (it.isEmpty() && locationViewModel.selectedLocationId.value != null) {
                    binding.noDataTextView.text = getString(R.string.no_meters_for_location)
                    binding.noDataTextView.visibility = View.VISIBLE
                    meterAdapter.submitList(emptyList())
                } else if (it.isNotEmpty()) {
                    binding.noDataTextView.visibility = View.GONE
                    meterAdapter.submitList(it)
                }
            } ?: run {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataTextView.text = getString(R.string.failed_to_load_meters)
                binding.noDataTextView.visibility = View.VISIBLE
            }
        }
    }

    /**
     * New combined function to send both meter readings and pictures.
     */
    private fun sendAllMeterReadingsAndPictures() {
        val readingsToSend = mutableListOf<Reading>()
        val enteredValues = meterAdapter.getEnteredReadings()
        val readingDateString = apiDateFormat.format(selectedReadingDate.time)

        // Process readings
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

        // Process images
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
                // Send readings
                readingsToSend.forEach { reading ->
                    locationViewModel.postMeterReading(reading)
                }

                // Queue image uploads via MeterRepository
                imagesToUpload.forEach { (meterId, imageUri) ->
                    val meter = meterAdapter.currentList.find { it.id == meterId }
                    if (meter != null) {
                        val projectId = meter.project_id ?: "unknown_project" // Project ID is still part of the Meter object, but not used in the storage path anymore
                        // FIX: Updated fullStoragePath for Supabase Storage to use meter-documents/meter/meter_id
                        // Format: "meter-documents/meter/meter_id/YYYYMMDD_HHMM_MeterNumber.jpg"
                        val currentTime = timeFormat.format(Date()) // Get current time in HHmm format
                        val fileName = "${s3KeyDateFormat.format(selectedReadingDate.time)}_${currentTime}_${meter.number.replace("/", "_").replace(".", "_")}.jpg"
                        val fullStoragePath = "meter-documents/meter/${meter.id}/${fileName}" // Changed path
                        locationViewModel.queueImageUpload(imageUri, fullStoragePath, projectId) // projectId is passed but no longer used in path
                    } else {
                        Log.e("MainActivity", "Meter not found for image with ID: $meterId. Skipping upload.")
                    }
                }

                meterAdapter.clearEnteredReadings()
                meterAdapter.clearMeterImages()

                val totalItemsSent = readingsToSend.size + imagesQueuedCount
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
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogout() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(KEY_REMEMBER_ME, false)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            apply()
        }

        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
