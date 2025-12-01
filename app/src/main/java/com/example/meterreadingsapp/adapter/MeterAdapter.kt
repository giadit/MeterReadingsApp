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
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.meterreadingsapp.R
import com.example.meterreadingsapp.data.Meter
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
    private val onCameraClicked: (meter: Meter, obisKey: String, obisCode: String?, currentUri: Uri?) -> Unit,
    private val onViewImageClicked: (meter: Meter, obisKey: String, uri: Uri) -> Unit,
    private val onDeleteImageClicked: (meter: Meter, obisKey: String, obisCode: String?, uri: Uri) -> Unit,
    private val onEditMeterClicked: (Meter) -> Unit,
    private val onDeleteMeterClicked: (Meter) -> Unit,
    private val onExchangeMeterClicked: (Meter) -> Unit,
    private val onInfoClicked: (Meter) -> Unit,
    private val currentMode: () -> AppMode
) : ListAdapter<MeterWithObisPoints, MeterAdapter.MeterViewHolder>(DiffCallback) {

    companion object {
        const val SINGLE_READING_KEY = "single_reading"

        private val DiffCallback = object : DiffUtil.ItemCallback<MeterWithObisPoints>() {
            override fun areItemsTheSame(oldItem: MeterWithObisPoints, newItem: MeterWithObisPoints): Boolean {
                return oldItem.meter.id == newItem.meter.id
            }
            override fun areContentsTheSame(oldItem: MeterWithObisPoints, newItem: MeterWithObisPoints): Boolean {
                return oldItem == newItem
            }
        }
    }

    private val enteredReadings: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    private val meterImages: MutableMap<String, MutableMap<String, Uri>> = mutableMapOf()
    private var allObisCodes: Map<String, ObisCode> = emptyMap()

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

        private val watchers = mutableMapOf<EditText, TextWatcher>()
        private val dynamicButtons = mutableListOf<ImageButton>()

        fun bind(meterWithObis: MeterWithObisPoints) {
            val meter = meterWithObis.meter
            val context = binding.root.context
            val isExpanded = expandedItems.contains(meter.id)

            // --- Bind Header Data ---
            binding.meterNumberTextView.text = meter.number
            binding.meterEnergyTypeTextView.text = meter.energyType ?: "N/A"

            // UPDATED: Hide the general "last reading" line in the header
            // We find the parent view of the text view (which is the LinearLayout row) and hide it
            (binding.meterLastReadingTextView.parent as? View)?.visibility = View.GONE

            // Format date for use in specific rows
            val formattedDate = meter.lastReadingDate?.let {
                try {
                    apiDateFormat.parse(it)?.let { date -> uiDateFormat.format(date) }
                } catch (e: Exception) { null }
            }

            // --- Setup Click Listeners ---
            binding.infoButton.setOnClickListener {
                onInfoClicked(meter)
            }

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
                binding.readingValueInputLayout.visibility = View.GONE

                obisPoints.forEach { meterObis ->
                    val obisKey = meterObis.id
                    val obisCode = allObisCodes[meterObis.obisCodeId]
                    val obisCodeString = obisCode?.code
                    val currentImageUri = meterImageMap[obisKey]

                    // UPDATED: Fetch specific last reading using OBIS code as key
                    val specificLastReading = if (obisCodeString != null) {
                        meter.lastReadings?.get(obisCodeString)?.toString()
                    } else null

                    val (rowLayout, editText) = createReadingInputRow(
                        context = context,
                        hint = obisCode?.description ?: obisCodeString ?: "Reading",
                        obisCode = obisCodeString,
                        savedValue = meterReadings[obisKey] ?: "",
                        hasImage = currentImageUri != null,
                        lastReading = specificLastReading, // Pass specific reading
                        lastReadingDate = formattedDate // Pass date
                    )

                    // FIX: Correct indices for buttons.
                    // Index 0 is the inputWrapper (LinearLayout) containing TextView and TextInputLayout
                    val cameraButton = rowLayout.getChildAt(1) as ImageButton
                    val viewButton = rowLayout.getChildAt(2) as ImageButton
                    val deleteButton = rowLayout.getChildAt(3) as ImageButton

                    binding.obisReadingsContainer.addView(rowLayout)

                    val textWatcher = object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            meterReadings[obisKey] = s.toString()
                            updateVisualState(meter, meterWithObis)
                        }
                    }
                    editText.addTextChangedListener(textWatcher)
                    watchers[editText] = textWatcher

                    cameraButton.setOnClickListener { onCameraClicked(meter, obisKey, obisCodeString, currentImageUri) }
                    viewButton.setOnClickListener { currentImageUri?.let { onViewImageClicked(meter, obisKey, it) } }
                    deleteButton.setOnClickListener { currentImageUri?.let { onDeleteImageClicked(meter, obisKey, obisCodeString, it) } }

                    dynamicButtons.addAll(listOf(cameraButton, viewButton, deleteButton))
                }
            } else {
                binding.readingValueInputLayout.visibility = View.GONE
                val obisKey = SINGLE_READING_KEY
                val obisCodeString = "main"
                val currentImageUri = meterImageMap[obisKey]

                // Fallback: Try to get any value from lastReadings or use the old field if it existed
                val specificLastReading = meter.lastReadings?.values?.firstOrNull()?.toString()

                val (rowLayout, dynamicEditText) = createReadingInputRow(
                    context = context,
                    hint = context.getString(R.string.enter_reading_hint),
                    obisCode = null,
                    savedValue = meterReadings[obisKey] ?: "",
                    hasImage = currentImageUri != null,
                    lastReading = specificLastReading,
                    lastReadingDate = formattedDate
                )

                binding.obisReadingsContainer.addView(rowLayout)

                // FIX: Correct indices for buttons here as well.
                val cameraButton = rowLayout.getChildAt(1) as ImageButton
                val viewButton = rowLayout.getChildAt(2) as ImageButton
                val deleteButton = rowLayout.getChildAt(3) as ImageButton

                val textWatcher = object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        meterReadings[obisKey] = s.toString()
                        updateVisualState(meter, meterWithObis)
                    }
                }
                dynamicEditText.addTextChangedListener(textWatcher)
                watchers[dynamicEditText] = textWatcher

                cameraButton.setOnClickListener { onCameraClicked(meter, obisKey, obisCodeString, currentImageUri) }
                viewButton.setOnClickListener { currentImageUri?.let { onViewImageClicked(meter, obisKey, it) } }
                deleteButton.setOnClickListener { currentImageUri?.let { onDeleteImageClicked(meter, obisKey, obisCodeString, it) } }

                dynamicButtons.addAll(listOf(cameraButton, viewButton, deleteButton))
            }

            binding.changeMeterButton.setOnClickListener { onExchangeMeterClicked(meter) }
            binding.buttonsContainer.visibility = View.GONE

            val mode = currentMode()
            updateFieldsForAppMode(mode)
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
                notifyItemChanged(position)
            }
        }

        // UPDATED: Added lastReading and lastReadingDate parameters
        private fun createReadingInputRow(
            context: Context,
            hint: String,
            obisCode: String?,
            savedValue: String,
            hasImage: Boolean,
            lastReading: String?,
            lastReadingDate: String?
        ): Pair<LinearLayout, TextInputEditText> {
            val rowLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    val marginInPixels = (8 * context.resources.displayMetrics.density).roundToInt()
                    it.topMargin = marginInPixels
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL // Vertical center for buttons, but text input might grow
            }

            // NEW: Vertical Wrapper for "Last Reading Text" and "Input Field"
            val inputWrapper = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, // Width 0 (weight 1)
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f // Weight 1
                )
                orientation = LinearLayout.VERTICAL
            }

            // NEW: TextView for Last Reading
            // CHANGED: Always display the TextView. Use "N/A" if lastReading is null.
            val displayReading = lastReading ?: "N/A"
            val displayDate = if (lastReadingDate != null) " ($lastReadingDate)" else ""

            val lastReadingTextView = TextView(context).apply {
                text = "Ltz. Stand: $displayReading$displayDate"
                textSize = 12f // Small text
                try {
                    setTextColor(ContextCompat.getColor(context, R.color.dark_gray_text))
                } catch (e: Exception) {
                    setTextColor(android.graphics.Color.GRAY)
                }
                setPadding(0, 0, 0, 4) // Padding bottom
            }
            inputWrapper.addView(lastReadingTextView)

            val textInputLayout = TextInputLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, // Match wrapper width
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHintEnabled = false
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                try {
                    setBoxStrokeColorStateList(ContextCompat.getColorStateList(context, R.drawable.text_input_stroke_color_selector))
                } catch (e: Exception) {
                    Log.e("MeterAdapter", "R.drawable.text_input_stroke_color_selector not found.")
                }
            }

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

            textInputLayout.addView(editText)
            inputWrapper.addView(textInputLayout)

            // Add Wrapper to Row
            rowLayout.addView(inputWrapper)

            // Add Buttons
            val cameraButton = createDynamicImageButton(context, R.drawable.ic_camera_white_24, R.drawable.button_orange_background, R.string.camera_button_description)
            val viewButton = createDynamicImageButton(context, R.drawable.ic_image_white_24, R.drawable.button_orange_background, R.string.view_image_button_description)
            val deleteButton = createDynamicImageButton(context, R.drawable.ic_delete_white_24, R.drawable.button_red_background, R.string.delete_image_button_description)

            updateDynamicButtonStates(viewButton, deleteButton, hasImage)

            rowLayout.addView(cameraButton)
            rowLayout.addView(viewButton)
            rowLayout.addView(deleteButton)

            return Pair(rowLayout, editText)
        }

        private fun createDynamicImageButton(context: Context, iconRes: Int, bgRes: Int, contentDescRes: Int): ImageButton {
            val marginInPixels = (4 * context.resources.displayMetrics.density).roundToInt()
            val sizeInPixels = (40 * context.resources.displayMetrics.density).roundToInt()
            val paddingInPixels = (8 * context.resources.displayMetrics.density).roundToInt()

            return ImageButton(context).apply {
                layoutParams = LinearLayout.LayoutParams(sizeInPixels, sizeInPixels).also {
                    it.marginStart = marginInPixels
                    // Center vertically in the row
                    it.gravity = Gravity.CENTER_VERTICAL
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
            val inactiveColor = ContextCompat.getColor(context, android.R.color.darker_gray)
            viewButton.imageTintList = ColorStateList.valueOf(if (hasImage) activeColor else inactiveColor)
            deleteButton.imageTintList = ColorStateList.valueOf(if (hasImage) activeColor else inactiveColor)
        }

        private fun updateVisualState(meter: Meter, meterWithObis: MeterWithObisPoints) {
            val context = binding.root.context
            val isReadToday = meter.lastReadingDate == todayDateString

            val readingsMap = enteredReadings[meter.id]
            val hasNewReading: Boolean
            val expectedReadingCount: Int

            if (meterWithObis.obisPoints.isNotEmpty()) {
                expectedReadingCount = meterWithObis.obisPoints.size
            } else {
                expectedReadingCount = 1
            }

            if (readingsMap == null || readingsMap.isEmpty()) {
                hasNewReading = false
            } else {
                val filledReadings = if (meterWithObis.obisPoints.isNotEmpty()) {
                    meterWithObis.obisPoints.count { obisPoint ->
                        readingsMap[obisPoint.id]?.isNotBlank() == true
                    }
                } else {
                    if (readingsMap[SINGLE_READING_KEY]?.isNotBlank() == true) 1 else 0
                }
                hasNewReading = (filledReadings == expectedReadingCount) && (expectedReadingCount > 0)
            }

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
            watchers.keys.forEach { it.isEnabled = isReadingMode }
            dynamicButtons.forEach { it.isEnabled = isReadingMode }
            binding.newReadingValueEditText.isEnabled = isReadingMode
            binding.readingValueInputLayout.isEnabled = isReadingMode
            binding.changeMeterButton.isEnabled = isReadingMode
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

    fun getEnteredReadings(): Map<String, Map<String, String>> = enteredReadings
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
        expandedItems.clear()
        notifyDataSetChanged()
    }

    fun clearMeterImages() {
        meterImages.clear()
        notifyDataSetChanged()
    }
}