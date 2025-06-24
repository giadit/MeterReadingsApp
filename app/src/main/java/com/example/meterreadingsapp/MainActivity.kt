package com.example.meterreadingsapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.DatePicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.data.AppDatabase
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.databinding.ActivityMainBinding
import com.example.meterreadingsapp.repository.MeterRepository
import com.example.meterreadingsapp.viewmodel.LocationViewModel
import com.example.meterreadingsapp.viewmodel.LocationViewModelFactory
import com.example.meterreadingsapp.adapter.LocationAdapter
import com.example.meterreadingsapp.adapter.MeterAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.Location
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.widget.EditText // Import EditText
import androidx.core.content.ContextCompat // Import ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var locationAdapter: LocationAdapter
    private lateinit var meterAdapter: MeterAdapter
    // Separate SimpleDateFormat instances for UI display and API communication
    private val uiDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US) // For UI display
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US) // For API communication

    private var selectedReadingDate: Calendar = Calendar.getInstance()
    private val selectedMeterTypesFilter: MutableSet<String> = mutableSetOf("All")

    private lateinit var filterTextViews: Map<String, TextView>

    // SharedPreferences name for LoginActivity
    private val PREFS_NAME = "LoginPrefs"
    private val KEY_USERNAME = "username" // Key for username
    private val KEY_PASSWORD = "password" // Key for password
    private val KEY_REMEMBER_ME = "rememberMe" // Key for "remember me" checkbox state


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- Hide System UI (Status Bar and Navigation Bar) ---
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

        // Set initial toolbar title to app name
        binding.toolbarTitle.text = getString(R.string.app_name)

        // Initialize ApiService without context
        val apiService = RetrofitClient.getService(ApiService::class.java)
        val database = AppDatabase.getDatabase(applicationContext)
        val locationDao = database.locationDao()
        val meterDao = database.meterDao()
        val readingDao = database.readingDao()
        val queuedRequestDao = database.queuedRequestDao() // Keep this for offline queuing
        val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, applicationContext) // Keep app context for WorkManager

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupLocationRecyclerView()
        setupMeterRecyclerView()
        setupSearchView()
        setupDateSelection()
        setupTypeFilter()
        setupSendButton()
        setupMeterSearchView() // Setup the meter search view

        observeLocations()
        observeMeters()
        observeUiMessages()

        // Initial UI state adjustments
        binding.backButton.visibility = View.GONE
        binding.metersContainer.visibility = View.GONE
        binding.sendReadingsFab.visibility = View.GONE

        binding.refreshButton.setOnClickListener {
            if (binding.locationsRecyclerView.visibility == View.VISIBLE) {
                locationViewModel.refreshAllMeters()
                Log.d("MainActivity", getString(R.string.log_refreshing_all_meters))
            } else if (binding.metersContainer.visibility == View.VISIBLE && locationViewModel.selectedLocationId.value != null) {
                locationViewModel.selectedLocationId.value?.let { locationId ->
                    locationViewModel.refreshAllMeters()
                    Log.d("MainActivity", getString(R.string.log_refreshing_meters_for_location, locationId))
                }
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
            binding.toolbarTitle.text = getString(R.string.app_name)
            binding.searchView.visibility = View.VISIBLE // Re-show location search
            binding.searchView.setQuery("", false)
            binding.searchView.isIconified = true
            locationViewModel.setSearchQuery("")
            binding.meterSearchView.setQuery("", false) // Clear meter search
            locationViewModel.setMeterSearchQuery("") // Clear meter search in ViewModel
        }

        // Set click listener for the hardcoded logout button
        binding.logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun setupSearchView() {
        // This is the LOCATION search bar in the toolbar
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

    // Setup for the Meter Search Bar
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

        // Get the internal EditText of the SearchView and style it
        val searchEditText: EditText? = binding.meterSearchView.findViewById(androidx.appcompat.R.id.search_src_text)
        searchEditText?.let {
            // Set background to null to ensure the CardView's background is visible
            it.background = null
            it.setTextColor(ContextCompat.getColor(this, R.color.black)) // Set text color to black
            it.setHintTextColor(ContextCompat.getColor(this, R.color.dark_gray_text)) // Set hint color
            // Explicitly set the cursor drawable
            it.textCursorDrawable = ContextCompat.getDrawable(this, R.drawable.text_cursor_orange) // FIX: Set custom cursor drawable
        }
    }

    private fun setupLocationRecyclerView() {
        locationAdapter = LocationAdapter { location ->
            locationViewModel.selectLocation(location)
            binding.locationsRecyclerView.visibility = View.GONE
            binding.metersContainer.visibility = View.VISIBLE
            binding.sendReadingsFab.visibility = View.VISIBLE
            binding.backButton.visibility = View.VISIBLE
            binding.toolbarTitle.text = location.address // Update toolbar title to location address

            binding.toolbarTitle.visibility = View.VISIBLE // Keep toolbar title visible
            binding.searchView.visibility = View.GONE // Hide location search when in meter view
            binding.searchView.setQuery("", false) // Clear location search query
            locationViewModel.setSearchQuery("") // Clear location search in ViewModel
            binding.meterSearchView.setQuery("", false) // Clear meter search initially
            locationViewModel.setMeterSearchQuery("") // Clear meter search in ViewModel
        }
        binding.locationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = locationAdapter
        }
    }

    private fun setupMeterRecyclerView() {
        meterAdapter = MeterAdapter()
        binding.metersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = meterAdapter
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
            negativeButton.setTextColor(getColor(R.color.bright_orange))
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
        locationViewModel.meters.value?.let { currentMeters ->
            // Re-apply filter and search when filter changes
            applyMeterAndSearchFilter(currentMeters)
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
                if (isSelected) getColor(R.color.black) else Color.TRANSPARENT
            } else {
                getColor(colorResId)
            }

            if (isSelected) {
                textView.setBackgroundResource(R.drawable.filter_button_selected_background)
                val drawable = textView.background as GradientDrawable
                drawable.setColor(accentColor)
                drawable.setStroke(2, accentColor)

                textView.setTextColor(getColor(R.color.white))
                textView.setTypeface(null, Typeface.BOLD)
            } else {
                textView.setBackgroundResource(R.drawable.filter_button_background)
                textView.setTextColor(getColor(R.color.black))
                textView.setTypeface(null, Typeface.NORMAL)
            }
        }
    }

    private fun setupSendButton() {
        binding.sendReadingsFab.setOnClickListener {
            sendAllMeterReadings()
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
                    meterAdapter.submitList(emptyList()) // Ensure the list is cleared
                } else if (it.isNotEmpty()) {
                    binding.noDataTextView.visibility = View.GONE
                    applyMeterAndSearchFilter(it) // Apply both type and search filter
                }
            } ?: run {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataTextView.text = getString(R.string.failed_to_load_meters)
                binding.noDataTextView.visibility = View.VISIBLE
                meterAdapter.submitList(emptyList()) // Ensure the list is cleared
            }
        }
    }

    // Combined filter function for meters
    private fun applyMeterAndSearchFilter(meters: List<Meter>) {
        val filteredByType = if ("All" in selectedMeterTypesFilter) {
            meters
        } else {
            meters.filter { meter ->
                selectedMeterTypesFilter.any { selectedType ->
                    meter.energy_type.equals(selectedType, ignoreCase = true)
                }
            }
        }

        val searchQuery = locationViewModel.meterSearchQuery.value.trim()
        val finalFilteredList = if (searchQuery.isNotEmpty()) {
            filteredByType.filter { meter ->
                // Filter by meter number, case-insensitive
                meter.number.contains(searchQuery, ignoreCase = true)
            }
        } else {
            filteredByType
        }
        meterAdapter.submitList(finalFilteredList)
    }

    private fun sendAllMeterReadings() {
        val readingsToSend = mutableListOf<Reading>()
        val enteredValues = meterAdapter.getEnteredReadings()
        val readingDateString = apiDateFormat.format(selectedReadingDate.time) // Use apiDateFormat for API

        if (enteredValues.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_readings_entered), Toast.LENGTH_SHORT).show()
            return
        }

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

        if (readingsToSend.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_valid_readings_to_send), Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_send_readings_title))
            .setMessage(getString(R.string.confirm_send_readings_message, readingsToSend.size, uiDateFormat.format(selectedReadingDate.time)))
            .setPositiveButton(getString(R.string.send_button_text)) { dialog, _ ->
                readingsToSend.forEach { reading ->
                    locationViewModel.postMeterReading(reading)
                }
                meterAdapter.clearEnteredReadings()
                Toast.makeText(this, getString(R.string.readings_sent_queued, readingsToSend.size), Toast.LENGTH_LONG).show()
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
        // Clear "Remember Me" credentials if they exist
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean(KEY_REMEMBER_ME, false)
            remove(KEY_USERNAME)
            remove(KEY_PASSWORD)
            apply()
        }

        // Navigate back to LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Finish MainActivity so user can't go back to it
    }
}
