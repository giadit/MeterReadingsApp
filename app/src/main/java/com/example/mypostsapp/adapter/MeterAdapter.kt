package com.example.mypostsapp.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mypostsapp.R // Import R for drawable and color resources
import com.example.mypostsapp.data.Meter
import com.example.mypostsapp.databinding.ItemMeterBinding
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale // FIX: Import Locale

/**
 * RecyclerView adapter for displaying a list of Meter objects in the batch reading view.
 * It manages the state of the editable reading fields and uses DiffUtil for efficient updates.
 *
 * This adapter no longer handles individual item clicks for opening a dialog.
 * Instead, it captures reading values entered by the user.
 */
class MeterAdapter :
    ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    // A map to store the entered reading values, keyed by meter ID.
    // This allows us to retrieve all readings when the "Send" button is clicked.
    // Use ConcurrentHashMap if you anticipate multi-threaded access outside of UI thread,
    // otherwise, HashMap is sufficient as all UI interactions are on main thread.
    private val enteredReadings: MutableMap<String, String> = ConcurrentHashMap()

    /**
     * ViewHolder for individual Meter items.
     * Binds data from a Meter object to the layout views and manages text changes.
     *
     * @param binding The ViewBinding object for item_meter.xml.
     */
    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // TextWatcher to capture input changes in the newReadingValueEditText
        private var currentTextWatcher: TextWatcher? = null

        /**
         * Binds a Meter object to the views in the ViewHolder.
         * @param meter The Meter object to bind.
         */
        fun bind(meter: Meter) {
            binding.apply {
                meterNumberTextView.text = meter.number
                meterTypeTextView.text = "Type: ${meter.type}"
                meterConsumerTextView.text = "Consumer: ${meter.consumer ?: "N/A"}"
                meterEnergyTypeTextView.text = "Energy Type: ${meter.energy_type}"
                meterLastReadingTextView.text = "Last Reading: ${meter.last_reading ?: "N/A"}"
                meterLastReadingDateTextView.text = "Date: ${meter.last_reading_date ?: "N/A"}"

                // Set the hint for the editable field
                newReadingValueEditText.hint = itemView.context.getString(R.string.enter_reading_hint)

                // Remove previous TextWatcher to prevent it from triggering for recycled views
                currentTextWatcher?.let { newReadingValueEditText.removeTextChangedListener(it) }

                // Set the text from our internal map, or clear if no value exists
                newReadingValueEditText.setText(enteredReadings[meter.id])

                // Add new TextWatcher
                currentTextWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                    override fun afterTextChanged(s: Editable?) {
                        // Save the entered text to our map, keyed by meter ID
                        if (s != null && s.toString().isNotBlank()) {
                            enteredReadings[meter.id] = s.toString()
                        } else {
                            enteredReadings.remove(meter.id) // Remove if text is empty/blank
                        }
                    }
                }.also { newReadingValueEditText.addTextChangedListener(it) }


                // Logic to set energy type icon based on meter.energy_type
                // Ensure you have these drawable resources (ic_bolt, ic_flame, ic_gas)
                // and colors (electric_blue, heat_orange, gas_green) defined in your project
                when (meter.energy_type.toLowerCase(Locale.ROOT)) { // Use toLowerCase for case-insensitive comparison
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
                        energyTypeIcon.setImageResource(0) // Clear icon
                        energyTypeIcon.visibility = View.GONE // Hide the ImageView
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
        return MeterViewHolder(binding) // No direct click listener on root anymore
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
        notifyDataSetChanged() // Notify adapter to clear text fields in UI
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
                // If you later add functionality where the adapter itself modifies Meter data,
                // you might need a more granular content check here, or if Meter has mutable properties.
                // For now, simple equality check is sufficient if Meter is truly immutable
                // and its content doesn't change from external updates directly (only through new Meter objects).
                return oldItem == newItem
            }
        }
    }
}
