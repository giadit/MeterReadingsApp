package com.example.meterreadingsapp.ui.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.databinding.DialogEditAddMeterBinding // Ensure this import is present and correct
import com.example.meterreadingsapp.viewmodel.LocationViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * DialogFragment for adding a new meter or editing an existing one.
 * It provides input fields for meter details and handles validation and submission.
 *
 * @param viewModel The LocationViewModel to interact with for data operations.
 * @param meter The Meter object to edit (null for adding a new meter).
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
class MeterEditAddDialog(
    private val viewModel: LocationViewModel,
    private val meter: Meter?,
    private val onDismiss: () -> Unit
) : DialogFragment() {

    private var _binding: DialogEditAddMeterBinding? = null
    private val binding get() = _binding!!

    private var selectedProject: Project? = null
    private val energyTypes = listOf("Electricity", "Heat", "Gas") // Define available energy types

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Correctly inflate the binding and assign it to _binding
        _binding = DialogEditAddMeterBinding.inflate(inflater, container, false)
        return binding.root // Return the root View from the binding
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set dialog title based on whether it's an add or edit operation
        binding.dialogTitle.text = if (meter == null) {
            getString(R.string.add_new_meter_title)
        } else {
            getString(R.string.edit_meter_title)
        }

        setupProjectSpinner()
        setupEnergyTypeSpinner()
        populateFieldsForEdit()
        setupButtons()
    }

    /**
     * Sets up the project selection spinner (AutoCompleteTextView).
     * Observes projects from the ViewModel and populates the adapter.
     */
    private fun setupProjectSpinner() {
        val projectAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf<Project>() // Initial empty list
        )
        binding.projectAutoCompleteTextView.setAdapter(projectAdapter)

        // Observe projects from ViewModel
        viewModel.projects.observe(viewLifecycleOwner) { projects ->
            projects?.let {
                projectAdapter.clear()
                projectAdapter.addAll(it)
                projectAdapter.notifyDataSetChanged()

                // If editing, try to pre-select the project
                if (meter != null && selectedProject == null) {
                    val currentProject = it.find { p -> p.id == meter.project_id }
                    currentProject?.let {
                        selectedProject = it
                        binding.projectAutoCompleteTextView.setText(it.name, false)
                    }
                }
            }
        }

        // Handle project selection
        binding.projectAutoCompleteTextView.setOnItemClickListener { parent, _, position, _ ->
            // CORRECTED: Explicitly cast parent.adapter to ArrayAdapter<Project>
            @Suppress("UNCHECKED_CAST") // Suppress unchecked cast as we know the adapter type
            selectedProject = (parent.adapter as ArrayAdapter<Project>).getItem(position) as Project
        }
    }

    /**
     * Sets up the energy type selection spinner (AutoCompleteTextView).
     */
    private fun setupEnergyTypeSpinner() {
        val energyTypeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            energyTypes
        )
        binding.energyTypeAutoCompleteTextView.setAdapter(energyTypeAdapter)

        // If editing, try to pre-select the energy type
        if (meter != null) {
            val selectedEnergyTypeIndex = energyTypes.indexOfFirst { it.equals(meter.energy_type, ignoreCase = true) }
            if (selectedEnergyTypeIndex != -1) {
                binding.energyTypeAutoCompleteTextView.setText(energyTypes[selectedEnergyTypeIndex], false)
            }
        }
    }

    /**
     * Populates the dialog fields if a Meter object is provided (for editing).
     */
    private fun populateFieldsForEdit() {
        meter?.let {
            binding.meterNumberEditText.setText(it.number)
            binding.energyTypeAutoCompleteTextView.setText(it.energy_type, false) // Set text and prevent filter
            binding.addressEditText.setText(it.address)
            binding.houseNumberEditText.setText(it.house_number)
            binding.houseNumberAdditionEditText.setText(it.house_number_addition)
            binding.postalCodeEditText.setText(it.postal_code)
            binding.cityEditText.setText(it.city)
            // Project is handled by its respective spinner setup function
        }
    }

    /**
     * Sets up click listeners for the Save and Cancel buttons.
     */
    private fun setupButtons() {
        binding.saveButton.setOnClickListener {
            if (validateInputs()) {
                saveMeter()
            }
        }
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    /**
     * Validates the input fields.
     * @return True if all inputs are valid, false otherwise.
     */
    private fun validateInputs(): Boolean {
        var isValid = true

        // Meter Number
        if (binding.meterNumberEditText.text.isNullOrBlank()) {
            binding.meterNumberInputLayout.error = getString(R.string.validation_field_required)
            isValid = false
        } else {
            binding.meterNumberInputLayout.error = null
        }

        // Energy Type
        val selectedEnergyType = binding.energyTypeAutoCompleteTextView.text.toString()
        if (selectedEnergyType.isBlank() || !energyTypes.contains(selectedEnergyType)) {
            binding.energyTypeInputLayout.error = getString(R.string.validation_invalid_energy_type)
            isValid = false
        } else {
            binding.energyTypeInputLayout.error = null
        }

        // Project Selection
        if (selectedProject == null) {
            binding.projectInputLayout.error = getString(R.string.validation_field_required)
            isValid = false
        } else {
            binding.projectInputLayout.error = null
        }

        // Address
        if (binding.addressEditText.text.isNullOrBlank()) {
            binding.addressInputLayout.error = getString(R.string.validation_field_required)
            isValid = false
        } else {
            binding.addressInputLayout.error = null
        }

        return isValid
    }

    /**
     * Saves or updates the meter based on the input fields.
     */
    private fun saveMeter() {
        val meterNumber = binding.meterNumberEditText.text.toString()
        val energyType = binding.energyTypeAutoCompleteTextView.text.toString()
        val address = binding.addressEditText.text.toString()
        val houseNumber = binding.houseNumberEditText.text.toString().takeIf { it.isNotBlank() }
        val houseNumberAddition = binding.houseNumberAdditionEditText.text.toString().takeIf { it.isNotBlank() }
        val postalCode = binding.postalCodeEditText.text.toString().takeIf { it.isNotBlank() }
        val city = binding.cityEditText.text.toString().takeIf { it.isNotBlank() }

        val newOrUpdatedMeter = meter?.copy(
            number = meterNumber,
            project_id = selectedProject?.id,
            address = address,
            house_number = houseNumber,
            house_number_addition = houseNumberAddition,
            postal_code = postalCode,
            city = city,
            energy_type = energyType
        ) ?: Meter(
            id = UUID.randomUUID().toString(), // Generate new UUID for new meters
            number = meterNumber,
            project_id = selectedProject?.id,
            address = address,
            postal_code = postalCode,
            city = city,
            house_number = houseNumber,
            house_number_addition = houseNumberAddition,
            energy_type = energyType,
            type = "Unknown", // Default type for new meters
            status = "Active", // Default status for new meters
            obis_1_8_0 = false, // Default value
            obis_2_8_0 = false // Default value
            // Other fields will be null or default as per Meter data class
        )

        lifecycleScope.launch {
            if (meter == null) {
                viewModel.addMeter(newOrUpdatedMeter)
            } else {
                viewModel.patchMeter(newOrUpdatedMeter)
            }
            dismiss() // Dismiss the dialog after saving/updating
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismiss() // Invoke the callback when the dialog is dismissed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
