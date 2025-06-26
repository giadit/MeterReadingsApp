package com.example.meterreadingsapp.api

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.PUT // Import for PUT method

/**
 * Retrofit interface for interacting with Supabase Storage.
 * Defines the endpoint for uploading objects, authenticated by the Supabase API key in headers.
 */
interface StorageApiService {

    /**
     * Uploads a file to Supabase Storage using the PUT method.
     * The file will be placed in the specified bucket at the given path.
     * If a file with the same path already exists, it will be overwritten.
     * The Supabase API key will be added as a header by the RetrofitClient's interceptor.
     * Supabase Storage recommends 'Cache-Control'. The file's content type is handled
     * by the RequestBody of the MultipartBody.Part.
     *
     * @param bucketName The name of the Supabase Storage bucket (e.g., "meter-documents").
     * @param path The path within the bucket where the file should be stored (e.g., "meter/meter_id/image.jpg").
     * @param file The MultipartBody.Part containing the file data (e.g., image).
     * @param cacheControl The Cache-Control header (e.g., "max-age=3600").
     * @return A Retrofit Response object with Unit type (200 OK, 201 Created, or 204 No Content for success).
     */
    @Multipart
    @PUT("object/{bucketName}/{path}") // Changed method from POST to PUT for idempotent uploads/overwrites
    suspend fun uploadFile(
        @Path("bucketName") bucketName: String,
        @Path("path") path: String,
        @Part file: MultipartBody.Part,
        // FIX: Removed @Header("Content-Type") as it's typically handled by Retrofit for Multipart requests
        @Header("Cache-Control") cacheControl: String = "max-age=3600" // Recommended by Supabase for new uploads
    ): Response<Unit>

}
