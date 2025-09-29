package com.example.meterreadingsapp.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.example.meterreadingsapp.BuildConfig
import android.util.Log

/**
 * Singleton object for configuring and providing the Retrofit API services.
 * This ensures distinct instances of Retrofit for the main API and the Storage API,
 * both using the Supabase API key for authentication, with Storage now also including
 * the Authorization header.
 */
object RetrofitClient {

    private val API_KEY = BuildConfig.SUPABASE_API_KEY

    // Base URL for your main Supabase API.
    private const val BASE_URL_API = "https://database.berliner-e-agentur.de"

    // Base URL for Supabase Storage API
    private const val BASE_URL_STORAGE = "https://rtbkdkofphqzifnozvqe.supabase.co/storage/v1/"


    // Interceptor to add Supabase API key to headers for ALL Supabase API calls (main and storage)
    // This interceptor will now add BOTH 'apikey' and 'Authorization: Bearer <API_KEY>'
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("apikey", API_KEY)
            // FIX: Add Authorization header with Bearer token, using the same API_KEY
            .header("Authorization", "Bearer $API_KEY")
            .build()
        chain.proceed(newRequest)
    }

    // Configure OkHttpClient for logging network requests
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log the full request and response body
    }

    // OkHttpClient for the main API (includes Supabase API key interceptor)
    private val okHttpClientApi = OkHttpClient.Builder()
        .addInterceptor(authInterceptor) // Apply Supabase API key and Authorization header
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // OkHttpClient for Supabase Storage uploads (now also uses the main authInterceptor for Supabase API Key and Authorization)
    private val okHttpClientStorage = OkHttpClient.Builder()
        .addInterceptor(authInterceptor) // Use the standard authInterceptor for Supabase API Key and Authorization
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Retrofit instance for the main API
    private val retrofitApi: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_API)
            .client(okHttpClientApi)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Retrofit instance for Supabase Storage API
    private val retrofitStorage: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_STORAGE) // Supabase Storage base URL
            .client(okHttpClientStorage) // Use the client with Supabase API key and Authorization
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provides an instance of the specified API service.
     * Selects the appropriate Retrofit instance based on the service class requested.
     */
    fun <T> getService(serviceClass: Class<T>): T {
        return when (serviceClass) {
            ApiService::class.java -> retrofitApi.create(serviceClass) as T
            StorageApiService::class.java -> retrofitStorage.create(serviceClass) as T
            else -> throw IllegalArgumentException("Unknown service class: ${serviceClass.name}")
        }
    }
}
