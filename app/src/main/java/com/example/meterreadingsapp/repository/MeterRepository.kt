package com.example.meterreadingsapp.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.RetrofitClient
import com.example.meterreadingsapp.api.StorageApiService
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.data.LocationDao
import com.example.meterreadingsapp.data.QueuedRequest
import com.example.meterreadingsapp.data.QueuedRequestDao
import com.example.meterreadingsapp.workers.SyncWorker
import com.example.meterreadingsapp.workers.S3UploadWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository class that abstracts the data sources (API and local database) for meters and readings.
 * It provides a clean API for the ViewModel to interact with data.
 *
 * @param apiService The Retrofit service for making API calls.
 * @param meterDao The Room DAO for accessing meter data in the local database.
 * @param readingDao The Room DAO for accessing reading data in the local database.
 * @param locationDao The Room DAO for accessing location data in the local database.
 * @param queuedRequestDao The Room DAO for accessing queued requests in the local database.
 * @param appContext The application context, needed for WorkManager.
 */
class MeterRepository(
    private val apiService: ApiService,
    private val meterDao: MeterDao,
    private val readingDao: ReadingDao,
    private val locationDao: LocationDao,
    private val queuedRequestDao: QueuedRequestDao,
    private val appContext: Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(appContext)

    private val storageApiService: StorageApiService = RetrofitClient.getService(StorageApiService::class.java)

    // This bucket name is still being used for splitting path, but the actual path is changed in MainActivity
    private val SUPABASE_BUCKET_NAME = "project-documents"

    init {
        Log.d(TAG, "MeterRepository initialized with Supabase Storage API approach.")
    }

    private fun generateLocationId(
        address: String?,
        postalCode: String?,
        city: String?,
        houseNumber: String?,
        houseNumberAddition: String?
    ): String {
        val safeAddress = address ?: ""
        val safePostalCode = postalCode ?: ""
        val safeCity = city ?: ""
        val safeHouseNumber = houseNumber ?: ""
        val safeHouseNumberAddition = houseNumberAddition ?: ""
        return "${safeAddress}|${safePostalCode}|${safeCity}|${safeHouseNumber}|${safeHouseNumberAddition}"
    }

    fun getAllMetersFromDb(): Flow<List<Meter>> {
        return meterDao.getAllMeters()
    }

    fun getUniqueLocations(): Flow<List<Location>> {
        return locationDao.getAllLocations()
    }

    fun getMetersForLocation(
        address: String,
        postalCode: String?,
        city: String?,
        houseNumber: String?,
        houseNumberAddition: String?
    ): Flow<List<Meter>> {
        return meterDao.getMetersByAddress(address, postalCode, city, houseNumber, houseNumberAddition)
    }

    suspend fun refreshAllMeters() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to fetch all meters from API...")
                val response = apiService.getAllMeters()
                if (response.isSuccessful && response.body() != null) {
                    val meters = response.body()
                    Log.d(TAG, "Successfully fetched ${meters?.size} meters from API.")

                    meters?.let { meterList ->
                        val locations = meterList.map { meter ->
                            val meterAddress = meter.address
                            val meterPostalCode = meter.postal_code
                            val meterCity = meter.city
                            val meterHouseNumber = meter.house_number
                            val meterHouseNumberAddition = meter.house_number_addition

                            val locationDisplayName = buildLocationDisplayName(
                                meterAddress,
                                meterHouseNumber,
                                meterHouseNumberAddition
                            ) ?: "No Address Provided"

                            Location(
                                id = generateLocationId(
                                    meterAddress,
                                    meterPostalCode,
                                    meterCity,
                                    meterHouseNumber,
                                    meterHouseNumberAddition
                                ),
                                name = locationDisplayName,
                                project_id = meter.project_id,
                                address = meterAddress,
                                postal_code = meterPostalCode,
                                city = meterCity,
                                house_number = meterHouseNumber,
                                house_number_addition = meterHouseNumberAddition,
                                created_at = meter.created_at,
                                updated_at = meter.updated_at
                            )
                        }.distinctBy { it.id }
                        locationDao.deleteAllLocations()
                        locationDao.insertAll(locations)
                        Log.d(TAG, "Locations refreshed in local database. Count: ${locations.size}")
                    }

                    meterDao.deleteAllMeters()
                    meters?.let { meterDao.insertAll(it) }
                    Log.d(TAG, "All meters refreshed in local database.")

                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to fetch all meters: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing all meters: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }

    private fun buildLocationDisplayName(
        address: String?,
        houseNumber: String?,
        houseNumberAddition: String?
    ): String? {
        val parts = mutableListOf<String>()
        address?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        houseNumber?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        houseNumberAddition?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        val combinedAddressDetails = parts.joinToString(" ").trim()

        return combinedAddressDetails.takeIf { it.isNotBlank() }
    }

    suspend fun postMeterReading(reading: Reading): Response<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to POST new meter reading for meter ID: ${reading.meter_id}. Value: ${reading.value}, Date: ${reading.date}")
                val response = apiService.postReading(reading)
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully POSTed reading. Response status: ${response.code()}")
                    refreshAllMeters()
                    Response.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to POST meter reading: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                    Response.error(response.code(), ResponseBody.create(null, "Error: ${response.code()} - ${response.message()}"))
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error during POST, queuing request: ${e.message}", e)
                queueRequest(
                    type = "reading", // Pass the type "reading"
                    endpoint = "readings",
                    method = "POST",
                    body = gson.toJson(reading)
                )
                Response.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during POST: ${e.message}", e)
                Response.error(500, ResponseBody.create(null, "Error: ${e.message}"))
            }
        }
    }

    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            S3UploadWorker.KEY_IMAGE_URI to imageUri.toString(),
            S3UploadWorker.KEY_S3_KEY to fullStoragePath,
            S3UploadWorker.KEY_PROJECT_ID to projectId
        )

        val uploadWorkRequest = OneTimeWorkRequestBuilder<S3UploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        workManager.enqueue(uploadWorkRequest)
        Log.d(TAG, "S3UploadWorker scheduled for image: $fullStoragePath (Supabase Storage)")
    }

    suspend fun uploadFileToS3(imageUri: Uri, fullStoragePath: String) {
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            var inputStream: InputStream? = null
            try {
                inputStream = appContext.contentResolver.openInputStream(imageUri)
                    ?: throw IOException("Could not open input stream for URI: $imageUri")

                val extension = appContext.contentResolver.getType(imageUri)?.let { mimeType ->
                    when {
                        mimeType.contains("jpeg") || mimeType.contains("jpg") -> ".jpg"
                        mimeType.contains("png") -> ".png"
                        else -> ".tmp"
                    }
                } ?: ".jpg"

                tempFile = File.createTempFile("upload_", extension, appContext.cacheDir)
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()

                if (!tempFile.exists()) {
                    throw IOException("Temporary file not created at path: ${tempFile.absolutePath}")
                }
                Log.d(TAG, "Temporary file created at: ${tempFile.absolutePath}, Size: ${tempFile.length()} bytes")


                val contentType = appContext.contentResolver.getType(imageUri) ?: "application/octet-stream"
                val requestBody = tempFile.asRequestBody(contentType.toMediaTypeOrNull())

                val segments = fullStoragePath.split("/", limit = 2)
                if (segments.size < 2) {
                    val errorMessage = "Invalid fullStoragePath format: $fullStoragePath. Expected 'bucketName/path'."
                    Log.e(TAG, errorMessage)
                    throw IllegalArgumentException(errorMessage)
                }
                val bucketName = segments[0]
                val pathWithinBucket = segments[1]

                Log.d(TAG, "Attempting Supabase Storage upload to bucket: $bucketName, path: $pathWithinBucket (Content-Type: $contentType)")

                val response = storageApiService.uploadFile(
                    bucketName = bucketName,
                    path = pathWithinBucket,
                    file = MultipartBody.Part.createFormData("file", pathWithinBucket, requestBody)
                    // FIX: Removed contentType parameter from here
                )

                if (response.isSuccessful) {
                    Log.d(TAG, "Supabase Storage upload completed successfully for path: $fullStoragePath. Response code: ${response.code()}")
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = "Supabase Storage upload failed for path: $fullStoragePath. Code: ${response.code()}, Message: ${response.message()}. Error Body: $errorBody"
                    Log.e(TAG, errorMessage)
                    throw IOException(errorMessage)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file to Supabase Storage: ${e.message}", e)
                throw e
            } finally {
                tempFile?.delete()
                Log.d(TAG, "Temporary file deleted: ${tempFile?.absolutePath}")
                inputStream?.close()
            }
        }
    }


    private suspend fun queueRequest(type: String, endpoint: String, method: String, body: String) {
        val queuedRequest = QueuedRequest(type = type, endpoint = endpoint, method = method, body = body)
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Request queued locally: ${queuedRequest.id} (Type: $type)")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueue(syncWorkRequest)
        Log.d(TAG, "SyncWorker scheduled.")
    }
}
