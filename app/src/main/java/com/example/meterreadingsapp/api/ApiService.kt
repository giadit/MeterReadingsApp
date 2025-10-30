package com.example.meterreadingsapp.api

import com.example.meterreadingsapp.data.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("/auth/v1/token?grant_type=password")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("projects")
    suspend fun getProjects(): Response<List<Project>>

    @GET("buildings")
    suspend fun getBuildings(@Query("project_id") projectId: String): Response<List<Building>>

    @GET("meters")
    suspend fun getAllMeters(): Response<List<Meter>>

    @GET("meters")
    suspend fun getMetersByBuildingId(@Query("building_id") buildingId: String): Response<List<Meter>>

    // ADDED: Endpoints for fetching new OBIS-related data
    @GET("obis_codes")
    suspend fun getObisCodes(): Response<List<ObisCode>>

    @GET("meter_obis")
    suspend fun getMeterObis(): Response<List<MeterObis>>

    // --- NEW: Get meter_obis entries filtered by meter_id ---
    @GET("meter_obis")
    suspend fun getMeterObisByMeterId(
        @Query("meter_id") meterIdFilter: String // e.g., "eq.some-uuid"
    ): Response<List<MeterObis>>
    // --- END NEW ---

    @POST("readings")
    suspend fun postReading(
        @Body reading: Reading,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    // ADDED: Endpoint to create a new meter.
    // It returns a list containing the single new meter, which is how Supabase works.
    @POST("meters")
    suspend fun createMeter(
        @Body newMeter: NewMeterRequest,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<Meter>>

    // --- NEW: Create new meter_obis entries (as a batch) ---
    @POST("meter_obis")
    suspend fun createMeterObis(
        @Body newMeterObisList: List<NewMeterObisRequest>,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>
    // --- END NEW ---

    // ADDED: Endpoint to update an existing meter using PATCH.
    @PATCH("meters")
    suspend fun updateMeter(
        @Query("id") meterIdFilter: String, // e.g., "eq.some-uuid-here"
        @Body updates: UpdateMeterRequest,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    @POST("files")
    suspend fun postFileMetadata(
        @Body fileMetadata: FileMetadata,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>
}
