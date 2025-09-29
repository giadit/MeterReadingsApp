package com.example.meterreadingsapp.api

import com.example.meterreadingsapp.data.AuthResponse
import com.example.meterreadingsapp.data.FileMetadata
import com.example.meterreadingsapp.data.LoginRequest
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.Reading
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for interacting with your Supabase API.
 * Defines the HTTP methods and endpoints for fetching and submitting data.
 */
interface ApiService {

    /**
     * NEW: Performs a login request to the Supabase authentication endpoint.
     * @param loginRequest The request body containing the user's email and password.
     * @param grantType The grant type, required by the Supabase token endpoint.
     * @return A Retrofit Response object containing an AuthResponse with the access token.
     */
    @POST("/auth/v1/token")
    suspend fun login(
        @Body loginRequest: LoginRequest,
        @Query("grant_type") grantType: String = "password"
    ): Response<AuthResponse>

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
     * NEW: Submits metadata for a file to the API.
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
}

