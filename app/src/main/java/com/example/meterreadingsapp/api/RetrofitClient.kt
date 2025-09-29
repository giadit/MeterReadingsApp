package com.example.meterreadingsapp.api

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.example.meterreadingsapp.BuildConfig

object RetrofitClient {

    private const val BASE_URL_API = "https://database.berliner-e-agentur.de"
    private const val BASE_URL_STORAGE = "https://rtbkdkofphqzifnozvqe.supabase.co/storage/v1/"
    private val API_KEY = BuildConfig.SUPABASE_API_KEY

    // This needs a Context to initialize SessionManager, so we'll pass it in.
    private var sessionManager: SessionManager? = null

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = sessionManager?.fetchAuthToken()

        val requestBuilder = originalRequest.newBuilder()
            .header("apikey", API_KEY)

        token?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }

        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClientApi = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ... (rest of the file remains the same)
    // You do not need to edit anything below this line in this file.

    private val okHttpClientStorage = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofitApi: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_API)
            .client(okHttpClientApi)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val retrofitStorage: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_STORAGE)
            .client(okHttpClientStorage)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> getService(serviceClass: Class<T>, context: Context): T {
        // Initialize SessionManager here, as we need the context.
        if (sessionManager == null) {
            sessionManager = SessionManager(context.applicationContext)
        }

        return when (serviceClass) {
            ApiService::class.java -> retrofitApi.create(serviceClass) as T
            StorageApiService::class.java -> retrofitStorage.create(serviceClass) as T
            else -> throw IllegalArgumentException("Unknown service class: ${serviceClass.name}")
        }
    }
}

