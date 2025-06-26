package com.example.meterreadingsapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName // Required for @SerializedName

/**
 * Represents a Meter item fetched from the API and stored in the local database.
 * Contains detailed information about each meter, including its associated address details.
 *
 * @param id The unique identifier of the meter. Primary key for Room.
 * @param number The identification number of the meter.
 * @param project_id The ID of the project this meter belongs to (can be null).
 * @param building_id The ID of the building this meter is explicitly linked to (often null in your data,
 * we'll rely on address for grouping).
 * @param generator_id The ID of the generator this meter is linked to (can be null).
 * @param address The street address where the meter is located.
 * @param postal_code The postal code of the meter's location.
 * @param city The city of the meter's location.
 * @param house_number The house number of the meter's location (NEW).
 * @param house_number_addition The house number addition of the meter's location (NEW).
 * @param location Specific location detail within the address (e.g., "ELT-Raum").
 * @param consumer The consumer associated with this meter.
 * @param type The type of meter (e.g., "Apartment", "Main Meter", "Generator (PV)").
 * @param marketing Marketing designation (e.g., "Company Customer").
 * @param msb Measurement service provider.
 * @param last_reading The last recorded reading value.
 * @param last_reading_date The date of the last recorded reading.
 * @param consumption Recorded consumption (can be null).
 * @param generation Recorded generation (can be null).
 * @param status The current status of the meter (e.g., "Valid", "Exchanged").
 * @param install_date The installation date of the meter (can be null).
 * @param last_inspection The date of the last inspection (can be null).
 * @param manufacturer The manufacturer of the meter.
 * @param model The model of the meter.
 * @param serial_number The serial number of the meter (can be null).
 * @param created_at Timestamp when the record was created.
 * @param updated_at Timestamp when the record was last updated.
 * @param obis_1_8_0 Boolean indicating OBIS code 1.8.0.
 * @param obis_2_8_0 Boolean indicating OBIS code 2.8.0.
 * @param energy_type The type of energy measured (e.g., "Electricity", "Heat", "Gas").
 * @param last_reading_2 Another optional last reading field (can be null).
 * @param meter_type_id ID for meter type (can be null).
 * @param energy_type_id ID for energy type (can be null).
 */
@Entity(tableName = "meters")
data class Meter(
    @PrimaryKey(autoGenerate = false)
    val id: String, // UUID as String
    val number: String,
    val project_id: String? = null, // Can be null
    val building_id: String? = null,
    val generator_id: String? = null,
    @SerializedName("street") val address: String, // Maps to 'street' from API, essential for grouping
    val postal_code: String?,
    val city: String?,
    val house_number: String? = null, // FIX: New field for house number
    val house_number_addition: String? = null, // FIX: New field for house number addition
    val location: String? = null, // Can be null or empty
    val consumer: String? = null, // Can be null or empty
    val type: String, // Assuming type is non-null based on common API designs
    val marketing: String? = null, // Can be null or empty
    val msb: String? = null, // Can be null or empty
    val last_reading: String? = null, // Value can be null or empty string
    val last_reading_date: String? = null, // Date string, can be null
    val consumption: String? = null, // Can be null
    val generation: String? = null, // Can be null
    val status: String, // Assuming status is non-null
    val install_date: String? = null, // Can be null
    val last_inspection: String? = null, // Can be null
    val manufacturer: String? = null, // Can be empty string
    val model: String? = null, // Can be empty string
    val serial_number: String? = null, // Can be null
    val created_at: String? = null, // Nullable
    val updated_at: String? = null, // Nullable
    val obis_1_8_0: Boolean, // Assuming non-null
    val obis_2_8_0: Boolean, // Assuming non-null
    val energy_type: String, // Assuming non-null
    val last_reading_2: String? = null, // Can be null
    val meter_type_id: String? = null, // Can be null
    val energy_type_id: String? = null // Can be null
)
