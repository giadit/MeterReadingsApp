package com.example.meterreadingsapp.api

import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Reading
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
// Removed: import retrofit2.http.PATCH
import retrofit2.http.Header // Import for Header

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


    // Removed: PATCH method for updating meters
    /*
    @PATCH("meters")
    suspend fun patchMeter(
        @Query("id") meterIdFilter: String,
        @Body updates: Map<String, String?>,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<Meter>>
    */


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
        @Header("Prefer") prefer: String = "return=minimal" // FIX: Request minimal response (no body)
        // Removed: @Query("select") select: String = "*"
    ): Response<Unit> // FIX: Changed return type to Response<Unit> for minimal response
}
