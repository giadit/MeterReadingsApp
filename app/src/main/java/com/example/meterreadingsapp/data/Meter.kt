package com.example.meterreadingsapp.data

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

    @SerializedName("project_id")
    val projectId: String,

    @SerializedName("building_id")
    val buildingId: String?,

    @SerializedName("generator_id")
    val generatorId: String?,

    @SerializedName("street")
    val street: String,

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

    @SerializedName("last_reading")
    val lastReading: String?,

    @SerializedName("last_reading_date")
    val lastReadingDate: String?,

    @SerializedName("consumption")
    val consumption: String?,

    @SerializedName("generation")
    val generation: String?,

    @SerializedName("status")
    val status: String?,

    @SerializedName("install_date")
    val installDate: String?,

    @SerializedName("last_inspection")
    val lastInspection: String?,

    @SerializedName("manufacturer")
    val manufacturer: String?,

    @SerializedName("model")
    val model: String?,

    @SerializedName("serial_number")
    val serialNumber: String?,

    @SerializedName("created_at")
    val createdAt: String?,

    @SerializedName("updated_at")
    val updatedAt: String?,

    @SerializedName("obis_1_8_0")
    val obis180: Boolean?,

    @SerializedName("obis_2_8_0")
    val obis280: Boolean?,

    @SerializedName("energy_type")
    val energyType: String?,

    @SerializedName("last_reading_2")
    val lastReading2: String?,

    @SerializedName("meter_type_id")
    val meterTypeId: String?,

    @SerializedName("energy_type_id")
    val energyTypeId: String?,

    @SerializedName("exchanged_with_new_meter_id")
    val exchangedWithNewMeterId: String?,

    @SerializedName("replaced_old_meter_id")
    val replacedOldMeterId: String?,

    @SerializedName("marktlokationsnummer")
    val marktlokationsnummer: String?,



    @SerializedName("messlokationsnummer")
    val messlokationsnummer: String?,

    @SerializedName("bezugs_malo_summenzaehler")
    val bezugsMaloSummenzaehler: String?,

    @SerializedName("calibration_valid_until")
    val calibrationValidUntil: Int?,

    @SerializedName("rollover_value")
    val rolloverValue: Long?,

    @Serialized-Name("wandlerfaktor")
val wandlerfaktor: Int?,

@SerializedName("house_number")
val houseNumber: String?,

@SerializedName("house_number_addition")
val houseNumberAddition: String?,

@SerializedName("elec_or_mech")
val elecOrMech: String?,

@SerializedName("elec_source")
val elecSource: String?
)

