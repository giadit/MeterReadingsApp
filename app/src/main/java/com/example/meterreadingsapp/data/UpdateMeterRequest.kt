package com.example.meterreadingsapp.data
import com.google.gson.annotations.SerializedName

data class UpdateMeterRequest(
    val status: String? = null,
    @SerializedName("exchanged_with_new_meter_id") val exchangedWithNewMeterId: String? = null
)

