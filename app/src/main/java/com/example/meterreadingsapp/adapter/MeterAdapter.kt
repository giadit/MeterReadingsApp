package com.example.meterreadingsapp.adapter

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast // ADDED: This line fixes the build error
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.databinding.ItemMeterBinding
import com.example.meterreadingsapp.MainActivity.AppMode
import java.text.SimpleDateFormat
import java.util.Locale

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

    // Date formatting helpers
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val uiDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val meter = getItem(adapterPosition)
                    enteredReadings[meter.id] = s.toString()
                }
            }
        }

        fun bind(meter: Meter) {
            // --- DATA BINDING FOR METER INFO ---
            binding.meterNumberTextView.text = meter.number
            binding.meterEnergyTypeTextView.text = meter.energyType

            // --- THIS IS THE NEWLY ADDED LOGIC ---
            // Display the last reading value, or "N/A" if it's null or blank
            binding.meterLastReadingTextView.text = if (meter.lastReading.isNullOrBlank()) "N/A" else meter.lastReading

            // Parse the date from the API format and display it in the UI format
            binding.meterLastReadingDateTextView.text = meter.lastReadingDate?.let {
                try {
                    val date = apiDateFormat.parse(it)
                    if (date != null) uiDateFormat.format(date) else "N/A"
                } catch (e: Exception) {
                    "N/A" // Handle parsing errors
                }
            } ?: "N/A"
            // --- END OF NEW LOGIC ---


            binding.newReadingValueEditText.removeTextChangedListener(textWatcher)
            binding.newReadingValueEditText.setText(enteredReadings[meter.id])
            binding.newReadingValueEditText.addTextChangedListener(textWatcher)

            // --- Image button logic (unchanged) ---
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

