package com.example.meterreadingsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.data.Building
import com.example.meterreadingsapp.databinding.ItemLocationBinding

/**
 * RecyclerView adapter for displaying a list of Building objects.
 * Uses DiffUtil for efficient updates to the list.
 * Note: It reuses the item_location.xml layout for visual consistency.
 *
 * @param onItemClicked Lambda function to be invoked when a building item is clicked.
 */
class BuildingAdapter(private val onItemClicked: (Building) -> Unit) :
    ListAdapter<Building, BuildingAdapter.BuildingViewHolder>(DiffCallback) {

    /**
     * ViewHolder for individual Building items.
     */
    inner class BuildingViewHolder(
        private var binding: ItemLocationBinding,
        private val clickListener: (Building) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds a Building object to the views in the ViewHolder.
         * @param building The Building object to bind.
         */
        fun bind(building: Building) {
            // Use the building's name and street address for display
            binding.locationAddressTextView.text = building.name
            binding.locationCityPostalTextView.text = "${building.street}, ${building.postal_code ?: ""} ${building.city ?: ""}".trim()

            binding.root.setOnClickListener {
                clickListener(building)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuildingViewHolder {
        val binding = ItemLocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return BuildingViewHolder(binding, onItemClicked)
    }

    override fun onBindViewHolder(holder: BuildingViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Building>() {
            override fun areItemsTheSame(oldItem: Building, newItem: Building): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Building, newItem: Building): Boolean {
                return oldItem == newItem
            }
        }
    }
}
