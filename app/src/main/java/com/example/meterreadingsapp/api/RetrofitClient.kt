package com.example.meterreadingsapp.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.example.meterreadingsapp.BuildConfig

/**
 * Singleton object for configuring and providing the Retrofit API service.
 * This ensures only one instance of Retrofit is created throughout the application.
 */
object RetrofitClient {

    // IMPORTANT: Replace this with your actual API key
    // For a real app, you would store this more securely, e.g., in local.properties or BuildConfig.
    private val API_KEY = BuildConfig.SUPABASE_API_KEY // <--- REPLACE THIS LINE WITH YOUR KEY

    // Base URL for your API. All API requests will start with this URL.
    // Make sure this matches the base part of the URL you used in Postman.
    private const val BASE_URL = "https://rtbkdkofphqzifnozvqe.supabase.co/rest/v1/" // <--- REPLACE THIS LINE WITH YOUR BASE URL

    // Interceptor to add API key to headers
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        // Build a new request with the Authorization header added
        val newRequest = originalRequest.newBuilder()
            .header("apikey", API_KEY) // <--- Adjust "Authorization" and "Bearer " if your API uses different header key or prefix
            .build()
        chain.proceed(newRequest)
    }

    // Configure OkHttpClient for logging network requests and adding the auth interceptor.
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log the full request and response body
    }

    // Build the OkHttpClient instance.
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor) // Add the authentication interceptor first
        .addInterceptor(loggingInterceptor) // Add the logging interceptor
        // Set timeouts for connection, read, and write operations.
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Build the Retrofit instance.
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL) // Set the base URL for API requests
            .client(okHttpClient) // Attach the OkHttpClient for headers, logging and timeouts
            .addConverterFactory(GsonConverterFactory.create()) // Use Gson to convert JSON to Kotlin objects
            .build()
    }

    /**
     * Provides an instance of the ApiService.
     * This is the method that other parts of the app will call to get the API interface.
     * T is a generic type, allowing this function to create any service interface.
     */
    fun <T> getService(serviceClass: Class<T>): T {
        return retrofit.create(serviceClass)
    }
}