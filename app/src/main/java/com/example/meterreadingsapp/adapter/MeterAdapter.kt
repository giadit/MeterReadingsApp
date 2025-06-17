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
import java.text.SimpleDateFormat // Import SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * RecyclerView adapter for displaying a list of Meter objects in the batch reading view.
 * It manages the state of the editable reading fields and uses DiffUtil for efficient updates.
 */
class MeterAdapter :
    ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    // A map to store the entered reading values, keyed by meter ID.
    private val enteredReadings: MutableMap<String, String> = ConcurrentHashMap()

    /**
     * ViewHolder for individual Meter items.
     * Binds data from a Meter object to the layout views and manages text changes.
     *
     * @param binding The ViewBinding object for item_meter.xml.
     */
    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var currentTextWatcher: TextWatcher? = null
        // Define a SimpleDateFormat for parsing and formatting dates in dd/MM/yyyy format for UI display
        private val uiDisplayDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US) // For parsing dates from API

        /**
         * Binds a Meter object to the views in the ViewHolder.
         * @param meter The Meter object to bind.
         */
        fun bind(meter: Meter) {
            binding.apply {
                meterNumberTextView.text = meter.number
                meterEnergyTypeTextView.text = "Energy Type: ${meter.energy_type}"
                meterLastReadingTextView.text = meter.last_reading ?: "N/A"

                // Format meter.last_reading_date for display
                meterLastReadingDateTextView.text = meter.last_reading_date?.let { dateString ->
                    try {
                        val date = apiDateFormat.parse(dateString)
                        if (date != null) uiDisplayDateFormat.format(date) else "N/A"
                    } catch (e: Exception) {
                        "Invalid Date" // Handle parsing errors gracefully
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
            }
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
