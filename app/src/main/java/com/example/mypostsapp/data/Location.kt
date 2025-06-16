package com.example.mypostsapp.data

import androidx.room.Entity // Import for @Entity annotation
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data class representing a unique location, derived from meter data.
 * This class will be used as a Room entity to display a list of "buildings" based on their addresses.
 *
 * @param id A unique ID for this location, derived from a combination of address, postal code, and city.
 * @param name A display name for the location (e.g., consumer or address).
 * @param project_id Project ID associated with this location.
 * @param address The street address.
 * @param postal_code The postal code (now nullable).
 * @param city The city (now nullable).
 * @param created_at Timestamp when the record was created (nullable).
 * @param updated_at Timestamp when the record was last updated (nullable).
 */
@Entity(tableName = "locations") // Mark as Room entity
data class Location(
    @PrimaryKey // Room annotation for primary key
    val id: String, // A composite ID like "address|postal_code|city"
    val name: String, // A display name for the location
    @SerializedName("project_id") val project_id: String,
    @SerializedName("address") val address: String,
    val postal_code: String?, // FIX: Made nullable
    val city: String?, // FIX: Made nullable
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("updated_at") val updated_at: String? = null
) {
    // REMOVED: generateId companion object function from here
}
