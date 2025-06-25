package com.example.meterreadingsapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data class representing a unique location, derived from meter data.
 * This class will be used as a Room entity to display a list of "buildings" based on their addresses,
 * including house number and addition for finer granularity.
 *
 * @param id A unique ID for this location, derived from a combination of street address,
 * postal code, city, house number, and house number addition.
 * @param name A display name for the location (e.g., combined street, house number, addition, and consumer).
 * @param project_id Project ID associated with this location (nullable).
 * @param address The street address.
 * @param postal_code The postal code (nullable).
 * @param city The city (nullable).
 * @param house_number The house number (NEW, nullable).
 * @param house_number_addition The house number addition (NEW, nullable).
 * @param created_at Timestamp when the record was created (nullable).
 * @param updated_at Timestamp when the record was last updated (nullable).
 */
@Entity(tableName = "locations") // Mark as Room entity
data class Location(
    @PrimaryKey // Room annotation for primary key
    val id: String, // FIX: A composite ID like "address|postal_code|city|house_number|house_number_addition"
    val name: String?, // FIX: Made nullable here, will ensure non-null string in repository
    val project_id: String?, // FIX: Made nullable as per Meter.kt, no SerializedName needed if direct mapping
    val address: String?, // FIX: Made nullable to match Meter.address nullability (API 'street')
    val postal_code: String?,
    val city: String?,
    val house_number: String? = null, // FIX: Added house_number
    val house_number_addition: String? = null, // FIX: Added house_number_addition
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("updated_at") val updated_at: String? = null
)
