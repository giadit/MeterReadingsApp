package com.example.meterreadingsapp.data

import com.google.gson.annotations.SerializedName

/**
 * Data class for creating a new MeterObis link.
 * Does not include 'id' or 'created_at', which are set by the server.
 */
data class NewMeterObisRequest(
    @SerializedName("meter_id")
    val meterId: String,

    @SerializedName("obis_code_id")
    val obisCodeId: String
)
