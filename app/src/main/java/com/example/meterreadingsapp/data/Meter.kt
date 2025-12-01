package com.example.meterreadingsapp.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Represents a Meter item, updated to match the new database schema.
 * This class is used for both Room (local database) and Retrofit (API calls).
 */
@Entity(tableName = "meters")
data class Meter(
    @PrimaryKey
    @SerializedName("id")
    val id: String,

    @SerializedName("number")
    val number: String,

    @ColumnInfo(name = "project_id")
    @SerializedName("project_id")
    val projectId: String,

    @ColumnInfo(name = "building_id")
    @SerializedName("building_id")
    val buildingId: String?,

    @ColumnInfo(name = "generator_id")
    @SerializedName("generator_id")
    val generatorId: String?,

    @SerializedName("street")
    val street: String,

    @ColumnInfo(name = "postal_code")
    @SerializedName("postal_code")
    val postalCode: String?,

    @SerializedName("city")
    val city: String?,

    @SerializedName("location")
    val location: String?,

    @SerializedName("consumer")
    val consumer: String?,

    @SerializedName("type")
    val type: String,

    @SerializedName("marketing")
    val marketing: String?,

    @SerializedName("msb")
    val msb: String?,

    // CHANGED: 'Any' -> 'Any?' to match the MapConverter signature.
    // This ensures Room can find the correct TypeConverter.
    @ColumnInfo(name = "last_readings")
    @SerializedName("last_readings")
    val lastReadings: Map<String, Any?>?,

    @ColumnInfo(name = "last_reading_date")
    @SerializedName("last_reading_date")
    val lastReadingDate: String?,

    @SerializedName("consumption")
    val consumption: String?,

    @SerializedName("generation")
    val generation: String?,

    @SerializedName("status")
    val status: String?,

    @ColumnInfo(name = "install_date")
    @SerializedName("install_date")
    val installDate: String?,

    @ColumnInfo(name = "last_inspection")
    @SerializedName("last_inspection")
    val lastInspection: String?,

    @SerializedName("manufacturer")
    val manufacturer: String?,

    @SerializedName("model")
    val model: String?,

    @ColumnInfo(name = "serial_number")
    @SerializedName("serial_number")
    val serialNumber: String?,

    @ColumnInfo(name = "created_at")
    @SerializedName("created_at")
    val createdAt: String?,

    @ColumnInfo(name = "updated_at")
    @SerializedName("updated_at")
    val updatedAt: String?,

    @ColumnInfo(name = "energy_type")
    @SerializedName("energy_type")
    val energyType: String?,

    @ColumnInfo(name = "meter_type_id")
    @SerializedName("meter_type_id")
    val meterTypeId: String?,

    @ColumnInfo(name = "energy_type_id")
    @SerializedName("energy_type_id")
    val energyTypeId: String?,

    @ColumnInfo(name = "exchanged_with_new_meter_id")
    @SerializedName("exchanged_with_new_meter_id")
    val exchangedWithNewMeterId: String?,

    @ColumnInfo(name = "replaced_old_meter_id")
    @SerializedName("replaced_old_meter_id")
    val replacedOldMeterId: String?,

    @SerializedName("marktlokationsnummer")
    val marktlokationsnummer: String?,

    @SerializedName("messlokationsnummer")
    val messlokationsnummer: String?,

    @ColumnInfo(name = "bezugs_malo_summenzaehler")
    @SerializedName("bezugs_malo_summenzaehler")
    val bezugsMaloSummenzaehler: String?,

    @ColumnInfo(name = "calibration_valid_until")
    @SerializedName("calibration_valid_until")
    val calibrationValidUntil: Int?,

    @ColumnInfo(name = "rollover_value")
    @SerializedName("rollover_value")
    val rolloverValue: Long?,

    @SerializedName("wandlerfaktor")
    val wandlerfaktor: Int?,

    @ColumnInfo(name = "house_number")
    @SerializedName("house_number")
    val houseNumber: String?,

    @ColumnInfo(name = "house_number_addition")
    @SerializedName("house_number_addition")
    val houseNumberAddition: String?,

    @ColumnInfo(name = "elec_or_mech")
    @SerializedName("elec_or_mech")
    val elecOrMech: String?,

    @ColumnInfo(name = "elec_source")
    @SerializedName("elec_source")
    val elecSource: String?
)