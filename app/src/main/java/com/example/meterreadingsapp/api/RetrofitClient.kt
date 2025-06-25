package com.example.meterreadingsapp.api

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.example.meterreadingsapp.BuildConfig

/**
 * Singleton object for configuring and providing the Retrofit API services.
 * This ensures distinct instances of Retrofit for the main API and the S3 Storage API.
 */
object RetrofitClient {

    private val API_KEY = BuildConfig.SUPABASE_API_KEY

    // Base URL for your main Supabase API.
    private const val BASE_URL_API = "https://rtbkdkofphqzifnozvqe.supabase.co/rest/v1/"

    // Placeholder Base URL for S3 Storage.
    // When using @Url, this base URL is ignored, but Retrofit still requires a valid one.
    private const val BASE_URL_STORAGE = "https://rtbkdkofphqzifnozvqe.supabase.co/storage/v1/s3" // Can be any valid URL, it's overridden by @Url


    // Interceptor to add API key to headers for the main API
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("apikey", API_KEY)
            .build()
        chain.proceed(newRequest)
    }

    // Configure OkHttpClient for logging network requests
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Log the full request and response body
    }

    // OkHttpClient for the main API (includes auth interceptor)
    private val okHttpClientApi = OkHttpClient.Builder()
        .addInterceptor(authInterceptor) // Add the authentication interceptor
        .addInterceptor(loggingInterceptor) // Add the logging interceptor
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // OkHttpClient for S3 Storage uploads (no auth interceptor needed, pre-signed URLs handle auth)
    private val okHttpClientStorage = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor) // Still include logging for storage uploads
        .connectTimeout(60, TimeUnit.SECONDS) // Potentially longer timeout for large file uploads
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

    // Retrofit instance for S3 Storage API
    private val retrofitStorage: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_STORAGE) // Placeholder, @Url will override this
            .client(okHttpClientStorage)
            // Note: No converter factory needed for Response<Unit> from uploadFileToS3,
            // but including GsonConverterFactory for consistency if other storage endpoints might return JSON.
            // If only Response<Unit> is ever expected, you could omit this.
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
