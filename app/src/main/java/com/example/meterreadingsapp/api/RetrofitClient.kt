package com.example.meterreadingsapp.api

import android.content.Context
import com.example.meterreadingsapp.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val BASE_URL_API = "https://database.berliner-e-agentur.de/rest/v1/"
    private val API_KEY = BuildConfig.SUPABASE_API_KEY

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

    private val retrofitApi: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_API)
            .client(okHttpClientApi)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> getService(serviceClass: Class<T>, context: Context): T {
        if (sessionManager == null) {
            sessionManager = SessionManager(context.applicationContext)
        }

        return when (serviceClass) {
            ApiService::class.java -> retrofitApi.create(serviceClass) as T
            // The StorageApiService case has been removed.
            else -> throw IllegalArgumentException("Unknown service class: ${serviceClass.name}")
        }
    }
}

