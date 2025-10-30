package com.example.meterreadingsapp.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MeterAdapter(
    // UPDATED: Pass obisKey (UUID) AND obisCode (String)
    private val onCameraClicked: (meter: Meter, obisKey: String, obisCode: String?, currentUri: Uri?) -> Unit,
    private val onViewImageClicked: (meter: Meter, obisKey: String, uri: Uri) -> Unit,
    private val onDeleteImageClicked: (meter: Meter, obisKey: String, obisCode: String?, uri: Uri) -> Unit,
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

    // Map<MeterID, Map<ObisKey, ReadingValue>>
    private val enteredReadings: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    // Map<MeterID, Map<ObisKey, ImageUri>>
    private val meterImages: MutableMap<String, MutableMap<String, Uri>> = mutableMapOf()
    private var allObisCodes: Map<String, ObisCode> = emptyMap() // Map<ObisCodeID, ObisCode>

    private val expandedItems: MutableSet<String> = mutableSetOf()

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val uiDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private val todayDateString = apiDateFormat.format(Date())

    fun setObisCodes(codes: List<ObisCode>) {
        allObisCodes = codes.associateBy { it.id }
        notifyDataSetChanged()
    }

    inner class MeterViewHolder(private val binding: ItemMeterBinding) :
        RecyclerView.ViewHolder(binding.root) {

        // Store watchers to remove them later
        private val watchers = mutableMapOf<EditText, TextWatcher>()

        // Store dynamically created buttons to manage state
        private val dynamicButtons = mutableListOf<ImageButton>()

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

            // --- Setup Expand/Collapse ---
            binding.collapsibleContent.isVisible = isExpanded
            binding.expandCollapseButton.setImageResource(
                if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
            binding.headerContent.setOnClickListener { toggleExpansion(meter.id) }
            binding.expandCollapseButton.setOnClickListener { toggleExpansion(meter.id) }

            // --- Clear previous dynamic views and watchers ---
            clearDynamicViewsAndWatchers()
            val meterReadings = enteredReadings.getOrPut(meter.id) { mutableMapOf() }
            val meterImageMap = meterImages.getOrPut(meter.id) { mutableMapOf() }

            val obisPoints = meterWithObis.obisPoints
            if (obisPoints.isNotEmpty()) {
                // Hide the fallback single reading input layout
                binding.readingValueInputLayout.visibility = View.GONE

                obisPoints.forEach { meterObis ->
                    val obisKey = meterObis.id // This is the UUID
                    val obisCode = allObisCodes[meterObis.obisCodeId]
                    val obisCodeString = obisCode?.code // This is "1.8.0" etc.
                    val currentImageUri = meterImageMap[obisKey]

                    val (rowLayout, editText) = createReadingInputRow(
                        context = context,
                        hint = obisCode?.description ?: obisCodeString ?: "Reading",
                        obisCode = obisCodeString,
                        savedValue = meterReadings[obisKey] ?: "",
                        hasImage = currentImageUri != null
                    )

                    val cameraButton = rowLayout.getChildAt(1) as ImageButton
                    val viewButton = rowLayout.getChildAt(2) as ImageButton
                    val deleteButton = rowLayout.getChildAt(3) as ImageButton

                    // Add to container
                    binding.obisReadingsContainer.addView(rowLayout)

                    // Add TextWatcher
                    val textWatcher = object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            meterReadings[obisKey] = s.toString()
                            updateVisualState(meter, meterWithObis) // Pass meterWithObis
                        }
                    }
                    editText.addTextChangedListener(textWatcher)
                    watchers[editText] = textWatcher // Store watcher for removal

                    // Set click listeners
                    cameraButton.setOnClickListener { onCameraClicked(meter, obisKey, obisCodeString, currentImageUri) }
                    viewButton.setOnClickListener { currentImageUri?.let { onViewImageClicked(meter, obisKey, it) } }
                    deleteButton.setOnClickListener { currentImageUri?.let { onDeleteImageClicked(meter, obisKey, obisCodeString, it) } }

                    dynamicButtons.addAll(listOf(cameraButton, viewButton, deleteButton))
                }
            } else {
                // Show the fallback single reading input layout
                binding.readingValueInputLayout.visibility = View.VISIBLE
                val obisKey = SINGLE_READING_KEY
                val obisCodeString = "main" // Special code for single/main reading
                val currentImageUri = meterImageMap[obisKey]

                // Create the dynamic row for the fallback
                val (rowLayout, dynamicEditText) = createReadingInputRow(
                    context = context,
                    hint = context.getString(R.string.enter_reading_hint),
                    obisCode = null, // No code for fallback
                    savedValue = meterReadings[obisKey] ?: "",
                    hasImage = currentImageUri != null
                )

                // Hide the XML-based layout
                binding.readingValueInputLayout.visibility = View.GONE
                // Add the new dynamic layout
                binding.obisReadingsContainer.addView(rowLayout)


                val cameraButton = rowLayout.getChildAt(1) as ImageButton
                val viewButton = rowLayout.getChildAt(2) as ImageButton
                val deleteButton = rowLayout.getChildAt(3) as ImageButton

                // Add TextWatcher
                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        meterReadings[obisKey] = s.toString()
                        updateVisualState(meter, meterWithObis) // Pass meterWithObis
                    }
                }
                dynamicEditText.addTextChangedListener(textWatcher)
                watchers[dynamicEditText] = textWatcher // Store watcher for removal

                // Set click listeners
                cameraButton.setOnClickListener { onCameraClicked(meter, obisKey, obisCodeString, currentImageUri) }
                viewButton.setOnClickListener { currentImageUri?.let { onViewImageClicked(meter, obisKey, it) } }
                deleteButton.setOnClickListener { currentImageUri?.let { onDeleteImageClicked(meter, obisKey, obisCodeString, it) } }

                dynamicButtons.addAll(listOf(cameraButton, viewButton, deleteButton))
            }


            // --- Button Click Listeners (Original XML buttons) ---
            binding.changeMeterButton.setOnClickListener { onExchangeMeterClicked(meter) }
            // Hide the XML buttons since we create them dynamically
            binding.buttonsContainer.visibility = View.GONE

            // --- Enable/Disable based on App Mode ---
            val mode = currentMode()
            updateFieldsForAppMode(mode)

            // --- Update Visual State ---
            updateVisualState(meter, meterWithObis)
        }

        private fun clearDynamicViewsAndWatchers() {
            binding.obisReadingsContainer.removeAllViews()
            watchers.forEach { (editText, watcher) -> editText.removeTextChangedListener(watcher) }
            watchers.clear()
            dynamicButtons.clear()
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

        private fun createReadingInputRow(
            context: Context,
            hint: String,
            obisCode: String?,
            savedValue: String,
            hasImage: Boolean
        ): Pair<LinearLayout, TextInputEditText> {
            // 1. Create Row LinearLayout
            val rowLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    val marginInPixels = (8 * context.resources.displayMetrics.density).roundToInt()
                    it.topMargin = marginInPixels
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // 2. Create TextInputLayout
            val textInputLayout = TextInputLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, // Width 0
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f // Weight 1
                )
                isHintEnabled = false
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                try {
                    setBoxStrokeColorStateList(ContextCompat.getColorStateList(context, R.drawable.text_input_stroke_color_selector))
                } catch (e: Exception) {
                    Log.e("MeterAdapter", "R.drawable.text_input_stroke_color_selector not found.")
                }
            }

            // 3. Create TextInputEditText
            val editText = TextInputEditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val fullHint = if (obisCode != null) "$hint [$obisCode]" else hint
                setHint(fullHint)
                setText(savedValue)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                maxLines = 1
            }

            // Add EditText to Layout
            textInputLayout.addView(editText)

            // 4. Create Buttons
            val cameraButton = createDynamicImageButton(context, R.drawable.ic_camera_white_24, R.drawable.button_orange_background, R.string.camera_button_description)
            val viewButton = createDynamicImageButton(context, R.drawable.ic_image_white_24, R.drawable.button_orange_background, R.string.view_image_button_description)
            val deleteButton = createDynamicImageButton(context, R.drawable.ic_delete_white_24, R.drawable.button_red_background, R.string.delete_image_button_description)

            // 5. Set button states
            updateDynamicButtonStates(viewButton, deleteButton, hasImage)

            // 6. Add all views to the row
            rowLayout.addView(textInputLayout)
            rowLayout.addView(cameraButton)
            rowLayout.addView(viewButton)
            rowLayout.addView(deleteButton)

            return Pair(rowLayout, editText)
        }

        private fun createDynamicImageButton(context: Context, iconRes: Int, bgRes: Int, contentDescRes: Int): ImageButton {
            val marginInPixels = (4 * context.resources.displayMetrics.density).roundToInt()
            val sizeInPixels = (40 * context.resources.displayMetrics.density).roundToInt() // Smaller 40dp buttons
            val paddingInPixels = (8 * context.resources.displayMetrics.density).roundToInt() // 8dp padding

            return ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(sizeInPixels, sizeInPixels).also {
                    it.marginStart = marginInPixels
                }
                setImageResource(iconRes)
                setBackgroundResource(bgRes)
                contentDescription = context.getString(contentDescRes)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, android.R.color.white))
                setPadding(paddingInPixels, paddingInPixels, paddingInPixels, paddingInPixels)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }

        private fun updateDynamicButtonStates(viewButton: ImageButton, deleteButton: ImageButton, hasImage: Boolean) {
            val context = viewButton.context
            viewButton.isEnabled = hasImage
            deleteButton.isEnabled = hasImage
            val activeColor = ContextCompat.getColor(context, android.R.color.white)
            val inactiveColor = ContextCompat.getColor(context, android.R.color.darker_gray) // Use a standard color
            viewButton.imageTintList = ColorStateList.valueOf(if (hasImage) activeColor else inactiveColor)
            deleteButton.imageTintList = ColorStateList.valueOf(if (hasImage) activeColor else inactiveColor)
        }

        // UPDATED: Function signature now takes meterWithObis
        private fun updateVisualState(meter: Meter, meterWithObis: MeterWithObisPoints) {
            val context = binding.root.context
            val isReadToday = meter.lastReadingDate == todayDateString

            // --- START OF NEW LOGIC ---
            val readingsMap = enteredReadings[meter.id]
            val hasNewReading: Boolean
            val expectedReadingCount: Int

            // Determine how many readings we expect
            if (meterWithObis.obisPoints.isNotEmpty()) {
                expectedReadingCount = meterWithObis.obisPoints.size
            } else {
                expectedReadingCount = 1 // For the single/fallback field
            }

            if (readingsMap == null || readingsMap.isEmpty()) {
                hasNewReading = false
            } else {
                // Count how many of the *expected* fields are filled
                val filledReadings = if (meterWithObis.obisPoints.isNotEmpty()) {
                    // Count filled fields based on the obisPoints list
                    meterWithObis.obisPoints.count { obisPoint ->
                        readingsMap[obisPoint.id]?.isNotBlank() == true
                    }
                } else {
                    // Check only the single reading key
                    if (readingsMap[SINGLE_READING_KEY]?.isNotBlank() == true) 1 else 0
                }

                // Set to true ONLY if all expected readings are filled
                hasNewReading = (filledReadings == expectedReadingCount) && (expectedReadingCount > 0)
            }
            // --- END OF NEW LOGIC ---

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

        private fun updateFieldsForAppMode(mode: AppMode) {
            val isReadingMode = (mode == AppMode.READINGS)
            // Enable/disable dynamically created EditTexts
            watchers.keys.forEach { it.isEnabled = isReadingMode }
            // Enable/disable dynamically created buttons
            dynamicButtons.forEach { it.isEnabled = isReadingMode }
            // Enable/disable fallback XML EditText (just in case)
            binding.newReadingValueEditText.isEnabled = isReadingMode
            binding.readingValueInputLayout.isEnabled = isReadingMode

            // Also enable/disable the original buttons
            binding.changeMeterButton.isEnabled = isReadingMode
            // These are now hidden, but disabling is good practice
            binding.cameraButton.isEnabled = isReadingMode
            binding.viewImageButton.isEnabled = isReadingMode
            binding.deleteImageButton.isEnabled = isReadingMode
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeterViewHolder {
        val binding = ItemMeterBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MeterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MeterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // Return the nested map structure
    fun getEnteredReadings(): Map<String, Map<String, String>> = enteredReadings

    // Return the new nested map structure
    fun getMeterImages(): Map<String, Map<String, Uri>> = meterImages

    fun updateMeterImageUri(meterId: String, obisKey: String, uri: Uri) {
        meterImages.getOrPut(meterId) { mutableMapOf() }[obisKey] = uri
        val index = currentList.indexOfFirst { it.meter.id == meterId }
        if (index != -1) notifyItemChanged(index)
    }

    fun removeMeterImageUri(meterId: String, obisKey: String) {
        meterImages[meterId]?.remove(obisKey)
        val index = currentList.indexOfFirst { it.meter.id == meterId }
        if (index != -1) notifyItemChanged(index)
    }

    fun clearEnteredObisReadings() {
        enteredReadings.clear()
        expandedItems.clear() // Also collapse all items when clearing
        notifyDataSetChanged()
    }

    fun clearMeterImages() {
        meterImages.clear()
        notifyDataSetChanged()
    }
}

