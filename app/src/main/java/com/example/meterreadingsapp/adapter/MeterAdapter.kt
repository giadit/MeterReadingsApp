package com.example.meterreadingsapp.adapter

import android.content.res.ColorStateList
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterObis
import com.example.meterreadingsapp.data.MeterWithObisPoints
import com.example.meterreadingsapp.data.ObisCode
import com.example.meterreadingsapp.databinding.ItemMeterBinding
import com.example.meterreadingsapp.MainActivity.AppMode
import com.google.android.material.card.MaterialCardView
// UPDATED: We must import TextInputEditText
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MeterAdapter(
    private val onCameraClicked: (Meter, Uri?) -> Unit,
    private val onViewImageClicked: (Meter, Uri) -> Unit,
    private val onDeleteImageClicked: (Meter, Uri) -> Unit,
    private val onEditMeterClicked: (Meter) -> Unit,
    private val onDeleteMeterClicked: (Meter) -> Unit,
    private val onExchangeMeterClicked: (Meter) -> Unit,
    private val currentMode: () -> AppMode
) : ListAdapter<MeterWithObisPoints, MeterAdapter.MeterViewHolder>(DiffCallback) {

    companion object {
        // Key used for meters that don't have OBIS points or use the single fallback field
        const val SINGLE_READING_KEY = "single_reading"

        private val DiffCallback = object : DiffUtil.ItemCallback<MeterWithObisPoints>() {
            override fun areItemsTheSame(oldItem: MeterWithObisPoints, newItem: MeterWithObisPoints): Boolean {
                return oldItem.meter.id == newItem.meter.id
            }
            override fun areContentsTheSame(oldItem: MeterWithObisPoints, newItem: MeterWithObisPoints): Boolean {
                return oldItem == newItem // Relies on data class equals implementation
            }
        }
    }

    // UPDATED: Store readings per OBIS point (meterObis.id or SINGLE_READING_KEY) for each meter (meter.id)
    // Map<MeterID, Map<ObisOrSingleKey, ReadingValue>>
    private val enteredObisReadings: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    private val meterImages: MutableMap<String, Uri> = mutableMapOf()
    private var allObisCodes: Map<String, ObisCode> = emptyMap() // Map<ObisCodeID, ObisCode>

    // UPDATED: Keep track of expanded items
    private val expandedItems: MutableSet<String> = mutableSetOf()

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val uiDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val todayDateString = apiDateFormat.format(Date())

    // UPDATED: Function to receive the list of all OBIS codes from the Activity/ViewModel
    fun setObisCodes(codes: List<ObisCode>) {
        allObisCodes = codes.associateBy { it.id }
        // We might need to refresh visible items if codes arrive after binding
        notifyDataSetChanged() // Use more specific updates if performance is an issue
    }

    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Store watchers to remove them later
        private val watchers = mutableMapOf<EditText, TextWatcher>()

        fun bind(meterWithObis: MeterWithObisPoints) {
            val meter = meterWithObis.meter
            val context = binding.root.context
            val isExpanded = expandedItems.contains(meter.id)

            // --- Bind Header Data ---
            binding.meterNumberTextView.text = meter.number
            binding.meterEnergyTypeTextView.text = meter.energyType ?: "N/A"
            binding.meterLastReadingTextView.text = if (meter.lastReading.isNullOrBlank()) "N/A" else meter.lastReading
            binding.meterLastReadingDateTextView.text = meter.lastReadingDate?.let {
                try {
                    apiDateFormat.parse(it)?.let { date -> uiDateFormat.format(date) } ?: "N/A"
                } catch (e: Exception) { "N/A" }
            } ?: "N/A"

            // --- Update Visual State (Color, Icon, Stroke) ---
            updateVisualState(meter)

            // --- Setup Expand/Collapse ---
            binding.collapsibleContent.isVisible = isExpanded
            binding.expandCollapseButton.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            val clickableView = binding.headerContent // Or binding.root or binding.expandCollapseButton
            clickableView.setOnClickListener {
                toggleExpansion(meter.id)
            }
            binding.expandCollapseButton.setOnClickListener { // Ensure button also toggles
                toggleExpansion(meter.id)
            }

            // --- Clear previous dynamic views and watchers ---
            binding.obisReadingsContainer.removeAllViews()
            watchers.forEach { (editText, watcher) -> editText.removeTextChangedListener(watcher) }
            watchers.clear()

            // --- Dynamically Create Reading Input Fields ---
            val meterReadings = enteredObisReadings.getOrPut(meter.id) { mutableMapOf() }

            if (meterWithObis.obisPoints.isNotEmpty()) {
                // Hide the fallback single reading input layout
                binding.readingValueInputLayout.visibility = View.GONE

                meterWithObis.obisPoints.forEach { meterObis ->
                    val obisCode = allObisCodes[meterObis.obisCodeId]

                    // UPDATED: Combine description and code for the hint as requested
                    val description = obisCode?.description?.takeIf { it.isNotBlank() }
                    val code = obisCode?.code?.takeIf { it.isNotBlank() }
                    val layoutHint = when {
                        !description.isNullOrBlank() && !code.isNullOrBlank() -> "$description [$code]"
                        !description.isNullOrBlank() -> description
                        !code.isNullOrBlank() -> code
                        else -> "Reading"
                    }

                    // Create TextInputLayout and EditText dynamically
                    val textInputLayout = TextInputLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).also {
                            val marginInPixels = (8 * context.resources.displayMetrics.density).roundToInt()
                            it.topMargin = marginInPixels
                        }
                        // FIXED: Set isHintEnabled to false to match your XML
                        isHintEnabled = false
                        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE

                        // UPDATED: Use the stroke color selector to match the original field
                        try {
                            // FIXED: Use the explicit setter 'setBoxStrokeColorStateList'
                            setBoxStrokeColorStateList(ContextCompat.getColorStateList(context, R.drawable.text_input_stroke_color_selector))
                        } catch (e: Exception) {
                            Log.e("MeterAdapter", "R.drawable.text_input_stroke_color_selector not found, using default color.")
                            boxStrokeColor = ContextCompat.getColor(context, R.color.bright_orange)
                        }
                    }

                    // FIXED: This is the critical change.
                    // We must use TextInputEditText for Material styles to apply correctly.
                    val editText = TextInputEditText(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        // FIXED: Set the hint on the EditText, not the layout
                        hint = layoutHint
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                        setText(meterReadings[meterObis.id] ?: "") // Restore saved value
                    }

                    // Add EditText to TextInputLayout
                    textInputLayout.addView(editText)

                    // Add TextInputLayout to the container
                    binding.obisReadingsContainer.addView(textInputLayout)

                    // Add TextWatcher
                    val textWatcher = object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            meterReadings[meterObis.id] = s.toString()
                            updateVisualState(meter) // Update stroke color if needed
                        }
                    }
                    editText.addTextChangedListener(textWatcher)
                    watchers[editText] = textWatcher // Store watcher for removal
                }
            } else {
                // Show the fallback single reading input layout
                binding.readingValueInputLayout.visibility = View.VISIBLE
                val editText = binding.newReadingValueEditText
                editText.setText(meterReadings[SINGLE_READING_KEY] ?: "") // Restore saved value

                // Add TextWatcher for the single field
                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        meterReadings[SINGLE_READING_KEY] = s.toString()
                        updateVisualState(meter) // Update stroke color if needed
                    }
                }
                editText.addTextChangedListener(textWatcher)
                watchers[editText] = textWatcher // Store watcher for removal
            }


            // --- Image Handling ---
            val newlyTakenImageUri = meterImages[meter.id]
            val hasImage = newlyTakenImageUri != null
            updateButtonStates(hasImage) // Update image button states

            // --- Button Click Listeners ---
            binding.changeMeterButton.setOnClickListener { onExchangeMeterClicked(meter) }
            binding.cameraButton.setOnClickListener { onCameraClicked(meter, newlyTakenImageUri) }
            binding.viewImageButton.setOnClickListener {
                newlyTakenImageUri?.let { uri -> onViewImageClicked(meter, uri) }
                    ?: Toast.makeText(context, R.string.no_image_to_view, Toast.LENGTH_SHORT).show()
            }
            binding.deleteImageButton.setOnClickListener {
                newlyTakenImageUri?.let { uri -> onDeleteImageClicked(meter, uri) }
                    ?: Toast.makeText(context, R.string.no_image_to_delete, Toast.LENGTH_SHORT).show()
            }

            // --- Enable/Disable based on App Mode ---
            val mode = currentMode()
            val isReadingMode = (mode == AppMode.READINGS)
            // Enable/disable dynamically created EditTexts
            watchers.keys.forEach { it.isEnabled = isReadingMode }
            // Enable/disable fallback EditText
            binding.newReadingValueEditText.isEnabled = isReadingMode
            binding.readingValueInputLayout.isEnabled = isReadingMode // Also disable the layout itself
            binding.cameraButton.isEnabled = isReadingMode

        }

        private fun toggleExpansion(meterId: String) {
            val position = adapterPosition
            if (position != RecyclerView.NO_POSITION) {
                if (expandedItems.contains(meterId)) {
                    expandedItems.remove(meterId)
                } else {
                    expandedItems.add(meterId)
                }
                notifyItemChanged(position) // Rebind the view to update visibility and icon
            }
        }

        private fun updateVisualState(meter: Meter) {
            val context = binding.root.context
            val isReadToday = meter.lastReadingDate == todayDateString
            // UPDATED: Check if any reading exists for this meter in the nested map
            val hasNewReading = enteredObisReadings[meter.id]?.any { it.value.isNotBlank() } ?: false

            binding.checkmarkIcon.visibility = if (isReadToday) View.VISIBLE else View.GONE

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
                    binding.energyTypeIcon.imageTintList = null
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MeterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // UPDATED: Return the new nested map structure
    fun getEnteredReadings(): Map<String, Map<String, String>> = enteredObisReadings

    fun getMeterImages(): Map<String, Uri> = meterImages

    fun updateMeterImageUri(meterId: String, uri: Uri) {
        meterImages[meterId] = uri
        val index = currentList.indexOfFirst { it.meter.id == meterId }
        if (index != -1) notifyItemChanged(index)
    }

    fun removeMeterImageUri(meterId: String) {
        meterImages.remove(meterId)
        val index = currentList.indexOfFirst { it.meter.id == meterId }
        if (index != -1) notifyItemChanged(index)
    }

    // UPDATED: Renamed clear function for clarity
    fun clearEnteredObisReadings() {
        enteredObisReadings.clear()
        expandedItems.clear() // Also collapse all items when clearing
        notifyDataSetChanged()
    }

    fun clearMeterImages() {
        meterImages.clear()
        notifyDataSetChanged()
    }
}

