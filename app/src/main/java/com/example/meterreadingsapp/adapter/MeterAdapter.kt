package com.example.meterreadingsapp.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // ADDED: Import Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.MainActivity
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.databinding.ItemMeterBinding

/**
 * RecyclerView adapter for displaying a list of Meter objects.
 * It handles displaying meter details, reading input, camera/image actions,
 * and now also edit/delete actions based on the current application mode.
 *
 * @param onCameraClicked Lambda invoked when the camera button is clicked for a meter.
 * @param onViewImageClicked Lambda invoked when the view image button is clicked for a meter.
 * @param onDeleteImageClicked Lambda invoked when the delete image button is clicked for a meter.
 * @param onEditMeterClicked Lambda invoked when the edit meter button is clicked for a meter (NEW).
 * @param onDeleteMeterClicked Lambda invoked when the delete meter button is clicked for a meter (NEW).
 * @param currentMode A lambda that provides the current AppMode (Readings or Editing).
 */
class MeterAdapter(
    private val onCameraClicked: (Meter, Uri?) -> Unit,
    private val onViewImageClicked: (Meter, Uri) -> Unit,
    private val onDeleteImageClicked: (Meter, Uri) -> Unit,
    private val onEditMeterClicked: (Meter) -> Unit, // NEW: Callback for edit button
    private val onDeleteMeterClicked: (Meter) -> Unit, // NEW: Callback for delete button
    private val currentMode: () -> MainActivity.AppMode // Lambda to get current mode from Activity
) : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    // Map to hold entered reading values (meterId to reading value string)
    private val enteredReadings: MutableMap<String, String> = mutableMapOf()
    // Map to hold locally taken image URIs (meterId to image Uri)
    private val meterImages: MutableMap<String, Uri> = mutableMapOf()

    /**
     * ViewHolder for individual Meter items.
     * Binds data from a Meter object to the layout views and handles UI logic based on app mode.
     */
    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            // Add a TextWatcher to the EditText to save entered readings
            binding.newReadingValueEditText.setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    val meterId = getItem(bindingAdapterPosition).id
                    enteredReadings[meterId] = binding.newReadingValueEditText.text.toString()
                }
            }

            // Set up click listeners for the buttons
            binding.cameraButton.setOnClickListener {
                val meter = getItem(bindingAdapterPosition)
                onCameraClicked(meter, meterImages[meter.id])
            }
            binding.viewImageButton.setOnClickListener {
                val meter = getItem(bindingAdapterPosition)
                meterImages[meter.id]?.let { uri ->
                    onViewImageClicked(meter, uri)
                } ?: run {
                    Toast.makeText(binding.root.context, R.string.no_image_to_view, Toast.LENGTH_SHORT).show()
                }
            }
            binding.deleteImageButton.setOnClickListener {
                val meter = getItem(bindingAdapterPosition)
                meterImages[meter.id]?.let { uri ->
                    onDeleteImageClicked(meter, uri)
                } ?: run {
                    Toast.makeText(binding.root.context, R.string.no_image_to_delete, Toast.LENGTH_SHORT).show()
                }
            }

            // NEW: Set up click listeners for edit and delete meter buttons
            binding.editMeterButton.setOnClickListener {
                val meter = getItem(bindingAdapterPosition)
                onEditMeterClicked(meter)
            }
            binding.deleteMeterButton.setOnClickListener {
                val meter = getItem(bindingAdapterPosition)
                onDeleteMeterClicked(meter)
            }
        }

        /**
         * Binds a Meter object to the views in the ViewHolder.
         * Adjusts visibility of elements based on the current application mode.
         * @param meter The Meter object to bind.
         */
        fun bind(meter: Meter) {
            binding.meterNumberTextView.text = meter.number
            binding.meterEnergyTypeTextView.text = meter.energy_type
            binding.meterLastReadingTextView.text = meter.last_reading ?: "N/A"
            binding.meterLastReadingDateTextView.text = meter.last_reading_date ?: "N/A"

            // Set energy type icon based on energy_type
            val iconResId = when (meter.energy_type.lowercase()) {
                "electricity" -> R.drawable.ic_bolt
                "heat" -> R.drawable.ic_flame // CORRECTED: Changed from ic_heat to ic_flame
                "gas" -> R.drawable.ic_gas
                else -> R.drawable.ic_bolt // Default icon
            }
            binding.energyTypeIcon.setImageResource(iconResId)

            // Restore entered reading if available
            binding.newReadingValueEditText.setText(enteredReadings[meter.id])

            // Update UI based on current AppMode
            val mode = currentMode()
            when (mode) {
                MainActivity.AppMode.READINGS -> {
                    binding.readingValueInputLayout.isVisible = true // Show input field
                    binding.cameraButton.isVisible = true // Show camera button
                    binding.viewImageButton.isVisible = meterImages.containsKey(meter.id) // Show view only if image exists
                    binding.deleteImageButton.isVisible = meterImages.containsKey(meter.id) // Show delete only if image exists
                    binding.editMeterButton.isVisible = false // Hide edit button
                    binding.deleteMeterButton.isVisible = false // Hide delete button
                }
                MainActivity.AppMode.EDITING -> {
                    binding.readingValueInputLayout.isVisible = false // Hide input field
                    binding.cameraButton.isVisible = false // Hide camera button
                    binding.viewImageButton.isVisible = false // Hide view image button
                    binding.deleteImageButton.isVisible = false // Hide delete image button
                    binding.editMeterButton.isVisible = true // Show edit button
                    binding.deleteMeterButton.isVisible = true // Show delete button
                }
            }
        }
    }

    /**
     * Creates and returns a new MeterViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MeterViewHolder(binding)
    }

    /**
     * Binds the data from the Meter object at the specified position to the ViewHolder.
     */
    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    /**
     * Returns a map of meter IDs to their currently entered reading values.
     */
    fun getEnteredReadings(): Map<String, String> {
        return enteredReadings.filterValues { it.isNotBlank() } // Only return non-blank readings
    }

    /**
     * Clears all entered reading values.
     */
    fun clearEnteredReadings() {
        enteredReadings.clear()
        notifyDataSetChanged() // Notify adapter to refresh views
    }

    /**
     * Updates the URI for a meter's image.
     */
    fun updateMeterImageUri(meterId: String, uri: Uri) {
        meterImages[meterId] = uri
        notifyItemChanged(currentList.indexOfFirst { it.id == meterId })
    }

    /**
     * Removes the image URI for a meter.
     */
    fun removeMeterImageUri(meterId: String) {
        meterImages.remove(meterId)
        notifyItemChanged(currentList.indexOfFirst { it.id == meterId })
    }

    /**
     * Returns a map of meter IDs to their associated image URIs.
     */
    fun getMeterImages(): Map<String, Uri> {
        return meterImages.toMap()
    }

    /**
     * Clears all locally stored meter image URIs.
     */
    fun clearMeterImages() {
        meterImages.clear()
        notifyDataSetChanged() // Notify adapter to refresh views
    }


    companion object {
        /**
         * DiffUtil.ItemCallback implementation for efficient list updates.
         */
        private val DiffCallback = object : DiffUtil.ItemCallback<Meter>() {
            override fun areItemsTheSame(oldItem: Meter, newItem: Meter): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Meter, newItem: Meter): Boolean {
                // Compare all relevant fields to determine if content has changed
                return oldItem == newItem && oldItem.last_reading == newItem.last_reading &&
                        oldItem.last_reading_date == newItem.last_reading_date
                // Add other fields if their changes should trigger a re-bind
            }
        }
    }
}
