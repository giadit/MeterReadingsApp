package com.example.meterreadingsapp.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.databinding.ItemMeterBinding
import com.example.meterreadingsapp.MainActivity.AppMode

/**
 * RecyclerView adapter for displaying a list of Meter objects.
 * Handles user input for readings, displays meter details, and manages image capture/display.
 * Uses DiffUtil for efficient updates to the list.
 *
 * @param onCameraClicked Lambda invoked when the camera icon is clicked for a meter.
 * @param onViewImageClicked Lambda invoked when the view image icon is clicked for a meter.
 * @param onDeleteImageClicked Lambda invoked when the delete image icon is clicked for a meter.
 * @param onEditMeterClicked Lambda invoked when the edit meter icon is clicked for a meter.
 * @param onDeleteMeterClicked Lambda invoked when the delete meter icon is clicked for a meter.
 * @param currentMode Lambda that provides the current [AppMode] (READINGS or EDITING).
 */
class MeterAdapter(
    private val onCameraClicked: (Meter, Uri?) -> Unit,
    private val onViewImageClicked: (Meter, Uri) -> Unit,
    private val onDeleteImageClicked: (Meter, Uri) -> Unit,
    private val onEditMeterClicked: (Meter) -> Unit,
    private val onDeleteMeterClicked: (Meter) -> Unit,
    private val currentMode: () -> AppMode
) : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    // Map to store entered readings (meterId to reading value)
    private val enteredReadings: MutableMap<String, String> = mutableMapOf()

    // Map to store image URIs for meters (meterId to Uri) - for *newly taken* images, only locally managed
    private val meterImages: MutableMap<String, Uri> = mutableMapOf()

    /**
     * ViewHolder for individual Meter items.
     * Binds data from a Meter object to the layout views.
     *
     * @param binding The ViewBinding object for item_meter.xml.
     */
    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds a Meter object to the views in the ViewHolder.
         * @param meter The Meter object to bind.
         */
        fun bind(meter: Meter) {
            binding.meterNumberTextView.text = meter.number
            binding.meterEnergyTypeTextView.text = meter.energy_type

            // Pre-fill reading if available from map
            binding.newReadingValueEditText.setText(enteredReadings[meter.id])

            // Set up text change listener to update enteredReadings map
            binding.newReadingValueEditText.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) {
                    // When focus is lost, save the text
                    val editText = v as EditText
                    enteredReadings[meter.id] = editText.text.toString()
                }
            }

            // Determine if a newly taken image exists for this meter
            val newlyTakenImageUri = meterImages[meter.id]
            val hasImage = newlyTakenImageUri != null

            // Set up camera button click listener
            binding.cameraButton.setOnClickListener {
                // Pass newly taken image URI if available, otherwise null
                onCameraClicked(meter, newlyTakenImageUri)
            }

            // Set up view image button click listener
            binding.viewImageButton.setOnClickListener {
                if (newlyTakenImageUri != null) {
                    onViewImageClicked(meter, newlyTakenImageUri)
                } else {
                    Toast.makeText(binding.root.context, R.string.no_image_to_view, Toast.LENGTH_SHORT).show()
                }
            }

            // Set up delete image button click listener
            binding.deleteImageButton.setOnClickListener {
                if (newlyTakenImageUri != null) {
                    // If it's a newly taken image, call the delete callback
                    onDeleteImageClicked(meter, newlyTakenImageUri)
                } else {
                    Toast.makeText(binding.root.context, R.string.no_image_to_delete, Toast.LENGTH_SHORT).show()
                }
            }

            // Ensure all buttons are always visible
            binding.cameraButton.visibility = View.VISIBLE
            binding.viewImageButton.visibility = View.VISIBLE
            binding.deleteImageButton.visibility = View.VISIBLE

            // Always set the camera icon resource to ensure it's present
            binding.cameraButton.setImageResource(R.drawable.ic_camera_white_24) // Corrected drawable name

            // Adjust tint and enabled state based on whether an image exists
            // Camera button is always usable, so its icon should remain white.
            binding.cameraButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.white))

            if (hasImage) {
                // Image exists: View/Delete enabled and tinted white
                binding.viewImageButton.isEnabled = true
                binding.viewImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.white))
                binding.deleteImageButton.isEnabled = true
                binding.deleteImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.white))
            } else {
                // No image: View/Delete disabled and tinted grey
                binding.viewImageButton.isEnabled = false
                binding.viewImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.darker_gray)) // Grey tint
                binding.deleteImageButton.isEnabled = false
                binding.deleteImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.darker_gray)) // Grey tint
            }

            // Adjust UI based on current app mode
            val mode = currentMode()
            binding.readingValueInputLayout.isEnabled = (mode == AppMode.READINGS)
            binding.cameraButton.isEnabled = (mode == AppMode.READINGS)
            // Assuming sendIcon, editMeterButton, deleteMeterButton are handled by MainActivity
            // and their visibility is controlled there based on AppMode.
            // If they are directly in item_meter.xml and need to be controlled by adapter,
            // ensure their IDs are correct and add logic here.
        }
    }

    /**
     * Creates and returns a new MeterViewHolder.
     * Inflates the item_meter.xml layout for each item.
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
     * Returns the map of entered readings.
     * @return A map where keys are meter IDs and values are the entered reading strings.
     */
    fun getEnteredReadings(): Map<String, String> {
        return enteredReadings
    }

    /**
     * Returns the map of meter images.
     * @return A map where keys are meter IDs and values are the image URIs.
     */
    fun getMeterImages(): Map<String, Uri> {
        return meterImages
    }

    /**
     * Updates the image URI for a specific meter.
     * This is for newly taken pictures.
     * @param meterId The ID of the meter.
     * @param uri The new image URI.
     */
    fun updateMeterImageUri(meterId: String, uri: Uri) {
        meterImages[meterId] = uri
        // Notify item changed to rebind and update UI (e.g., show/hide view/delete buttons)
        val index = currentList.indexOfFirst { it.id == meterId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    /**
     * Removes the image URI for a specific meter.
     * This is for newly taken pictures that are being discarded or after successful upload.
     * @param meterId The ID of the meter.
     */
    fun removeMeterImageUri(meterId: String) {
        meterImages.remove(meterId)
        // Notify item changed to rebind and update UI
        val index = currentList.indexOfFirst { it.id == meterId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    /**
     * Clears all entered readings from the map.
     */
    fun clearEnteredReadings() {
        enteredReadings.clear()
        notifyDataSetChanged() // Notify all items to clear their EditTexts
    }

    /**
     * Clears all stored meter images from the map.
     * This should be called after all images are processed (uploaded or discarded).
     */
    fun clearMeterImages() {
        meterImages.clear()
        notifyDataSetChanged() // Notify all items to update image button visibility
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
                return oldItem == newItem // Data class equals handles this
            }
        }
    }
}
