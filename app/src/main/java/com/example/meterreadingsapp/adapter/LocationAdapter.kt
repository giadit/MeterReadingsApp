package com.example.meterreadingsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.databinding.ItemLocationBinding

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
     * @param clickListener The lambda function to be invoked when the item is clicked.
     */
    inner class LocationViewHolder(
        private var binding: ItemLocationBinding,
        private val clickListener: (Location) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds a Location object to the views in the ViewHolder.
         * @param location The Location object to bind.
         */
        fun bind(location: Location) {
            binding.locationAddressTextView.text = location.name ?: location.address ?: "Unknown Location"
            binding.locationCityPostalTextView.text = buildPostalCodeCityDisplay(location.city, location.postal_code)

            // Set up click listener for the entire item view
            binding.root.setOnClickListener {
                clickListener(location)
            }
        }

        /**
         * Helper to build a readable city and postal code string.
         */
        private fun buildPostalCodeCityDisplay(city: String?, postalCode: String?): String {
            val parts = mutableListOf<String>()
            city?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
            postalCode?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

            return if (parts.isNotEmpty()) {
                parts.joinToString(", ")
            } else {
                "N/A"
            }
        }
    }

    /**
     * Creates and returns a new LocationViewHolder.
     * Inflates the item_location.xml layout for each item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LocationViewHolder(binding, onItemClicked)
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
         */
        private val DiffCallback = object : DiffUtil.ItemCallback<Location>() {
            override fun areItemsTheSame(oldItem: Location, newItem: Location): Boolean { // FIX: Corrected newItem type to Location
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Location, newItem: Location): Boolean { // FIX: Corrected newItem type to Location
                return oldItem == newItem
            }
        }
    }
}
