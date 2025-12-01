package com.example.meterreadingsapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.DatePicker
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.example.meterreadingsapp.data.*
import com.example.meterreadingsapp.databinding.ActivityMainBinding
import com.example.meterreadingsapp.databinding.DialogAddMeterBinding
import com.example.meterreadingsapp.databinding.DialogChangeMeterBinding
import com.example.meterreadingsapp.repository.MeterRepository
import com.example.meterreadingsapp.viewmodel.LocationViewModel
import com.example.meterreadingsapp.viewmodel.LocationViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
    private var currentObisKeyForPhoto: String? = null
    private var currentObisCodeForPhoto: String? = null

    private var backPressedTime: Long = 0
    private val BACK_PRESS_INTERVAL: Long = 2000 // 2 seconds

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                if (currentMeterForPhoto != null && currentObisKeyForPhoto != null) {
                    launchCamera(currentMeterForPhoto!!, currentObisCodeForPhoto)
                }
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show()
                currentMeterForPhoto = null
                currentPhotoUri = null
                currentObisKeyForPhoto = null
                currentObisCodeForPhoto = null
            }
        }

    private val takePictureLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                if (currentPhotoUri != null && currentMeterForPhoto != null && currentObisKeyForPhoto != null) {
                    meterAdapter.updateMeterImageUri(currentMeterForPhoto!!.id, currentObisKeyForPhoto!!, currentPhotoUri!!)
                    Toast.makeText(this, getString(R.string.picture_saved_success, currentPhotoUri!!.lastPathSegment), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.picture_capture_failed), Toast.LENGTH_SHORT).show()
            }
            currentPhotoUri = null
            currentMeterForPhoto = null
            currentObisKeyForPhoto = null
            currentObisCodeForPhoto = null
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
        val obisCodeDao = database.obisCodeDao()
        val meterObisDao = database.meterObisDao()

        val repository = MeterRepository(
            apiService,
            meterDao,
            readingDao,
            projectDao,
            buildingDao,
            queuedRequestDao,
            obisCodeDao,
            meterObisDao,
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
            navigateBack()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })

        binding.logoutButton.setOnClickListener {
            performLogout()
        }

        binding.bottomBarAddMeterButton.setOnClickListener {
            showAddMeterDialog()
        }
    }

    private fun navigateBack() {
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
            else -> {
                if (System.currentTimeMillis() - backPressedTime < BACK_PRESS_INTERVAL) {
                    finish()
                } else {
                    backPressedTime = System.currentTimeMillis()
                    Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.noDataTextView.isVisible = false
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
            onCameraClicked = { meter, obisKey, obisCodeString, _ ->
                currentMeterForPhoto = meter
                currentObisKeyForPhoto = obisKey
                currentObisCodeForPhoto = obisCodeString
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera(meter, obisCodeString)
                } else {
                    requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            },
            onViewImageClicked = { _, _, imageUri -> viewImage(imageUri) },
            onDeleteImageClicked = { meter, obisKey, obisCodeString, imageUri -> confirmAndDeleteImage(meter, obisKey, obisCodeString, imageUri) },
            onEditMeterClicked = { meter -> Toast.makeText(this, "Edit meter: ${meter.number}", Toast.LENGTH_SHORT).show() },
            onDeleteMeterClicked = { meter -> Toast.makeText(this, "Delete meter: ${meter.number}", Toast.LENGTH_SHORT).show() },
            onExchangeMeterClicked = { meter -> showChangeMeterDialog(meter) },
            onInfoClicked = { meter -> showMeterInfoDialog(meter) },
            currentMode = { AppMode.READINGS }
        )
        binding.metersRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = meterAdapter
        }
    }

    private fun showMeterInfoDialog(meter: Meter) {
        val info = """
            Standort: ${meter.location ?: "-"}
            Verbraucher: ${meter.consumer ?: "-"}
            Zählertypus: ${meter.type ?: "-"}
            MSB: ${meter.msb ?: "-"}
        """.trimIndent()

        MaterialAlertDialogBuilder(this)
            .setTitle("Zähler-Informationen (${meter.number})")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showChangeMeterDialog(oldMeter: Meter) {
        val meterWithObis = locationViewModel.meters.value?.find { it.meter.id == oldMeter.id }
        if (meterWithObis == null) {
            Toast.makeText(this, "Zählerdaten konnten nicht geladen werden.", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogChangeMeterBinding.inflate(LayoutInflater.from(this))
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Bestätigen", null)
            .setNegativeButton("Abbrechen", null)
            .create()

        // --- DYNAMIC FIELDS FOR OLD METER ---
        val oldMeterInputs = mutableMapOf<String, EditText>()
        val obisCodeList = locationViewModel.allObisCodes.value ?: emptyList()

        if (meterWithObis.obisPoints.isNotEmpty()) {
            meterWithObis.obisPoints.forEach { meterObis ->
                val obisCodeDef = obisCodeList.find { it.id == meterObis.obisCodeId }
                val label = obisCodeDef?.let { "${it.description} [${it.code}]" } ?: "Unbekannter OBIS Code"

                val inputLayout = TextInputLayout(this).apply {
                    hint = label
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 16 }
                }
                val editText = TextInputEditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                inputLayout.addView(editText)
                dialogBinding.oldMeterReadingsContainer.addView(inputLayout)
                oldMeterInputs[meterObis.id] = editText
            }
        } else {
            val inputLayout = TextInputLayout(this).apply {
                hint = "Zählerstand (Einfach)"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val editText = TextInputEditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            inputLayout.addView(editText)
            dialogBinding.oldMeterReadingsContainer.addView(inputLayout)
            oldMeterInputs["legacy_single"] = editText
        }

        // --- DYNAMIC FIELDS FOR NEW METER ---
        val newMeterInputs = mutableMapOf<String, EditText>()

        if (meterWithObis.obisPoints.isNotEmpty()) {
            meterWithObis.obisPoints.forEach { meterObis ->
                val obisCodeDef = obisCodeList.find { it.id == meterObis.obisCodeId }
                val label = obisCodeDef?.let { "${it.description} [${it.code}]" } ?: "Unbekannter OBIS Code"

                val inputLayout = TextInputLayout(this).apply {
                    hint = label
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 16 }
                }
                val editText = TextInputEditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                }
                inputLayout.addView(editText)
                dialogBinding.newMeterReadingsContainer.addView(inputLayout)
                newMeterInputs[meterObis.obisCodeId] = editText
            }
        } else {
            val inputLayout = TextInputLayout(this).apply {
                hint = "Anfangszählerstand (Einfach)"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val editText = TextInputEditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            inputLayout.addView(editText)
            dialogBinding.newMeterReadingsContainer.addView(inputLayout)
            newMeterInputs["legacy_single"] = editText
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setBackgroundColor(ContextCompat.getColor(this, R.color.bright_orange))
            positiveButton.setTextColor(Color.WHITE)

            positiveButton.setOnClickListener {
                val newMeterNumber = dialogBinding.newMeterNumber.text.toString()

                // Retrieve Date (Common for both)
                val datePicker = dialogBinding.oldMeterLastReadingDate
                val dateCalendar = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                }
                val exchangeDate = apiDateFormat.format(dateCalendar.time)

                if (newMeterNumber.isBlank()) {
                    Toast.makeText(this, "Bitte neue Zählernummer eingeben.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Collect Old Readings
                val oldReadings = mutableListOf<Reading>()
                var allOldFilled = true
                oldMeterInputs.forEach { (meterObisId, editText) ->
                    val value = editText.text.toString()
                    if (value.isBlank()) {
                        allOldFilled = false
                    } else {
                        oldReadings.add(Reading(
                            id = UUID.randomUUID().toString(),
                            meter_id = oldMeter.id,
                            value = value,
                            date = exchangeDate,
                            read_by = "App User",
                            meterObisId = if (meterObisId == "legacy_single") null else meterObisId,
                            migrationStatus = null
                        ))
                    }
                }

                // Collect New Readings
                val newReadingsMap = mutableMapOf<String, Reading>()
                var allNewFilled = true
                newMeterInputs.forEach { (obisCodeId, editText) ->
                    val value = editText.text.toString()
                    if (value.isBlank()) {
                        allNewFilled = false
                    } else {
                        newReadingsMap[obisCodeId] = Reading(
                            id = UUID.randomUUID().toString(),
                            meter_id = "",
                            value = value,
                            date = exchangeDate,
                            read_by = "App User",
                            meterObisId = null,
                            migrationStatus = null
                        )
                    }
                }

                if (!allOldFilled || !allNewFilled) {
                    Toast.makeText(this, "Bitte alle Zählerstände ausfüllen.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                locationViewModel.exchangeMeter(
                    oldMeter,
                    oldReadings,
                    newMeterNumber,
                    newReadingsMap
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
        val stromMeterTypes = listOf("Summenzähler","Wohnung","Gewerbe", "Hausstrom", "Unterzähler",
            "Erzeugungszähler PV", "Eigenbedarf PV", "Eigenbedarf KWK", "Erzeugungszähler KWK",
            "BEA Eigenbedarf", "Elektromobilität", "Zwischenzähler","Abgrenzungszähler","Baustrom",
            "Balkon-PV Einspeisung", "Eigenbedarf WP", "unbekannt")

        val energyAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, energyTypes)
        val stromMeterAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, stromMeterTypes)

        energyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        stromMeterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        dialogBinding.spinnerEnergyType.adapter = energyAdapter
        dialogBinding.spinnerMeterType.adapter = stromMeterAdapter

        dialogBinding.spinnerEnergyType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                try {
                    when (energyTypes[position]) {
                        "Strom" -> {
                            dialogBinding.meterTypeSpinnerLabel.visibility = View.VISIBLE
                            dialogBinding.spinnerMeterType.visibility = View.VISIBLE
                            dialogBinding.meterTypeDisplayLayout.visibility = View.GONE
                        }
                        "Wärme" -> {
                            dialogBinding.meterTypeSpinnerLabel.visibility = View.GONE
                            dialogBinding.spinnerMeterType.visibility = View.GONE
                            dialogBinding.meterTypeDisplayLayout.visibility = View.VISIBLE
                            dialogBinding.meterTypeDisplayEditText.setText("Wärmemengenzähler")
                        }
                        "Gas" -> {
                            dialogBinding.meterTypeSpinnerLabel.visibility = View.GONE
                            dialogBinding.spinnerMeterType.visibility = View.GONE
                            dialogBinding.meterTypeDisplayLayout.visibility = View.VISIBLE
                            dialogBinding.meterTypeDisplayEditText.setText("Gaszähler")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("showAddMeterDialog", "Error toggling view visibility: ${e.message}")
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // --- DYNAMIC OBIS SELECTION & INPUT LOGIC (PROGRAMMATIC REPLACEMENT) ---
        val obisInputs = mutableMapOf<String, EditText>()
        dialogBinding.obisChecklistContainer.removeAllViews()

        val colorStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(ContextCompat.getColor(this, R.color.bright_orange), ContextCompat.getColor(this, R.color.dark_gray_text))
        )

        locationViewModel.allObisCodes.value?.forEach { obisCode ->
            // Create container for this row
            val rowLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // Create Checkbox
            val checkBox = CheckBox(this).apply {
                text = "${obisCode.description ?: "N/A"} [${obisCode.code}]"
                tag = obisCode.id
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                buttonTintList = colorStateList
            }

            // Create Input Layout
            val inputLayout = TextInputLayout(this).apply {
                hint = "Anfangszählerstand"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 32 // Indent
                }
                visibility = View.GONE // Initially hidden
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            }

            // Create Input Edit Text
            val editText = TextInputEditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            }

            inputLayout.addView(editText)
            rowLayout.addView(checkBox)
            rowLayout.addView(inputLayout)
            dialogBinding.obisChecklistContainer.addView(rowLayout)

            // Track input for later retrieval
            obisInputs[obisCode.id] = editText

            // Toggle input visibility based on checkbox
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                inputLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
                if (!isChecked) editText.text?.clear()
            }
        }
        // --- END DYNAMIC LOGIC ---

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setBackgroundColor(ContextCompat.getColor(this, R.color.bright_orange))
            positiveButton.setTextColor(Color.WHITE)

            positiveButton.setOnClickListener {
                val newMeterNumber = dialogBinding.newMeterNumber.text.toString()
                val datePicker = dialogBinding.newMeterReadingDate
                val calendar = Calendar.getInstance().apply {
                    set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                }
                val readingDate = apiDateFormat.format(calendar.time)

                val energyType = dialogBinding.spinnerEnergyType.selectedItem.toString()
                val meterType: String = when (energyType) {
                    "Strom" -> dialogBinding.spinnerMeterType.selectedItem.toString()
                    "Wärme" -> "Wärmemengenzähler"
                    "Gas" -> "Gaszähler"
                    else -> ""
                }

                if (newMeterNumber.isBlank() || meterType.isBlank()) {
                    Toast.makeText(this, "Bitte Zählernummer und Typ angeben.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Collect Readings
                val initialReadingsMap = mutableMapOf<String, Reading>() // Key: ObisCodeId
                var hasSelectedObis = false
                var allInputsFilled = true

                obisInputs.forEach { (obisCodeId, editText) ->
                    if (editText.isShown) { // Check if corresponding checkbox was checked (input is shown)
                        hasSelectedObis = true
                        val value = editText.text.toString()
                        if (value.isBlank()) {
                            allInputsFilled = false
                        } else {
                            initialReadingsMap[obisCodeId] = Reading(
                                id = UUID.randomUUID().toString(),
                                meter_id = "", // Will be set in Repository
                                value = value,
                                date = readingDate,
                                read_by = "App User",
                                meterObisId = null, // Will be set in Repository
                                migrationStatus = null
                            )
                        }
                    }
                }

                if (!hasSelectedObis) {
                    Toast.makeText(this, "Bitte mindestens eine OBIS-Kennzahl auswählen.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (!allInputsFilled) {
                    Toast.makeText(this, "Bitte Anfangszählerstände für alle ausgewählten Kennzahlen eingeben.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newMeterRequest = NewMeterRequest(
                    number = newMeterNumber,
                    projectId = currentProjectId,
                    buildingId = currentBuilding.id,
                    energyType = energyType,
                    type = meterType,
                    status = "Aktiv",
                    replacedOldMeterId = null,
                    street = currentBuilding.street,
                    postalCode = currentBuilding.postal_code,
                    city = currentBuilding.city,
                    houseNumber = currentBuilding.house_number,
                    houseNumberAddition = currentBuilding.house_number_addition
                )

                // Pass the map of readings to the ViewModel
                locationViewModel.addNewMeter(newMeterRequest, initialReadingsMap)
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

    private fun launchCamera(meter: Meter, obisCode: String?) {
        currentPhotoUri = createImageFileForMeter(meter, obisCode)
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
    private fun createImageFileForMeter(meter: Meter, obisCode: String?): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeObisKey = obisCode?.replace(Regex("[^a-zA-Z0-9.-]"), "_") ?: "reading"
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
            Toast.makeText(this, getString(R.string.error_viewing_image), Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmAndDeleteImage(meter: Meter, obisKey: String, obisCode: String?, imageUri: Uri) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_delete_image_title))
            .setMessage(getString(R.string.confirm_delete_image_message, "${meter.number} (${obisCode ?: "N/A"})"))
            .setPositiveButton(getString(R.string.delete_button_text)) { dialog, _ ->
                imageUri.path?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        if (file.delete()) {
                            meterAdapter.removeMeterImageUri(meter.id, obisKey)
                            Toast.makeText(this, getString(R.string.image_deleted_success), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, getString(R.string.image_deleted_failed), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, getString(R.string.image_file_not_found), Toast.LENGTH_SHORT).show()
                        meterAdapter.removeMeterImageUri(meter.id, obisKey)
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
                binding.noDataTextView.text = getString(R.string.no_projects_found)
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
                binding.noDataTextView.text = getString(R.string.no_locations_for_project)
                buildingAdapter.submitList(emptyList())
            } else {
                buildingAdapter.submitList(buildings)
            }
        }
    }

    private fun observeMeters() {
        locationViewModel.meters.observe(this) { meters ->
            binding.loadingProgressBar.isVisible = false
            if (meters != null) {
                applyMeterFilter(meters)
            } else {
                binding.noDataTextView.text = getString(R.string.no_meters_for_location)
                binding.noDataTextView.isVisible = true
                meterAdapter.submitList(emptyList())
            }
        }
    }

    private fun applyMeterFilter(meters: List<MeterWithObisPoints>) {
        val statusFilteredMeters = if (showExchangedMeters) {
            meters.filter { it.meter.status.equals("Ausgetauscht", ignoreCase = true) }
        } else {
            meters.filter { it.meter.status.equals("Aktiv", ignoreCase = true) }
        }
        val finalFilteredMeters = if ("All" in selectedMeterTypesFilter) {
            statusFilteredMeters
        } else {
            statusFilteredMeters.filter { meterWithObis ->
                selectedMeterTypesFilter.any {
                    it.equals(meterWithObis.meter.energyType, ignoreCase = true)
                }
            }
        }
        meterAdapter.submitList(finalFilteredMeters)
        binding.noDataTextView.isVisible = finalFilteredMeters.isEmpty()
        if (finalFilteredMeters.isEmpty()) {
            binding.noDataTextView.text = getString(R.string.no_data_found)
        }
    }

    private fun sendAllMeterReadingsAndPictures() {
        val readingsToSend = mutableListOf<Reading>()
        val enteredValues = meterAdapter.getEnteredReadings()
        val readingDateString = apiDateFormat.format(selectedReadingDate.time)
        val allMetersWithObis = locationViewModel.meters.value ?: emptyList()

        var invalidInputFound = false
        for ((meterId, readingsMap) in enteredValues) {
            for ((_, readingValue) in readingsMap) {
                if (readingValue.isNotBlank()) {
                    if (readingValue == "." || readingValue.startsWith(".") || readingValue.endsWith(".")) {
                        val meterNumber = allMetersWithObis.find { it.meter.id == meterId }?.meter?.number ?: "Unknown"
                        Toast.makeText(this, "Ungültige Eingabe für Zähler $meterNumber: '$readingValue'", Toast.LENGTH_LONG).show()
                        invalidInputFound = true
                        break
                    }
                }
            }
            if (invalidInputFound) break
        }
        if (invalidInputFound) return

        enteredValues.forEach { (meterId, readingsMap) ->
            readingsMap.forEach { (obisOrKey, readingValue) ->
                if (readingValue.isNotBlank()) {
                    val meterObisId = if (obisOrKey == MeterAdapter.SINGLE_READING_KEY) {
                        null
                    } else {
                        obisOrKey
                    }

                    readingsToSend.add(
                        Reading(
                            id = UUID.randomUUID().toString(),
                            meter_id = meterId,
                            value = readingValue,
                            date = readingDateString,
                            read_by = "App User",
                            meterObisId = meterObisId,
                            migrationStatus = null
                        )
                    )
                }
            }
        }

        val imagesToUpload = meterAdapter.getMeterImages()
        val totalImageCount = imagesToUpload.values.sumOf { it.size }

        if (readingsToSend.isEmpty() && totalImageCount == 0) {
            Toast.makeText(this, getString(R.string.no_readings_or_pictures_entered), Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.confirm_send_data_title))
            .setMessage(getString(R.string.confirm_send_data_message, readingsToSend.size, totalImageCount, uiDateFormat.format(selectedReadingDate.time)))
            .setPositiveButton(getString(R.string.send_button_text)) { dialog, _ ->
                readingsToSend.forEach { locationViewModel.postMeterReading(it) }

                imagesToUpload.forEach { (meterId, obisMap) ->
                    obisMap.forEach { (obisKey, imageUri) ->
                        val meter = allMetersWithObis.find { it.meter.id == meterId }?.meter
                        if (meter != null) {
                            val projectId = meter.projectId
                            val currentTime = timeFormat.format(Date())

                            val obisSuffix: String
                            if (obisKey == MeterAdapter.SINGLE_READING_KEY) {
                                obisSuffix = "main"
                            } else {
                                val meterWithObis = allMetersWithObis.find { it.meter.id == meterId }
                                val meterObisPoint = meterWithObis?.obisPoints?.find { it.id == obisKey }
                                val obisCode = meterObisPoint?.let { locationViewModel.allObisCodes.value?.find { c -> c.id == it.obisCodeId } }
                                val obisCodeString = obisCode?.code

                                obisSuffix = obisCodeString?.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                                    ?: obisKey.substring(0, 8)
                            }
                            val fileName = "${s3KeyDateFormat.format(selectedReadingDate.time)}_${currentTime}_${meter.number.replace("/", "_").replace(".", "_")}_$obisSuffix.jpg"
                            val fullStoragePath = "meter-documents/meter/${meter.id}/$fileName"
                            locationViewModel.queueImageUpload(imageUri, fullStoragePath, projectId, meter.id)
                        }
                    }
                }

                meterAdapter.clearEnteredObisReadings()
                meterAdapter.clearMeterImages()
                Toast.makeText(this, getString(R.string.data_sent_queued, readingsToSend.size, totalImageCount), Toast.LENGTH_LONG).show()
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
        binding.searchView.queryHint = getString(R.string.search_project_hint)
        binding.toolbarTitle.isVisible = true
        binding.searchView.isVisible = true
        binding.logoutButton.isVisible = true
        binding.meterSearchView.isVisible = false
        binding.toolbarDateTextView.isVisible = false
    }

    private fun updateToolbarForBuildings(projectId: String?) {
        val projectName = locationViewModel.projects.value?.find { it.id == projectId }?.name ?: "Buildings"
        binding.toolbarTitle.text = projectName
        binding.searchView.queryHint = getString(R.string.search_location_hint)
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