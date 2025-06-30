package com.example.meterreadingsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.databinding.ItemProjectBinding

/**
 * RecyclerView adapter for displaying a list of Project objects.
 * Uses DiffUtil for efficient updates to the list.
 *
 * @param onItemClicked Lambda function to be invoked when a project item is clicked.
 */
class ProjectAdapter(private val onItemClicked: (Project) -> Unit) :
    ListAdapter<Project, ProjectAdapter.ProjectViewHolder>(DiffCallback) {

    /**
     * ViewHolder for individual Project items.
     * Binds data from a Project object to the layout views.
     *
     * @param binding The ViewBinding object for item_project.xml.
     * @param clickListener The lambda function to be invoked when the item is clicked.
     */
    inner class ProjectViewHolder(
        private val binding: ItemProjectBinding,
        private val clickListener: (Project) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * Binds a Project object to the views in the ViewHolder.
         * @param project The Project object to bind.
         */
        fun bind(project: Project) {
            binding.projectNameTextView.text = project.name ?: "Unknown Project"
            // REMOVED: binding.projectAddressTextView.text = project.address ?: "No Address Provided"
            // REMOVED: binding.projectStatusTextView.text = "Status: ${project.status ?: "N/A"}"
            binding.projectResponsibleTextView.text = "Responsible: ${project.responsible ?: "N/A"}"
            binding.projectMeterCountTextView.text = "Meters: ${project.metersCount ?: 0}"
            binding.projectBuildingCountTextView.text = "Buildings: ${project.buildingsCount ?: 0}" // This now uses the calculated count from ViewModel

            // Set up click listener for the entire item view
            binding.root.setOnClickListener {
                clickListener(project)
            }
        }
    }

    /**
     * Creates and returns a new ProjectViewHolder.
     * Inflates the item_project.xml layout for each item.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val binding = ItemProjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProjectViewHolder(binding, onItemClicked)
    }

    /**
     * Binds the data from the Project object at the specified position to the ViewHolder.
     */
    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val current = getItem(position)
        holder.bind(current)
    }

    companion object {
        /**
         * DiffUtil.ItemCallback implementation for efficient list updates.
         */
        private val DiffCallback = object : DiffUtil.ItemCallback<Project>() {
            override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean {
                return oldItem == newItem
            }
        }
    }
}
