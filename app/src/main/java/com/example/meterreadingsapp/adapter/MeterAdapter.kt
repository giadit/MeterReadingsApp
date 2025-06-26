package com.example.meterreadingsapp.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.databinding.ItemMeterBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import android.net.Uri // Import Uri

/**
 * RecyclerView adapter for displaying a list of Meter objects in the batch reading view.
 * It manages the state of the editable reading fields and uses DiffUtil for efficient updates.
 *
 * @param onCameraClicked Lambda invoked when the camera button is clicked for a meter,
 * passing the Meter object and the current local image URI (if any).
 * @param onViewImageClicked Lambda invoked when the view image button is clicked for a meter,
 * passing the Meter object and the local image URI.
 */
class MeterAdapter(
    private val onCameraClicked: (Meter, Uri?) -> Unit, // FIX: New lambda for camera button
    private val onViewImageClicked: (Meter, Uri) -> Unit // FIX: New lambda for view image button
) : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    // A map to store the entered reading values, keyed by meter ID.
    private val enteredReadings: MutableMap<String, String> = ConcurrentHashMap()

    // FIX: A map to store the local image URIs, keyed by meter ID.
    // This will hold the URI of the picture taken for each meter.
    private val meterImageUris: MutableMap<String, Uri> = ConcurrentHashMap()

    /**
     * ViewHolder for individual Meter items.
     * Binds data from a Meter object to the layout views and manages text changes.
     *
     * @param binding The ViewBinding object for item_meter.xml.
     * @param cameraClickListener The lambda for camera button clicks.
     * @param viewImageClickListener The lambda for view image button clicks.
     */
    inner class MeterViewHolder(
        private val binding: ItemMeterBinding,
        private val cameraClickListener: (Meter, Uri?) -> Unit, // FIX: Pass camera click listener
        private val viewImageClickListener: (Meter, Uri) -> Unit // FIX: Pass view image click listener
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentTextWatcher: TextWatcher? = null
        private val uiDisplayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        /**
         * Binds a Meter object to the views in the ViewHolder.
         * @param meter The Meter object to bind.
         */
        fun bind(meter: Meter) {
            binding.apply {
                meterNumberTextView.text = meter.number
                meterEnergyTypeTextView.text = "Energy Type: ${meter.energy_type}"
                meterLastReadingTextView.text = meter.last_reading ?: "N/A"

                meterLastReadingDateTextView.text = meter.last_reading_date?.let { dateString ->
                    try {
                        val date = apiDateFormat.parse(dateString)
                        if (date != null) uiDisplayDateFormat.format(date) else "N/A"
                    } catch (e: Exception) {
                        "Invalid Date"
                    }
                } ?: "N/A"

                newReadingValueEditText.hint = itemView.context.getString(R.string.enter_reading_hint)

                currentTextWatcher?.let { newReadingValueEditText.removeTextChangedListener(it) }
                newReadingValueEditText.setText(enteredReadings[meter.id])

                currentTextWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (s != null && s.toString().isNotBlank()) {
                            enteredReadings[meter.id] = s.toString()
                        } else {
                            enteredReadings.remove(meter.id)
                        }
                    }
                }.also { newReadingValueEditText.addTextChangedListener(it) }

                when (meter.energy_type.toLowerCase(Locale.ROOT)) {
                    "electricity" -> {
                        energyTypeIcon.setImageResource(R.drawable.ic_bolt)
                        energyTypeIcon.setColorFilter(itemView.context.getColor(R.color.electric_blue))
                        energyTypeIcon.visibility = View.VISIBLE
                    }
                    "heat" -> {
                        energyTypeIcon.setImageResource(R.drawable.ic_flame)
                        energyTypeIcon.setColorFilter(itemView.context.getColor(R.color.heat_orange))
                        energyTypeIcon.visibility = View.VISIBLE
                    }
                    "gas" -> {
                        energyTypeIcon.setImageResource(R.drawable.ic_gas)
                        energyTypeIcon.setColorFilter(itemView.context.getColor(R.color.gas_green))
                        energyTypeIcon.visibility = View.VISIBLE
                    }
                    else -> {
                        energyTypeIcon.setImageResource(0)
                        energyTypeIcon.visibility = View.GONE
                    }
                }

                // FIX: Set click listeners for the new buttons
                cameraButton.setOnClickListener {
                    // Pass the meter and its current image URI (if any)
                    cameraClickListener(meter, meterImageUris[meter.id])
                }

                // FIX: Enable/Disable viewImageButton based on whether an image exists for this meter
                val hasImage = meterImageUris.containsKey(meter.id)
                viewImageButton.isEnabled = hasImage
                viewImageButton.alpha = if (hasImage) 1.0f else 0.5f // Visually indicate enabled/disabled state
                viewImageButton.setOnClickListener {
                    // Only allow click if an image exists
                    meterImageUris[meter.id]?.let { uri ->
                        viewImageClickListener(meter, uri)
                    }
                }
            }
        }
    }

    /**
     * Creates and returns a new MeterViewHolder.
     * Inflates the item_meter.xml layout for each item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // FIX: Pass the new click listeners to the ViewHolder constructor
        return MeterViewHolder(binding, onCameraClicked, onViewImageClicked)
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
     * Only includes entries where a value has been entered (not blank).
     */
    fun getEnteredReadings(): Map<String, String> {
        return enteredReadings
    }

    /**
     * Clears all entered readings from the internal map.
     * Call this when the "Send" button is pressed to reset the state.
     */
    fun clearEnteredReadings() {
        enteredReadings.clear()
        notifyDataSetChanged()
    }

    /**
     * FIX: Returns a map of meter IDs to their associated local image URIs.
     * Only includes meters for which a picture has been saved.
     */
    fun getMeterImages(): Map<String, Uri> {
        return meterImageUris
    }

    /**
     * FIX: Clears all stored image URIs from the internal map.
     * Call this when the "Send" button is pressed (after images are uploaded).
     */
    fun clearMeterImages() {
        meterImageUris.clear()
        notifyDataSetChanged()
    }

    /**
     * FIX: Updates the local image URI for a specific meter.
     * @param meterId The ID of the meter.
     * @param imageUri The local Uri of the captured image.
     */
    fun updateMeterImageUri(meterId: String, imageUri: Uri) {
        meterImageUris[meterId] = imageUri
        notifyItemChanged(currentList.indexOfFirst { it.id == meterId }) // Notify adapter to rebind this item
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
                return oldItem == newItem
            }
        }
    }
}
