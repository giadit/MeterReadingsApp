package com.example.meterreadingsapp.data
import com.google.gson.annotations.SerializedName

data class NewMeterRequest(
    val number: String,
    @SerializedName("project_id") val projectId: String,
    @SerializedName("building_id") val buildingId: String?,
    @SerializedName("energy_type") val energyType: String?,
    @SerializedName("type") val type: String,
    @SerializedName("status") val status: String?, // <-- ADDED THIS LINE
    @SerializedName("replaced_old_meter_id") val replacedOldMeterId: String?,

    // ADDED: Address fields are required by the database
    val street: String,
    @SerializedName("postal_code") val postalCode: String?,
    val city: String?,
    @SerializedName("house_number") val houseNumber: String?,
    @SerializedName("house_number_addition") val houseNumberAddition: String?
)
