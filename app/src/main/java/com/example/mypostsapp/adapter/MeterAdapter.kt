package com.example.mypostsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mypostsapp.R // Import your R file to access drawable resources
import com.example.mypostsapp.data.Meter
import com.example.mypostsapp.databinding.ItemMeterBinding

/**
 * RecyclerView adapter for displaying a list of Meter objects.
 * Uses DiffUtil for efficient updates to the list.
 *
 * @param onItemClicked Lambda function to be invoked when a meter item is clicked.
 */
class MeterAdapter(private val onItemClicked: (Meter) -> Unit) :
    ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    /**
     * ViewHolder for individual Meter items.
     * Binds data from a Meter object to the layout views.
     *
     * @param binding The ViewBinding object for item_meter.xml.
     */
    class MeterViewHolder(private var binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds a Meter object to the views in the ViewHolder.
         * @param meter The Meter object to bind.
         */
        fun bind(meter: Meter) {
            // Display meter number and type
            binding.meterNumberTextView.text = meter.number
            binding.meterTypeTextView.text = meter.type
            binding.meterConsumerTextView.text = meter.consumer ?: "N/A"
            binding.meterEnergyTypeTextView.text = meter.energy_type
            binding.meterLastReadingTextView.text = meter.last_reading?.let { "Last Reading: $it" } ?: "No Reading"
            binding.meterLastReadingDateTextView.text = meter.last_reading_date?.let { "Date: $it" } ?: ""

            // Logic to set energy type icon based on meter.energy_type (NEW)
            when (meter.energy_type) {
                "Electricity" -> {
                    binding.energyTypeIcon.setImageResource(R.drawable.ic_bolt) // Use your lightning bolt icon
                    binding.energyTypeIcon.setColorFilter(itemView.context.getColor(R.color.electric_blue)) // Example color
                }
                "Heat" -> {
                    binding.energyTypeIcon.setImageResource(R.drawable.ic_flame) // Use your flame icon
                    binding.energyTypeIcon.setColorFilter(itemView.context.getColor(R.color.heat_orange)) // Example color
                }
                "Gas" -> {
                    binding.energyTypeIcon.setImageResource(R.drawable.ic_gas) // Assuming you'll add a gas icon
                    binding.energyTypeIcon.setColorFilter(itemView.context.getColor(R.color.gas_green)) // Example color
                }
                else -> {
                    // Fallback for unknown energy types (e.g., hide or set a generic icon)
                    binding.energyTypeIcon.setImageResource(0) // Set to 0 to clear any icon
                    binding.energyTypeIcon.visibility = android.view.View.GONE // Hide the ImageView
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
        return MeterViewHolder(binding).also { viewHolder ->
            // Set up click listener for the entire item view
            binding.root.setOnClickListener {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Invoke the onItemClicked lambda with the clicked Meter object
                    onItemClicked(getItem(position))
                }
            }
        }
    }

    /**
     * Binds the data from the Meter object at the specified position to the ViewHolder.
     */
    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    companion object {
        /**
         * DiffUtil.ItemCallback implementation for efficient list updates.
         * Helps RecyclerView optimize re-drawing by only updating changed items.
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
