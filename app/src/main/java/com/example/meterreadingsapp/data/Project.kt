package com.example.meterreadingsapp.data

import com.google.gson.annotations.SerializedName
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Data class representing a Project fetched from the API.
 * This class holds detailed information about a project, mapped from the provided API log.
 *
 * @param id The unique identifier of the project (UUID string).
 * @param name The name of the project.
 * @param description A description of the project (can be an empty string, so nullable).
 * @param responsible The person responsible for the project (nullable).
 * @param responsibleEmail The email of the responsible person (nullable, maps from responsible_email).
 * @param status The current status of the project (e.g., "active", "on_hold", "Inactive").
 * @param address The street address of the project (nullable, as per example format).
 * @param buildingsCount The number of buildings associated with the project (Integer, maps from buildings_count).
 * @param metersCount The number of meters associated with the project (Integer, maps from meters_count).
 * @param createdAt Timestamp when the record was created (ISO 8601 string, nullable, maps from created_at).
 * @param updatedAt Timestamp when the record was last updated (ISO 8601 string, nullable, maps from updated_at).
 * @param projectNumber A unique project number (string, nullable, maps from project_number).
 * @param serviceCompanyName The name of the service company (nullable, maps from service_company_name).
 * @param serviceCompanyEmail The email of the service company (nullable, maps from service_company_email).
 * @param serviceCompanyPhone The phone number of the service company (nullable, maps from service_company_phone).
 * @param projectType The type of project (e.g., "Eigenverbrauch", "BHKW_VC", maps from project_type).
 * @param launchDate The launch date of the project (date string, nullable, maps from launch_date).
 * @param energyGenerators Information about energy generators (string, nullable, maps from energy_generators).
 * @param userId User ID associated with the project (UUID string, nullable, maps from user_id).
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = false) // 'id' is a UUID from API, so no Room auto-generation
    val id: String,
    val name: String?,
    val description: String?,
    val responsible: String?,
    @SerializedName("responsible_email") val responsibleEmail: String?,
    val status: String?,
    val address: String?,
    @SerializedName("buildings_count") val buildingsCount: Int?,
    @SerializedName("meters_count") val metersCount: Int?,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("updated_at") val updatedAt: String? = null,
    @SerializedName("project_number") val projectNumber: String?,
    @SerializedName("service_company_name") val serviceCompanyName: String?,
    @SerializedName("service_company_email") val serviceCompanyEmail: String?,
    @SerializedName("service_company_phone") val serviceCompanyPhone: String?,
    @SerializedName("project_type") val projectType: String?,
    @SerializedName("launch_date") val launchDate: String?,
    @SerializedName("energy_generators") val energyGenerators: String?,
    @SerializedName("user_id") val userId: String?
) {
    // Override toString() to display the project name in AutoCompleteTextView
    override fun toString(): String {
        return name ?: "Unknown Project"
    }
}
