package com.example.meterreadingsapp.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * POJO (Plain Old Kotlin Object) used by Room to hold a Meter entity combined
 * with all its associated MeterObis points (the multiple reading points).
 * This structure is necessary for displaying dynamic input fields in the UI.
 */
data class MeterWithObisPoints(
    // The main Meter data, embedded directly into this object
    @Embedded
    val meter: Meter,

    // The list of MeterObis points linked to this meter
    @Relation(
        parentColumn = "id",         // The ID of the parent (Meter.id)
        entityColumn = "meter_id"    // The column in the child table (MeterObis.meter_id)
    )
    val obisPoints: List<MeterObis>
)
