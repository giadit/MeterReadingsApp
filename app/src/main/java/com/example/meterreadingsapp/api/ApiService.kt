package com.example.meterreadingsapp.api

import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.FileMetadata
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Header
import retrofit2.http.PATCH // NEW: Import for PATCH method
import retrofit2.http.DELETE // NEW: Import for DELETE method
import retrofit2.http.Path // NEW: Import for @Path if using path parameters

/**
 * Retrofit interface for interacting with your Supabase API.
 * Defines the HTTP methods and endpoints for fetching and submitting data.
 */
interface ApiService {

    /**
     * Fetches a list of all meters from the API.
     * Corresponds to a GET request to the '/meters' endpoint.
     * We fetch all meters and then derive locations from them locally.
     * @return A Retrofit Response object containing a list of Meter objects.
     */
    @GET("meters") // Fetch all meters to derive locations and populate meter list
    suspend fun getAllMeters(): Response<List<Meter>>

    /**
     * Fetches a list of meters, filtered by address, postal code, and city.
     * Corresponds to a GET request to the '/meters' endpoint with query parameters.
     *
     * @param address The street address to filter meters by.
     * @param postalCode The postal code to filter meters by (optional).
     * @param city The city to filter meters by (optional).
     * @return A Retrofit Response object containing a list of Meter objects.
     */
    @GET("meters")
    suspend fun getMetersByLocation(
        @Query("address") address: String,
        @Query("postal_code") postalCode: String?,
        @Query("city") city: String?
    ): Response<List<Meter>>

    /**
     * Fetches a single meter by its ID.
     * Used for retrieving the current state of a meter before performing an update.
     *
     * @param meterIdFilter A PostgREST filter string for the meter ID (e.g., "eq.uuid").
     * @return A Retrofit Response object containing a list of Meter objects (expected to be one).
     */
    @GET("meters")
    suspend fun getMeterById(
        @Query("id") meterIdFilter: String // Use @Query("id") to filter by ID
    ): Response<List<Meter>> // PostgREST returns a list, even for a single item filter

    /**
     * Submits a new meter reading to the database.
     * Corresponds to a POST request to the '/readings' endpoint.
     *
     * @param reading The Reading object containing the new meter reading data.
     * @param prefer Header to control server response (e.g., return=minimal for no body).
     * @return A Retrofit Response object with Unit type, indicating no response body is expected on success (204 No Content).
     */
    @POST("readings")
    suspend fun postReading(
        @Body reading: Reading,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    /**
     * Fetches a list of all projects from the API.
     * Corresponds to a GET request to the '/projects' endpoint.
     * @return A Retrofit Response object containing a list of Project objects.
     */
    @GET("projects")
    suspend fun getProjects(): Response<List<Project>>

    /**
     * Submits metadata for a file to the API.
     * Corresponds to a POST request to the '/files' endpoint.
     *
     * @param fileMetadata The FileMetadata object containing the file details.
     * @param prefer Header to control server response (e.g., return=minimal for no body).
     * @return A Retrofit Response object with Unit type, indicating no response body is expected on success (204 No Content).
     */
    @POST("files")
    suspend fun postFileMetadata(
        @Body fileMetadata: FileMetadata,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    // NEW: Meter Management Endpoints

    /**
     * Adds a new meter to the API.
     * Corresponds to a POST request to the '/meters' endpoint.
     *
     * @param meter The Meter object to add.
     * @param prefer Header to control server response (e.g., return=minimal for no body).
     * @return A Retrofit Response object with Unit type, indicating no response body is expected on success (201 Created or 204 No Content).
     */
    @POST("meters")
    suspend fun addMeter(
        @Body meter: Meter,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    /**
     * Updates an existing meter on the API using a PATCH request.
     * This allows for partial updates of a meter record.
     *
     * @param meterId The ID of the meter to update. Used in the query parameter for PostgREST.
     * @param meter The Meter object containing the fields to update.
     * @param prefer Header to control server response.
     * @return A Retrofit Response object with Unit type.
     */
    @PATCH("meters") // PostgREST uses PATCH to /resource?id=eq.UUID
    suspend fun patchMeter(
        @Query("id") meterId: String, // Filter by ID for the specific meter
        @Body meter: Meter,
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>

    /**
     * Deletes a meter from the API.
     * Corresponds to a DELETE request to the '/meters' endpoint.
     *
     * @param meterId The ID of the meter to delete. Used in the query parameter for PostgREST.
     * @param prefer Header to control server response.
     * @return A Retrofit Response object with Unit type.
     */
    @DELETE("meters") // PostgREST uses DELETE to /resource?id=eq.UUID
    suspend fun deleteMeter(
        @Query("id") meterId: String, // Filter by ID for the specific meter
        @Header("Prefer") prefer: String = "return=minimal"
    ): Response<Unit>
}
