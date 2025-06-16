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
import com.example.mypostsapp.data.Reading
import androidx.lifecycle.lifecycleScope // Import lifecycleScope
import kotlinx.coroutines.flow.collectLatest // Import collectLatest
import kotlinx.coroutines.launch // Import launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var locationViewModel: LocationViewModel
    private lateinit var locationAdapter: LocationAdapter
    private lateinit var meterAdapter: MeterAdapter
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var selectedReadingDate: Calendar = Calendar.getInstance()
    private var currentMeterTypeFilter: String = "All"

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
        setupMeterRecyclerView()
        setupSearchView()
        setupDateSelection()
        setupTypeFilter()
        setupSendButton()

        observeLocations()
        observeMeters()
        observeUiMessages() // Observe for messages from ViewModel

        binding.backButton.visibility = View.GONE
        binding.metersContainer.visibility = View.GONE
        binding.sendReadingsFab.visibility = View.GONE

        binding.refreshButton.setOnClickListener {
            if (binding.locationsRecyclerView.visibility == View.VISIBLE) {
                locationViewModel.refreshAllMeters()
                Log.d("MainActivity", "Refreshing all meters/locations...")
            } else if (binding.metersContainer.visibility == View.VISIBLE && locationViewModel.selectedLocationId.value != null) {
                locationViewModel.selectedLocationId.value?.let { locationId ->
                    locationViewModel.refreshAllMeters()
                    Log.d("MainActivity", "Refreshing meters for location: $locationId (by refreshing all meters)")
                }
            } else {
                Log.d("MainActivity", "No specific list to refresh or no location selected.")
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
            binding.searchView.visibility = View.VISIBLE
            binding.searchView.setQuery("", false)
            binding.searchView.isIconified = true
            locationViewModel.setSearchQuery("")
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
            locationViewModel.selectLocation(location)
            binding.locationsRecyclerView.visibility = View.GONE
            binding.metersContainer.visibility = View.VISIBLE
            binding.sendReadingsFab.visibility = View.VISIBLE
            binding.backButton.visibility = View.VISIBLE
            binding.toolbarTitle.text = location.address

            binding.toolbarTitle.visibility = View.VISIBLE
            binding.searchView.visibility = View.GONE
            binding.searchView.isIconified = true
            locationViewModel.setSearchQuery("")
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
                    meterAdapter.submitList(emptyList())
                } else if (it.isNotEmpty()) {
                    binding.noDataTextView.visibility = View.GONE
                    applyMeterFilter(it)
                }
            } ?: run {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataTextView.text = "Failed to load meters."
                binding.noDataTextView.visibility = View.VISIBLE
                meterAdapter.submitList(emptyList())
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
            Toast.makeText(this, "No valid readings to send.", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Send Readings")
            .setMessage("Are you sure you want to send ${readingsToSend.size} readings for date ${readingDateString}?")
            .setPositiveButton("Send") { dialog, _ ->
                readingsToSend.forEach { reading ->
                    locationViewModel.postMeterReading(reading)
                }
                meterAdapter.clearEnteredReadings()
                Toast.makeText(this, "${readingsToSend.size} readings sent or queued!", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    private fun observeUiMessages() {
        // FIX: Collect the SharedFlow using lifecycleScope.launch
        lifecycleScope.launch {
            locationViewModel.uiMessage.collectLatest { message ->
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
