package com.example.mypostsapp

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.DatePicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mypostsapp.api.RetrofitClient
import com.example.mypostsapp.api.ApiService
import com.example.mypostsapp.data.AppDatabase
import com.example.mypostsapp.data.Meter
import com.example.mypostsapp.databinding.ActivityMainBinding
import com.example.mypostsapp.repository.MeterRepository
import com.example.mypostsapp.viewmodel.LocationViewModel
import com.example.mypostsapp.viewmodel.LocationViewModelFactory
import com.example.mypostsapp.adapter.LocationAdapter
import com.example.mypostsapp.adapter.MeterAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.example.mypostsapp.data.Reading // Ensure Reading is imported

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var locationAdapter: LocationAdapter
    private lateinit var meterAdapter: MeterAdapter // Keep this for meter list
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US) // Date formatter

    // Current selected date for readings, defaults to today
    private var selectedReadingDate: Calendar = Calendar.getInstance()
    // Current selected meter type filter, defaults to "All"
    private var currentMeterTypeFilter: String = "All"

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
        val locationDao = database.locationDao()
        val meterDao = database.meterDao()
        val readingDao = database.readingDao()
        val queuedRequestDao = database.queuedRequestDao()
        val repository = MeterRepository(
            apiService,
            meterDao,
            readingDao,
            locationDao,
            queuedRequestDao,
            applicationContext
        )

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupLocationRecyclerView()
        setupMeterRecyclerView() // Still setup for MeterAdapter instance
        setupSearchView()
        setupDateSelection() // NEW: Setup date picker
        setupTypeFilter() // NEW: Setup type filter radio buttons
        setupSendButton() // NEW: Setup send button

        observeLocations()
        observeMeters()
        observeUiMessages() // Observe for messages from ViewModel

        // Initial UI state: Toolbar title is visible, search view is iconified (handled by XML), back button is hidden
        binding.backButton.visibility = View.GONE
        binding.metersContainer.visibility = View.GONE // Ensure meters container is initially hidden
        binding.sendReadingsFab.visibility = View.GONE // Ensure FAB is initially hidden


        binding.refreshButton.setOnClickListener {
            if (binding.locationsRecyclerView.visibility == View.VISIBLE) {
                locationViewModel.refreshAllMeters()
                Log.d("MainActivity", "Refreshing all meters/locations...")
            } else if (binding.metersContainer.visibility == View.VISIBLE && locationViewModel.selectedLocationId.value != null) { // Check metersContainer visibility
                locationViewModel.selectedLocationId.value?.let { locationId ->
                    locationViewModel.refreshAllMeters() // Refresh all meters to ensure updates for selected location
                    Log.d("MainActivity", "Refreshing meters for location: $locationId (by refreshing all meters)")
                }
            } else {
                Log.d("MainActivity", "No specific list to refresh or no location selected.")
            }
        }

        binding.backButton.setOnClickListener {
            locationViewModel.selectLocation(null) // Clear selected location
            binding.locationsRecyclerView.visibility = View.VISIBLE // Show locations RecyclerView
            binding.metersContainer.visibility = View.GONE // FIX: Hide metersContainer
            binding.sendReadingsFab.visibility = View.GONE // FIX: Hide FAB
            binding.backButton.visibility = View.GONE // Hide back button
            binding.noDataTextView.visibility = View.GONE

            // When navigating back to locations list:
            binding.toolbarTitle.visibility = View.VISIBLE // Make toolbar title visible
            binding.toolbarTitle.text = getString(R.string.app_name) // Reset title to app name
            binding.searchView.visibility = View.VISIBLE // Make search icon visible
            binding.searchView.setQuery("", false) // Clear search query
            binding.searchView.isIconified = true // Collapse search view to icon
            locationViewModel.setSearchQuery("") // Reset ViewModel search query
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

    private fun setupLocationRecyclerView() {
        locationAdapter = LocationAdapter { location ->
            locationViewModel.selectLocation(location) // Select the clicked location
            binding.locationsRecyclerView.visibility = View.GONE // Hide locations
            binding.metersContainer.visibility = View.VISIBLE // FIX: Show metersContainer
            binding.sendReadingsFab.visibility = View.VISIBLE // FIX: Show FAB
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
        // MeterAdapter no longer takes an onItemClicked listener in its constructor
        meterAdapter = MeterAdapter()
        binding.metersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = meterAdapter
        }
    }

    private fun setupDateSelection() {
        // Initialize date TextView with today's date
        updateSelectedDateText()

        // Set up click listener for the date TextView and button
        binding.selectedDateTextView.setOnClickListener { showDatePicker() }
        binding.selectDateButton.setOnClickListener { showDatePicker() }
    }

    private fun updateSelectedDateText() {
        binding.selectedDateTextView.text = dateFormat.format(selectedReadingDate.time)
    }

    private fun showDatePicker() {
        val year = selectedReadingDate.get(Calendar.YEAR)
        val month = selectedReadingDate.get(Calendar.MONTH)
        val day = selectedReadingDate.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _: DatePicker, selectedYear: Int, selectedMonth: Int, selectedDayOfMonth: Int ->
                selectedReadingDate.set(selectedYear, selectedMonth, selectedDayOfMonth)
                updateSelectedDateText()
            },
            year, month, day
        )
        datePickerDialog.show()
    }

    private fun setupTypeFilter() {
        binding.meterTypeFilterGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMeterTypeFilter = when (checkedId) {
                R.id.filterElectricity -> "Electricity"
                R.id.filterHeat -> "Heat"
                R.id.filterGas -> "Gas"
                else -> "All"
            }
            // Trigger filter application when type changes
            locationViewModel.meters.value?.let { currentMeters ->
                applyMeterFilter(currentMeters)
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
                        binding.noDataTextView.text = "No matching locations found for '${locationViewModel.searchQuery.value}'."
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
                    binding.noDataTextView.text = "No meters found for this location."
                    binding.noDataTextView.visibility = View.VISIBLE
                    meterAdapter.submitList(emptyList()) // Clear adapter if no meters
                } else if (it.isNotEmpty()) {
                    binding.noDataTextView.visibility = View.GONE
                    applyMeterFilter(it) // Apply filter when meters data changes
                }
            } ?: run {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataTextView.text = "Failed to load meters."
                binding.noDataTextView.visibility = View.VISIBLE
                meterAdapter.submitList(emptyList()) // Clear adapter on failure
            }
        }
    }

    private fun applyMeterFilter(meters: List<Meter>) {
        val filteredMeters = if (currentMeterTypeFilter == "All") {
            meters
        } else {
            meters.filter { it.energy_type.equals(currentMeterTypeFilter, ignoreCase = true) }
        }
        meterAdapter.submitList(filteredMeters)
    }

    private fun sendAllMeterReadings() {
        val readingsToSend = mutableListOf<Reading>()
        val enteredValues = meterAdapter.getEnteredReadings()
        val readingDateString = dateFormat.format(selectedReadingDate.time)

        if (enteredValues.isEmpty()) {
            Toast.makeText(this, "No readings entered.", Toast.LENGTH_SHORT).show()
            return
        }

        // Iterate through all meters in the adapter's current list (which is already filtered by type)
        // This ensures we only process meters currently displayed and for which values were entered.
        for (meter in meterAdapter.currentList) {
            val readingValue = enteredValues[meter.id]
            if (!readingValue.isNullOrBlank()) { // Only send if value is not null or blank
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
            Toast.makeText(this, "No valid readings to send.", Toast.LENGTH_SHORT).show()
            return
        }

        // Confirm dialog before sending multiple readings
        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Send Readings")
            .setMessage("Are you sure you want to send ${readingsToSend.size} readings for date ${readingDateString}?")
            .setPositiveButton("Send") { dialog, _ ->
                readingsToSend.forEach { reading ->
                    locationViewModel.postMeterReading(reading)
                }
                meterAdapter.clearEnteredReadings() // Clear inputs after sending
                Toast.makeText(this, "${readingsToSend.size} readings sent or queued!", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun observeUiMessages() {
        // Observe messages from ViewModel, e.g., for showing Toast messages
        locationViewModel.uiMessage.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
