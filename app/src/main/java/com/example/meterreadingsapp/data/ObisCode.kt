package com.example.meterreadingsapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Data class representing an OBIS Code definition.
 * Corresponds to the 'obis_codes' table.
 */
@Entity(tableName = "obis_codes")
data class ObisCode(
    @PrimaryKey
    @SerializedName("id")
    val id: String, // UUID of the OBIS code definition

    @SerializedName("code")
    val code: String, // The actual OBIS code string (e.g., "1-1:1.8.0*255")

    @SerializedName("description")
    val description: String?, // Human-readable description

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    @SerializedName("updated_at")
    val updatedAt: String?
)
