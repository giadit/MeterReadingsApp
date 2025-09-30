package com.example.meterreadingsapp.adapter

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.databinding.ItemMeterBinding
import com.example.meterreadingsapp.MainActivity.AppMode

class MeterAdapter(
    private val onCameraClicked: (Meter, Uri?) -> Unit,
    private val onViewImageClicked: (Meter, Uri) -> Unit,
    private val onDeleteImageClicked: (Meter, Uri) -> Unit,
    private val onEditMeterClicked: (Meter) -> Unit,
    private val onDeleteMeterClicked: (Meter) -> Unit,
    private val currentMode: () -> AppMode
) : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    private val enteredReadings: MutableMap<String, String> = mutableMapOf()
    private val meterImages: MutableMap<String, Uri> = mutableMapOf()

    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // A TextWatcher to avoid removing and re-adding the listener repeatedly
        private val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Save the text to the map in real-time
                val meter = getItem(adapterPosition)
                enteredReadings[meter.id] = s.toString()
            }
        }

        fun bind(meter: Meter) {
            binding.meterNumberTextView.text = meter.number
            binding.meterEnergyTypeTextView.text = meter.energyType

            // --- THIS IS THE CORRECTED LOGIC ---
            // Remove the old listener to prevent multiple listeners on recycled views
            binding.newReadingValueEditText.removeTextChangedListener(textWatcher)
            // Pre-fill the reading from our map
            binding.newReadingValueEditText.setText(enteredReadings[meter.id])
            // Add the listener to capture changes in real-time
            binding.newReadingValueEditText.addTextChangedListener(textWatcher)
            // --- END OF CORRECTION ---

            val newlyTakenImageUri = meterImages[meter.id]
            val hasImage = newlyTakenImageUri != null

            binding.cameraButton.setOnClickListener {
                onCameraClicked(meter, newlyTakenImageUri)
            }

            binding.viewImageButton.setOnClickListener {
                newlyTakenImageUri?.let { uri ->
                    onViewImageClicked(meter, uri)
                } ?: Toast.makeText(binding.root.context, R.string.no_image_to_view, Toast.LENGTH_SHORT).show()
            }

            binding.deleteImageButton.setOnClickListener {
                newlyTakenImageUri?.let { uri ->
                    onDeleteImageClicked(meter, uri)
                } ?: Toast.makeText(binding.root.context, R.string.no_image_to_delete, Toast.LENGTH_SHORT).show()
            }

            binding.cameraButton.visibility = View.VISIBLE
            binding.viewImageButton.visibility = View.VISIBLE
            binding.deleteImageButton.visibility = View.VISIBLE
            binding.cameraButton.setImageResource(R.drawable.ic_camera_white_24)
            binding.cameraButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.white))

            if (hasImage) {
                binding.viewImageButton.isEnabled = true
                binding.viewImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.white))
                binding.deleteImageButton.isEnabled = true
                binding.deleteImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.white))
            } else {
                binding.viewImageButton.isEnabled = false
                binding.viewImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.darker_gray))
                binding.deleteImageButton.isEnabled = false
                binding.deleteImageButton.setColorFilter(ContextCompat.getColor(binding.root.context, android.R.color.darker_gray))
            }

            val mode = currentMode()
            binding.readingValueInputLayout.isEnabled = (mode == AppMode.READINGS)
            binding.cameraButton.isEnabled = (mode == AppMode.READINGS)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MeterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getEnteredReadings(): Map<String, String> = enteredReadings
    fun getMeterImages(): Map<String, Uri> = meterImages

    fun updateMeterImageUri(meterId: String, uri: Uri) {
        meterImages[meterId] = uri
        val index = currentList.indexOfFirst { it.id == meterId }
        if (index != -1) notifyItemChanged(index)
    }

    fun removeMeterImageUri(meterId: String) {
        meterImages.remove(meterId)
        val index = currentList.indexOfFirst { it.id == meterId }
        if (index != -1) notifyItemChanged(index)
    }

    fun clearEnteredReadings() {
        enteredReadings.clear()
        notifyDataSetChanged()
    }

    fun clearMeterImages() {
        meterImages.clear()
        notifyDataSetChanged()
    }

    companion object {
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

