package com.example.meterreadingsapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data class representing a Building, fetched from the API and stored locally.
 * This corresponds to the `public.buildings` table in the database.
 *
 * @param id The unique identifier of the building (UUID).
 * @param name The name of the building.
 * @param project_id The foreign key linking this building to a project.
 * @param street The street address of the building.
 * @param postal_code The postal code of the building's location.
 * @param city The city of the building's location.
 * @param apartments The number of apartments in the building.
 * @param created_at Timestamp when the record was created.
 * @param updated_at Timestamp when the record was last updated.
 * @param house_number The house number of the building.
 * @param house_number_addition Addition to the house number (e.g., 'A', 'B').
 * @param number_of_meters The number of meters in this building (stored as text).
 */
@Entity(tableName = "buildings")
data class Building(
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("project_id")
    val project_id: String,

    @SerializedName("street")
    val street: String,

    @SerializedName("postal_code")
    val postal_code: String?,

    @SerializedName("city")
    val city: String?,

    @SerializedName("apartments")
    val apartments: Int?,

    @SerializedName("created_at")
    val created_at: String?,

    @SerializedName("updated_at")
    val updated_at: String?,

    @SerializedName("house_number")
    val house_number: String?,

    @SerializedName("house_number_addition")
    val house_number_addition: String?,

    @SerializedName("number_of_meters")
    val number_of_meters: String?
)
