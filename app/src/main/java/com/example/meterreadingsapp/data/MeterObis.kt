package com.example.meterreadingsapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data class representing the link between a Meter and a specific ObisCode.
 * Corresponds to the new 'meter_obis' table.
 */
@Entity(tableName = "meter_obis")
data class MeterObis(
    @PrimaryKey
    @SerializedName("id")
    val id: String, // UUID of the meter_obis entry

    @ColumnInfo(name = "meter_id")
    @SerializedName("meter_id")
    val meterId: String, // Foreign key to the meters table

    @ColumnInfo(name = "obis_code_id")
    @SerializedName("obis_code_id")
    val obisCodeId: String, // Foreign key to the obis_codes table

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    val createdAt: String?
)
