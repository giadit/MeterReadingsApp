package com.example.mypostsapp

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.EditText
import android.widget.DatePicker
import android.widget.Toast // Still used for general errors like empty reading value
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
    private lateinit var meterAdapter: MeterAdapter

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
        val repository = MeterRepository(apiService, meterDao, readingDao, locationDao) // Pass LocationDao here

        locationViewModel = ViewModelProvider(this, LocationViewModelFactory(repository))
            .get(LocationViewModel::class.java)

        setupLocationRecyclerView()
        setupMeterRecyclerView()
        setupSearchView()

        observeLocations()
        observeMeters()
        // Removed: observeMeterUpdateResult() as it's no longer needed for PATCH approach

        // Initial UI state: Toolbar title is visible, search view is iconified (handled by XML), back button is hidden
        binding.backButton.visibility = View.GONE


        binding.refreshButton.setOnClickListener {
            if (binding.locationsRecyclerView.visibility == View.VISIBLE) {
                locationViewModel.refreshAllMeters()
                Log.d("MainActivity", "Refreshing all meters/locations...")
            } else if (binding.metersRecyclerView.visibility == View.VISIBLE && locationViewModel.selectedLocationId.value != null) {
                locationViewModel.selectedLocationId.value?.let { locationId ->
                    val parts = locationId.split("|")
                    if (parts.size == 3) {
                        // FIX: Use refreshAllMeters as there is no specific refresh for meters by location in viewmodel
                        locationViewModel.refreshAllMeters()
                        Log.d("MainActivity", "Refreshing meters for location: $locationId (by refreshing all meters)")
                    }
                }
            } else {
                Log.d("MainActivity", "No specific list to refresh or no location selected.")
            }
        }

        binding.backButton.setOnClickListener {
            locationViewModel.selectLocation(null) // Clear selected location
            binding.locationsRecyclerView.visibility = View.VISIBLE // Show locations RecyclerView
            binding.metersRecyclerView.visibility = View.GONE // Hide meters RecyclerView
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
        // Listener for query text changes (filters results as user types)
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
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
            binding.metersRecyclerView.visibility = View.VISIBLE // Show meters
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
        meterAdapter = MeterAdapter { meter ->
            showAddReadingDialog(meter)
        }
        binding.metersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = meterAdapter
        }
        binding.metersRecyclerView.visibility = View.GONE
        binding.backButton.visibility = View.GONE
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
                } else if (it.isNotEmpty()) {
                    binding.noDataTextView.visibility = View.GONE
                    meterAdapter.submitList(it)
                }
            } ?: run {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataTextView.text = "Failed to load meters."
                binding.noDataTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun showAddReadingDialog(meter: Meter) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_reading, null)
        val readingValueEditText = dialogView.findViewById<EditText>(R.id.readingValueEditText)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.datePicker)

        val calendar = Calendar.getInstance()
        datePicker.init(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
            null
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Add New Reading for Meter: ${meter.number} (${meter.serial_number ?: "N/A"})")
            .setView(dialogView)
            .setPositiveButton("Add") { dialog, _ ->
                val readingValue = readingValueEditText.text.toString().trim()
                if (readingValue.isNotEmpty()) {
                    val day = datePicker.dayOfMonth
                    val month = datePicker.month + 1
                    val year = calendar.get(Calendar.YEAR)

                    val formattedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

                    val newReadingId = java.util.UUID.randomUUID().toString()

                    val newReading = Reading(
                        id = newReadingId,
                        meter_id = meter.id,
                        value = readingValue,
                        date = formattedDate,
                        created_by = null,
                        read_by = "App User"
                    )
                    // FIX: Call postMeterReading instead of updateMeterReading
                    locationViewModel.postMeterReading(newReading)
                } else {
                    Log.w("MainActivity", "Reading value cannot be empty.")
                    Toast.makeText(this, "Reading value cannot be empty. Please enter a value.", Toast.LENGTH_LONG).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }
}
