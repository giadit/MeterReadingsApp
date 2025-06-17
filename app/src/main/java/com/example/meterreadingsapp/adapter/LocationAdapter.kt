package com.example.meterreadingsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.data.Location // Import new Location data class
import com.example.meterreadingsapp.databinding.ItemLocationBinding // Will rename/update this layout

/**
 * RecyclerView adapter for displaying a list of Location objects.
 * Uses DiffUtil for efficient updates to the list.
 *
 * @param onItemClicked Lambda function to be invoked when a location item is clicked.
 */
class LocationAdapter(private val onItemClicked: (Location) -> Unit) :
    ListAdapter<Location, LocationAdapter.LocationViewHolder>(DiffCallback) {

    /**
     * ViewHolder for individual Location items.
     * Binds data from a Location object to the layout views.
     *
     * @param binding The ViewBinding object for item_location.xml.
     */
    class LocationViewHolder(private var binding: ItemLocationBinding) : // Changed to ItemLocationBinding
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds a Location object to the views in the ViewHolder.
         * @param location The Location object to bind.
         */
        fun bind(location: Location) {
            // Display location details (address, city, postal code)
            binding.locationAddressTextView.text = location.address
            binding.locationCityPostalTextView.text = "${location.city}, ${location.postal_code}"
        }
    }

    /**
     * Creates and returns a new LocationViewHolder.
     * Inflates the item_location.xml layout for each item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LocationViewHolder(binding).also { viewHolder ->
            // Set up click listener for the entire item view
            binding.root.setOnClickListener {
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    // Invoke the onItemClicked lambda with the clicked Location object
                    onItemClicked(getItem(position))
                }
            }
        }
    }

    /**
     * Binds the data from the Location object at the specified position to the ViewHolder.
     */
    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    companion object {
        /**
         * DiffUtil.ItemCallback implementation for efficient list updates.
         * Helps RecyclerView optimize re-drawing by only updating changed items.
         */
        private val DiffCallback = object : DiffUtil.ItemCallback<Location>() {
            override fun areItemsTheSame(oldItem: Location, newItem: Location): Boolean {
                return oldItem.id == newItem.id // Check if items represent the same underlying entity
            }

            override fun areContentsTheSame(oldItem: Location, newItem: Location): Boolean {
                return oldItem == newItem // Check if the content of the items is the same
            }
        }
    }
}
    