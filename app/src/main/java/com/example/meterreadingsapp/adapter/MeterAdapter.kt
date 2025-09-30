package com.example.meterreadingsapp.adapter

import android.content.res.ColorStateList
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.databinding.ItemMeterBinding
import com.example.meterreadingsapp.MainActivity.AppMode
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*

class MeterAdapter(
    private val onCameraClicked: (Meter, Uri?) -> Unit,
    private val onViewImageClicked: (Meter, Uri) -> Unit,
    private val onDeleteImageClicked: (Meter, Uri) -> Unit,
    private val onEditMeterClicked: (Meter) -> Unit,
    private val onDeleteMeterClicked: (Meter) -> Unit,
    private val onExchangeMeterClicked: (Meter) -> Unit,
    private val currentMode: () -> AppMode
) : ListAdapter<Meter, MeterAdapter.MeterViewHolder>(DiffCallback) {

    private val enteredReadings: MutableMap<String, String> = mutableMapOf()
    private val meterImages: MutableMap<String, Uri> = mutableMapOf()

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val uiDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val todayDateString = apiDateFormat.format(Date())

    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val meter = getItem(adapterPosition)
                    enteredReadings[meter.id] = s.toString()
                    updateVisualState(meter)
                }
            }
        }

        fun bind(meter: Meter) {
            binding.meterNumberTextView.text = meter.number
            binding.meterEnergyTypeTextView.text = meter.energyType
            binding.meterLastReadingTextView.text = if (meter.lastReading.isNullOrBlank()) "N/A" else meter.lastReading
            binding.meterLastReadingDateTextView.text = meter.lastReadingDate?.let { try { apiDateFormat.parse(it)?.let { date -> uiDateFormat.format(date) } ?: "N/A" } catch (e: Exception) { "N/A" } } ?: "N/A"

            updateVisualState(meter)

            binding.newReadingValueEditText.removeTextChangedListener(textWatcher)
            binding.newReadingValueEditText.setText(enteredReadings[meter.id])
            binding.newReadingValueEditText.addTextChangedListener(textWatcher)

            val newlyTakenImageUri = meterImages[meter.id]
            val hasImage = newlyTakenImageUri != null

            binding.changeMeterButton.setOnClickListener { onExchangeMeterClicked(meter) }
            binding.cameraButton.setOnClickListener { onCameraClicked(meter, newlyTakenImageUri) }
            binding.viewImageButton.setOnClickListener { newlyTakenImageUri?.let { uri -> onViewImageClicked(meter, uri) } ?: Toast.makeText(binding.root.context, R.string.no_image_to_view, Toast.LENGTH_SHORT).show() }
            binding.deleteImageButton.setOnClickListener { newlyTakenImageUri?.let { uri -> onDeleteImageClicked(meter, uri) } ?: Toast.makeText(binding.root.context, R.string.no_image_to_delete, Toast.LENGTH_SHORT).show() }

            updateButtonStates(hasImage)
            val mode = currentMode()
            binding.readingValueInputLayout.isEnabled = (mode == AppMode.READINGS)
            binding.cameraButton.isEnabled = (mode == AppMode.READINGS)
        }

        private fun updateVisualState(meter: Meter) {
            val context = binding.root.context
            val isReadToday = meter.lastReadingDate == todayDateString
            val hasNewReading = !enteredReadings[meter.id].isNullOrBlank()

            binding.checkmarkIcon.visibility = if (isReadToday) View.VISIBLE else View.GONE

            // CORRECTED: Cast to MaterialCardView
            val cardView = binding.root as MaterialCardView
            if (isReadToday || hasNewReading) {
                cardView.strokeColor = ContextCompat.getColor(context, R.color.bright_orange)
            } else {
                cardView.strokeColor = ContextCompat.getColor(context, android.R.color.transparent)
            }

            when (meter.energyType?.lowercase(Locale.ROOT)) {
                "strom" -> {
                    binding.energyTypeIcon.setImageResource(R.drawable.ic_bolt)
                    binding.energyTypeIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.electric_blue))
                }
                "wärme" -> {
                    binding.energyTypeIcon.setImageResource(R.drawable.ic_fire)
                    binding.energyTypeIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.heat_orange))
                }
                "gas" -> {
                    binding.energyTypeIcon.setImageResource(R.drawable.ic_gas_meter)
                    binding.energyTypeIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gas_green))
                }
                else -> {
                    binding.energyTypeIcon.setImageDrawable(null)
                }
            }
        }

        private fun updateButtonStates(hasImage: Boolean) {
            val context = binding.root.context
            binding.viewImageButton.isEnabled = hasImage
            binding.deleteImageButton.isEnabled = hasImage
            val activeColor = ContextCompat.getColor(context, android.R.color.white)
            val inactiveColor = ContextCompat.getColor(context, android.R.color.darker_gray)
            binding.viewImageButton.setColorFilter(if (hasImage) activeColor else inactiveColor)
            binding.deleteImageButton.setColorFilter(if (hasImage) activeColor else inactiveColor)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder { val binding = ItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false); return MeterViewHolder(binding) }
    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) { holder.bind(getItem(position)) }
    fun getEnteredReadings(): Map<String, String> = enteredReadings
    fun getMeterImages(): Map<String, Uri> = meterImages
    fun updateMeterImageUri(meterId: String, uri: Uri) { meterImages[meterId] = uri; val index = currentList.indexOfFirst { it.id == meterId }; if (index != -1) notifyItemChanged(index) }
    fun removeMeterImageUri(meterId: String) { meterImages.remove(meterId); val index = currentList.indexOfFirst { it.id == meterId }; if (index != -1) notifyItemChanged(index) }
    fun clearEnteredReadings() { enteredReadings.clear(); notifyDataSetChanged() }
    fun clearMeterImages() { meterImages.clear(); notifyDataSetChanged() }
    companion object { private val DiffCallback = object : DiffUtil.ItemCallback<Meter>() { override fun areItemsTheSame(oldItem: Meter, newItem: Meter): Boolean { return oldItem.id == newItem.id } override fun areContentsTheSame(oldItem: Meter, newItem: Meter): Boolean { return oldItem == newItem } } }
}

