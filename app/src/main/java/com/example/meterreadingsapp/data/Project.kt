package com.example.meterreadingsapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data class representing a Project, updated to match the new database schema.
 * This class is used for both Room (local database) and Retrofit (API calls).
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("responsible")
    val responsible: String,

    @SerializedName("responsible_email")
    val responsibleEmail: String?,

    @SerializedName("status")
    val status: String,

    @SerializedName("address")
    val address: String?,

    @SerializedName("buildings_count")
    val buildingsCount: Int?,

    @SerializedName("meters_count")
    val metersCount: Int?,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("updated_at")
    val updatedAt: String?,

    @SerializedName("project_number")
    val projectNumber: String?,

    @SerializedName("service_company_name")
    val serviceCompanyName: String?,

    @SerializedName("service_company_email")
    val serviceCompanyEmail: String?,

    @SerializedName("service_company_phone")
    val serviceCompanyPhone: String?,

    @SerializedName("project_type")
    val projectType: String?,

    @SerializedName("launch_date")
    val launchDate: String?,

    @SerializedName("energy_generators")
    val energyGenerators: String?,

    @SerializedName("user_id")
    val userId: String?
)

