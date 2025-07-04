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
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.data.LocationDao
import com.example.meterreadingsapp.data.QueuedRequest
import com.example.meterreadingsapp.data.QueuedRequestDao
import com.example.meterreadingsapp.data.ProjectDao
import com.example.meterreadingsapp.data.FileMetadata // Still needed for API request body
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
import kotlinx.coroutines.flow.first

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Repository class that abstracts the data sources (API and local database) for meters, readings,
 * and projects. It provides a clean API for the ViewModel to interact with data.
 * File metadata will now be queued as a QueuedRequest if API submission fails.
 *
 * @param apiService The Retrofit service for making API calls.
 * @param meterDao The Room DAO for accessing meter data in the local database.
 * @param readingDao The Room DAO for accessing reading data in the local database.
 * @param locationDao The Room DAO for accessing location data in the local database.
 * @param queuedRequestDao The Room DAO for accessing queued requests in the local database.
 * @param projectDao The Room DAO for accessing project data in the local database.
 * @param appContext The application context, needed for WorkManager.
 */
class MeterRepository(
    private val apiService: ApiService,
    private val meterDao: MeterDao,
    private val readingDao: ReadingDao,
    private val locationDao: LocationDao,
    private val queuedRequestDao: QueuedRequestDao,
    private val projectDao: ProjectDao,
    private val appContext: Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(appContext)

    private val storageApiService: StorageApiService = RetrofitClient.getService(StorageApiService::class.java)

    private val SUPABASE_BUCKET_NAME = "project-documents"

    init {
        Log.d(TAG, "MeterRepository initialized with Supabase Storage API approach and ProjectDao. File metadata will be queued.")
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

    // --- Project-related methods ---
    fun getAllProjectsFromDb(): Flow<List<Project>> {
        return projectDao.getAllProjects()
    }

    fun getProjectByIdFromDb(projectId: String): Flow<Project?> {
        return projectDao.getProjectById(projectId)
    }

    fun getLocationsByProjectId(projectId: String?): Flow<List<Location>> {
        return if (projectId == null) {
            // If no project is selected, return all unique locations that have no project_id or empty project_id
            locationDao.getAllLocations().map { locations ->
                locations.filter { it.project_id.isNullOrBlank() }
            }
        } else {
            // Return locations associated with a specific project ID
            locationDao.getAllLocations().map { locations ->
                locations.filter { it.project_id == projectId }
            }
        }
    }

    // --- Meter-related methods ---
    fun getAllMetersFromDb(): Flow<List<Meter>> {
        return meterDao.getAllMeters()
    }

    fun getUniqueLocations(): Flow<List<Location>> {
        return locationDao.getAllLocations()
    }

    /**
     * Retrieves a single location by its unique ID from the database.
     * This method is needed by LocationViewModel to get full location details before querying meters.
     * @param id The unique ID of the location to retrieve.
     * @return A Flow emitting a single Location object or null if not found.
     */
    fun getLocationByIdFromDb(id: String): Flow<Location?> {
        return locationDao.getLocationByIdFromDb(id)
    }

    /**
     * Retrieves meters associated with a specific address, including house number and addition.
     * Parameters are nullable to allow flexible filtering (e.g., get all meters on a street regardless of house number).
     * The query now explicitly handles both NULL and empty string values for nullable parameters.
     * @param address The street address to filter meters by.
     * @param postalCode The postal code to filter meters by (can be null).
     * @param city The city to filter meters by (can be null).
     * @param houseNumber The house number to filter meters by (NEW, can be null).
     * @param houseNumberAddition The house number addition to filter meters by (NEW, can be null).
     * @return A Flow emitting a list of Meter objects matching the given address details.
     */
    fun getMetersByAddress(
        address: String,
        postalCode: String?,
        city: String?,
        houseNumber: String?,
        houseNumberAddition: String?
    ): Flow<List<Meter>> {
        return meterDao.getMetersByAddress(address, postalCode, city, houseNumber, houseNumberAddition)
    }

    /**
     * Refreshes all projects and their associated meters/locations from the API and updates the local database.
     * This is the comprehensive data sync now that we have a project layer.
     */
    suspend fun refreshAllProjectsAndMeters() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to fetch all projects from API...")
                val projectsResponse = apiService.getProjects()
                if (projectsResponse.isSuccessful && projectsResponse.body() != null) {
                    val projects = projectsResponse.body()
                    Log.d(TAG, "Successfully fetched ${projects?.size} projects from API.")
                    projectDao.deleteAllProjects()
                    projects?.let { projectDao.insertAll(it) }
                    Log.d(TAG, "All projects refreshed in local database.")
                } else {
                    val errorBody = projectsResponse.errorBody()?.string()
                    Log.e(TAG, "Failed to fetch all projects: ${projectsResponse.code()} - ${projectsResponse.message()}. Error Body: $errorBody")
                }

                Log.d(TAG, "Attempting to fetch all meters from API...")
                val metersResponse = apiService.getAllMeters()
                if (metersResponse.isSuccessful && metersResponse.body() != null) {
                    val meters = metersResponse.body()
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
                    val errorBody = metersResponse.errorBody()?.string()
                    Log.e(TAG, "Failed to fetch all meters: ${metersResponse.code()} - ${metersResponse.message()}. Error Body: $errorBody")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing all projects and meters: ${e.message}", e)
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
                    refreshAllProjectsAndMeters()
                    Response.success(Unit)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to POST meter reading: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                    Response.error(response.code(), ResponseBody.create(null, errorBody ?: "Unknown error"))
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error during POST, queuing request: ${e.message}", e)
                queueRequest(
                    type = "reading",
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

    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String, meterId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            S3UploadWorker.KEY_IMAGE_URI to imageUri.toString(),
            S3UploadWorker.KEY_S3_KEY to fullStoragePath,
            S3UploadWorker.KEY_PROJECT_ID to projectId,
            S3UploadWorker.KEY_METER_ID to meterId
        )

        val uploadWorkRequest = OneTimeWorkRequestBuilder<S3UploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        workManager.enqueue(uploadWorkRequest)
        Log.d(TAG, "S3UploadWorker scheduled for image: $fullStoragePath (Supabase Storage)")
    }

    suspend fun uploadFileToS3(imageUri: Uri, fullStoragePath: String): Boolean {
        return withContext(Dispatchers.IO) {
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
                )

                if (response.isSuccessful) {
                    Log.d(TAG, "Supabase Storage upload completed successfully for path: $fullStoragePath. Response code: ${response.code()}")
                    true // Indicate success
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = "Supabase Storage upload failed for path: $fullStoragePath. Code: ${response.code()}, Message: ${response.message()}. Error Body: $errorBody"
                    Log.e(TAG, errorMessage)
                    false // Indicate failure
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file to Supabase Storage: ${e.message}", e)
                false // Indicate failure
            } finally {
                tempFile?.delete() // Ensure temporary file is deleted
                Log.d(TAG, "Temporary file deleted: ${tempFile?.absolutePath}")
                inputStream?.close()
            }
        }
    }

    /**
     * Posts file metadata to the backend API. If the API call fails, it queues the request.
     *
     * @param fileName The name of the file.
     * @param bucketName The name of the storage bucket.
     * @param storagePath The full path to the file within the bucket.
     * @param fileSize The size of the file in bytes.
     * @param fileMimeType The MIME type of the file.
     * @param entityId The ID of the entity this file is associated with (e.g., meter ID).
     * @param entityType The type of entity (e.g., "meter", "project").
     * @param documentType The type of document (e.g., "picture", "report", "other"). Defaults to "other".
     * @return True if metadata was posted successfully or queued, false if an unrecoverable error occurred.
     */
    suspend fun postFileMetadata(
        fileName: String,
        bucketName: String,
        storagePath: String,
        fileSize: Long,
        fileMimeType: String,
        entityId: String,
        entityType: String,
        documentType: String = "other"
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fileMetadata = FileMetadata(
                    id = UUID.randomUUID().toString(), // Generate a new UUID for the metadata entry
                    name = fileName,
                    bucket = bucketName,
                    storage_path = storagePath,
                    size = fileSize,
                    type = fileMimeType,
                    metadata = mapOf(
                        "entity_id" to entityId,
                        "entity_type" to entityType,
                        "document_type" to documentType
                    )
                )

                Log.d(TAG, "Attempting to POST file metadata for: $storagePath")
                val response = apiService.postFileMetadata(fileMetadata)

                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully POSTed file metadata. Response status: ${response.code()}")
                    true
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to POST file metadata: ${response.code()} - ${response.message()}. Error Body: $errorBody")
                    // Queue the request if API call fails
                    queueRequest(
                        type = "file_metadata", // NEW: Type for file metadata
                        endpoint = "files",
                        method = "POST",
                        body = gson.toJson(fileMetadata)
                    )
                    true // Indicate that the request was handled (either sent or queued)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Network error during POSTing file metadata, queuing request: ${e.message}", e)
                // Queue the request on network error
                val fileMetadata = FileMetadata( // Re-create FileMetadata for queuing if network error happens before it's sent
                    id = UUID.randomUUID().toString(),
                    name = fileName,
                    bucket = bucketName,
                    storage_path = storagePath,
                    size = fileSize,
                    type = fileMimeType,
                    metadata = mapOf(
                        "entity_id" to entityId,
                        "entity_type" to entityType,
                        "document_type" to documentType
                    )
                )
                queueRequest(
                    type = "file_metadata",
                    endpoint = "files",
                    method = "POST",
                    body = gson.toJson(fileMetadata)
                )
                true // Indicate that the request was handled (queued)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during POSTing file metadata: ${e.message}", e)
                false // Indicate an unrecoverable error
            }
        }
    }

    suspend fun processQueuedRequest(request: QueuedRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                when (request.type) {
                    "reading" -> {
                        val reading = gson.fromJson(request.body, Reading::class.java)
                        val response = apiService.postReading(reading)
                        response.isSuccessful
                    }
                    "file_metadata" -> { // NEW: Handle file_metadata type
                        val fileMetadata = gson.fromJson(request.body, FileMetadata::class.java)
                        val response = apiService.postFileMetadata(fileMetadata)
                        response.isSuccessful
                    }
                    else -> {
                        Log.w(TAG, "Unknown queued request type: ${request.type}")
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing queued request ${request.id}: ${e.message}", e)
                false
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
