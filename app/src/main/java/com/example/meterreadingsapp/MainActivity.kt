package com.example.meterreadingsapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent // Import Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.DatePicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.meterreadingsapp.api.RetrofitClient // Updated package
import com.example.meterreadingsapp.api.ApiService // Updated package
import com.example.meterreadingsapp.data.AppDatabase // Updated package
import com.example.meterreadingsapp.data.Meter // Updated package
import com.example.meterreadingsapp.databinding.ActivityMainBinding // Updated package for binding
import com.example.meterreadingsapp.repository.MeterRepository // Updated package
import com.example.meterreadingsapp.viewmodel.LocationViewModel // Updated package
import com.example.meterreadingsapp.viewmodel.LocationViewModelFactory // Updated package
import com.example.meterreadingsapp.adapter.LocationAdapter // Updated package
import com.example.meterreadingsapp.adapter.MeterAdapter // Updated package
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.meterreadingsapp.data.Reading // Updated package
import com.example.meterreadingsapp.data.Location // Updated package
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        val apiService = RetrofitClient.getService(ApiService::class.java)
        val database = AppDatabase.getDatabase(applicationContext)
        val locationDao = database.locationDao() // Get LocationDao
        val meterDao = database.meterDao()
        val readingDao = database.readingDao()
        val queuedRequestDao = database.queuedRequestDao() // Get QueuedRequestDao
        val repository = MeterRepository(apiService, meterDao, readingDao, locationDao, queuedRequestDao, applicationContext) // Pass all DAOs and context

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupLocationRecyclerView()
        setupMeterRecyclerView()
        setupSearchView()
        setupDateSelection() // Re-added setupDateSelection
        setupTypeFilter()    // Re-added setupTypeFilter
        setupSendButton()    // Re-added setupSendButton

        observeLocations()
        observeMeters()
        observeUiMessages() // Observe messages from ViewModel

        // Initial UI state adjustments
        binding.backButton.visibility = View.GONE
        binding.metersContainer.visibility = View.GONE // Hide metersContainer initially
        binding.sendReadingsFab.visibility = View.GONE // Hide FAB initially

        binding.refreshButton.setOnClickListener {
            if (binding.locationsRecyclerView.visibility == View.VISIBLE) {
                locationViewModel.refreshAllMeters()
                Log.d("MainActivity", getString(R.string.log_refreshing_all_meters))
            } else if (binding.metersContainer.visibility == View.VISIBLE && locationViewModel.selectedLocationId.value != null) {
                locationViewModel.selectedLocationId.value?.let { locationId ->
                    // No specific refresh for meters in a location, just refresh all meters to update.
                    locationViewModel.refreshAllMeters() // Refresh all meters, which updates the view for selected location
                    Log.d("MainActivity", getString(R.string.log_refreshing_meters_for_location, locationId))
                }
            } else {
                Log.d("MainActivity", getString(R.string.log_no_list_to_refresh))
            }
        }

        binding.backButton.setOnClickListener {
            locationViewModel.selectLocation(null) // Clear selected location
            binding.locationsRecyclerView.visibility = View.VISIBLE // Show locations RecyclerView
            binding.metersContainer.visibility = View.GONE // Hide meters RecyclerView
            binding.sendReadingsFab.visibility = View.GONE // Hide FAB
            binding.backButton.visibility = View.GONE // Hide back button
            binding.noDataTextView.visibility = View.GONE // Hide "No data" text

            // When navigating back to locations list:
            binding.toolbarTitle.visibility = View.VISIBLE // Make toolbar title visible
            binding.toolbarTitle.text = getString(R.string.app_name) // Reset title to app name
            binding.searchView.visibility = View.VISIBLE // Make search icon visible
            binding.searchView.setQuery("", false) // Clear search query
            binding.searchView.isIconified = true // Collapse search view to icon
            locationViewModel.setSearchQuery("") // Reset ViewModel search query
        }

        // Logout button click listener (NEW)
        binding.logoutButton.setOnClickListener {
            performLogout()
        }
    }

    private fun setupSearchView() {
        // Listener for query text changes (filters results as user types)
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean { // FIX: Renamed from onOnQueryTextSubmit to onQueryTextSubmit
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                locationViewModel.setSearchQuery(newText ?: "")
                return true
            }
        })

        // Listener for when the search view expands/collapses (focus changes)
        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Search View gained focus (expanded)
                binding.toolbarTitle.visibility = View.GONE // Hide title
            } else {
                // Search View lost focus (collapsed)
                // Only show title if we are on the locations list AND search query is empty
                if (binding.locationsRecyclerView.visibility == View.VISIBLE && binding.searchView.query.isNullOrEmpty()) {
                    binding.toolbarTitle.visibility = View.VISIBLE // Show title
                }
            }
        }
    }

    private fun setupLocationRecyclerView() {
        locationAdapter = LocationAdapter { location ->
            locationViewModel.selectLocation(location) // Select the clicked location
            binding.locationsRecyclerView.visibility = View.GONE // Hide locations
            binding.metersContainer.visibility = View.VISIBLE // Show meters container
            binding.sendReadingsFab.visibility = View.VISIBLE // Show FAB
            binding.backButton.visibility = View.VISIBLE // Show back button
            binding.toolbarTitle.text = location.address // Set title to location address

            // When a location is selected:
            binding.toolbarTitle.visibility = View.VISIBLE // Ensure toolbar title is visible
            binding.searchView.visibility = View.GONE // Hide search view
            binding.searchView.isIconified = true // Collapse search view (clean up state)
            locationViewModel.setSearchQuery("") // Clear search query in ViewModel
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
            R.style.AppDatePickerDialogTheme, // Apply the custom theme for the dialog and calendar elements
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDayOfMonth: Int ->
                selectedReadingDate.set(selectedYear, selectedMonth, selectedDayOfMonth)
                updateSelectedDateText()
            },
            year, month, day
        )
        datePickerDialog.show()

        // Programmatically style the positive (OK) button after the dialog is shown
        datePickerDialog.getButton(DatePickerDialog.BUTTON_POSITIVE)?.let { positiveButton ->
            positiveButton.setBackgroundResource(R.drawable.button_orange_background) // Use the drawable for background
            positiveButton.setTextColor(Color.WHITE) // Set text color to white
        }
        // Programmatically style the negative (Cancel) button if desired (e.g., text color only)
        datePickerDialog.getButton(DatePickerDialog.BUTTON_NEGATIVE)?.let { negativeButton ->
            negativeButton.setTextColor(getColor(R.color.bright_orange)) // Example: set cancel button text to orange
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
                selectedMeterTypesFilter.remove("All")
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
                    applyMeterFilter(it)
                }
            } ?: run {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataTextView.text = getString(R.string.failed_to_load_meters)
                binding.noDataTextView.visibility = View.VISIBLE
                meterAdapter.submitList(emptyList()) // Ensure the list is cleared
            }
        }
    }

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
                    read_by = "App User" // Consider making this a string resource too if it changes
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

    // New Logout Function (NEW)
    private fun performLogout() {
        // Clear "Remember Me" credentials if they exist
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with(prefs.edit()) {
            putBoolean("rememberMe", false) // Uncheck remember me
            remove("username")
            remove("password")
            apply()
        }

        // Navigate back to LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Finish MainActivity so user can't go back to it
    }
}
