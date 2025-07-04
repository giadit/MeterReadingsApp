package com.example.meterreadingsapp.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.StorageApiService
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.LocationDao
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.data.QueuedRequestDao
import com.example.meterreadingsapp.data.ProjectDao
import com.example.meterreadingsapp.data.QueuedRequest
import com.example.meterreadingsapp.data.FileMetadata
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull // CORRECTED: Changed toMediaTypeOrOrNull to toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Repository class responsible for mediating data operations between the ViewModel,
 * the local Room database, and the remote API.
 * It handles data fetching, caching, and synchronization.
 *
 * @param apiService The Retrofit service for API calls.
 * @param meterDao The DAO for Meter entities.
 * @param readingDao The DAO for Reading entities.
 * @param locationDao The DAO for Location entities.
 * @param queuedRequestDao The DAO for QueuedRequest entities.
 * @param projectDao The DAO for Project entities.
 * @param applicationContext The application context, used for file operations.
 */
class MeterRepository(
    private val apiService: ApiService,
    private val meterDao: MeterDao,
    private val readingDao: ReadingDao,
    private val locationDao: LocationDao,
    private val queuedRequestDao: QueuedRequestDao,
    private val projectDao: ProjectDao,
    private val applicationContext: Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson()

    // Inject StorageApiService
    private val storageApiService: StorageApiService = com.example.meterreadingsapp.api.RetrofitClient.getService(StorageApiService::class.java)

    // --- Projects ---
    fun getAllProjects(): Flow<List<Project>> {
        return projectDao.getAllProjects()
    }

    suspend fun refreshProjects() {
        try {
            val response = apiService.getProjects()
            if (response.isSuccessful && response.body() != null) {
                projectDao.insertAll(response.body()!!)
                Log.d(TAG, "Projects refreshed successfully.")
            } else {
                Log.e(TAG, "Failed to refresh projects: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing projects: ${e.message}", e)
            throw e // Re-throw to be handled by ViewModel
        }
    }

    fun getProjectById(projectId: String): Flow<Project?> {
        return projectDao.getProjectById(projectId)
    }

    // --- Locations ---
    fun getLocationsForProject(projectId: String): Flow<List<Location>> {
        // Observe all meters, then map them to locations and filter by project ID
        return meterDao.getAllMeters().map { meters ->
            val locationsMap = mutableMapOf<String, Location>()
            meters.forEach { meter ->
                // Only process meters belonging to the selected project
                if (meter.project_id == projectId) {
                    val locationId = generateLocationId(meter)
                    if (!locationsMap.containsKey(locationId)) {
                        val locationName = buildLocationName(meter)
                        locationsMap[locationId] = Location(
                            id = locationId,
                            name = locationName,
                            project_id = meter.project_id,
                            address = meter.address,
                            postal_code = meter.postal_code,
                            city = meter.city,
                            house_number = meter.house_number,
                            house_number_addition = meter.house_number_addition
                        )
                    }
                }
            }
            locationsMap.values.toList().sortedBy { it.name } // Sort by name for consistent display
        }
    }

    private fun generateLocationId(meter: Meter): String {
        // Use a combination of address, postal code, city, house number, and addition for a unique ID
        // Ensure consistent order and handle nulls/blanks
        return "${meter.address ?: ""}|${meter.postal_code ?: ""}|${meter.city ?: ""}|${meter.house_number ?: ""}|${meter.house_number_addition ?: ""}".trim().lowercase()
    }

    private fun buildLocationName(meter: Meter): String {
        val parts = mutableListOf<String>()
        meter.address.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        meter.house_number?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        meter.house_number_addition?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        meter.city?.takeIf { it.isNotBlank() }?.let { parts.add(it) }

        return if (parts.isNotEmpty()) {
            parts.joinToString(" ")
        } else {
            "Unknown Location"
        }
    }

    // --- Meters ---
    fun getMetersForLocation(locationId: String): Flow<List<Meter>> {
        // Observe all meters, then filter by location ID
        return meterDao.getAllMeters().map { meters ->
            meters.filter { meter ->
                generateLocationId(meter) == locationId
            }
        }
    }

    suspend fun refreshMeters() {
        try {
            val response = apiService.getAllMeters()
            if (response.isSuccessful && response.body() != null) {
                meterDao.insertAll(response.body()!!)
                Log.d(TAG, "Meters refreshed successfully.")
            } else {
                Log.e(TAG, "Failed to refresh meters: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing meters: ${e.message}", e)
            throw e // Re-throw to be handled by ViewModel
        }
    }

    /**
     * Adds a new meter to the local database and attempts to sync it with the API.
     * @param meter The Meter object to add.
     */
    suspend fun addMeter(meter: Meter) {
        meterDao.insert(meter) // Insert into local DB immediately

        val requestBody = gson.toJson(meter)
        val queuedRequest = QueuedRequest(
            type = "add_meter",
            endpoint = "meters",
            method = "POST",
            body = requestBody
        )
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Meter ${meter.number} added locally and queued for API sync.")
    }

    /**
     * Updates an existing meter in the local database and attempts to sync it with the API.
     * @param meter The Meter object with updated fields.
     */
    suspend fun patchMeter(meter: Meter) {
        meterDao.update(meter) // Update local DB immediately

        val requestBody = gson.toJson(meter)
        val queuedRequest = QueuedRequest(
            type = "patch_meter",
            endpoint = "meters?id=eq.${meter.id}", // Target specific meter by ID
            method = "PATCH",
            body = requestBody
        )
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Meter ${meter.number} updated locally and queued for API sync.")
    }

    /**
     * Deletes a meter from the local database and attempts to sync the deletion with the API.
     * @param meterId The ID of the meter to delete.
     */
    suspend fun deleteMeter(meterId: String) {
        meterDao.deleteById(meterId) // Delete from local DB immediately

        val queuedRequest = QueuedRequest(
            type = "delete_meter",
            endpoint = "meters?id=eq.$meterId", // Target specific meter by ID
            method = "DELETE",
            body = "" // No body needed for DELETE
        )
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Meter $meterId deleted locally and queued for API sync.")
    }


    // --- Readings ---
    suspend fun postReading(reading: Reading) {
        // Insert into local DB first
        readingDao.insert(reading)

        val requestBody = gson.toJson(reading)
        val queuedRequest = QueuedRequest(
            type = "reading",
            endpoint = "readings",
            method = "POST",
            body = requestBody
        )
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Reading for meter ${reading.meter_id} added locally and queued for API sync.")
    }

    /**
     * Queues an image upload request for background processing.
     * This creates a QueuedRequest of type "image_upload" that will be processed by SyncWorker.
     * @param fileUri The local URI of the image file.
     * @param storagePath The full path in Supabase Storage (e.g., "bucket/folder/filename.jpg").
     * @param projectId The ID of the project associated with the image.
     * @param meterId The ID of the meter associated with the image.
     */
    suspend fun queueImageUpload(fileUri: Uri, storagePath: String, projectId: String, meterId: String) {
        val data = mapOf(
            "fileUri" to fileUri.toString(),
            "storagePath" to storagePath,
            "projectId" to projectId,
            "meterId" to meterId
        )
        val requestBody = gson.toJson(data)
        val queuedRequest = QueuedRequest(
            type = "image_upload",
            endpoint = "storage", // A logical endpoint for the worker to identify
            method = "POST", // Logical method for the worker
            body = requestBody
        )
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Image for meter $meterId queued for upload.")
    }

    /**
     * Uploads an image file to Supabase Storage.
     * @param fileUriString The URI string of the local file to upload.
     * @param storagePath The full path in Supabase Storage (e.g., "bucket/folder/filename.jpg").
     * @return True if upload was successful, false otherwise.
     */
    suspend fun uploadImageToStorage(fileUriString: String, storagePath: String): Boolean {
        return try {
            val fileUri = Uri.parse(fileUriString)
            val file = uriToFile(applicationContext, fileUri)
            if (!file.exists()) {
                Log.e(TAG, "File does not exist at URI: $fileUriString")
                return false
            }

            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

            // Extract bucket name from storagePath for the @Path parameter
            val bucketName = storagePath.substringBefore("/")
            val pathInBucket = storagePath.substringAfter("/", "") // Path relative to bucket

            val response = storageApiService.uploadFile(bucketName, pathInBucket, body)

            if (response.isSuccessful) {
                Log.d(TAG, "Image uploaded successfully to Supabase Storage: $storagePath")
                true
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to upload image to Supabase Storage: ${response.code()} - $errorBody")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading image to Supabase Storage: ${e.message}", e)
            false
        }
    }

    /**
     * Posts file metadata to the main API.
     * @param fileName The name of the file.
     * @param bucketName The name of the storage bucket.
     * @param storagePath The full path of the file in storage.
     * @param fileSize The size of the file in bytes.
     * @param fileMimeType The MIME type of the file.
     * @param entityId The ID of the entity this file is associated with (e.g., meter ID).
     * @param entityType The type of entity (e.g., "meter").
     * @param documentType The type of document (e.g., "photo", "invoice").
     * @return True if metadata was posted successfully, false otherwise.
     */
    suspend fun postFileMetadata(
        fileName: String,
        bucketName: String,
        storagePath: String,
        fileSize: Long,
        fileMimeType: String,
        entityId: String,
        entityType: String,
        documentType: String
    ): Boolean {
        return try {
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
            val response = apiService.postFileMetadata(fileMetadata)
            if (response.isSuccessful) {
                Log.d(TAG, "File metadata posted successfully for $fileName.")
                true
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Failed to post file metadata for $fileName: ${response.code()} - $errorBody")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error posting file metadata for $fileName: ${e.message}", e)
            false
        }
    }

    /**
     * Helper function to convert a content URI to a File.
     * This is necessary because WorkManager often deals with content URIs,
     * but OkHttp's RequestBody.asRequestBody() prefers a File object.
     */
    private fun uriToFile(context: Context, uri: Uri): File {
        val contentResolver = context.contentResolver
        val fileName = getFileName(context, uri) ?: UUID.randomUUID().toString() + ".tmp"
        val file = File(context.cacheDir, fileName) // Use cache directory
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to file: ${e.message}", e)
            throw IOException("Could not create file from URI", e)
        }
        return file
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        result = it.getString(columnIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    /**
     * Processes a single queued request by sending it to the appropriate API endpoint.
     * @param request The QueuedRequest object to process.
     * @return True if the request was successfully sent and resulted in a successful API response, false otherwise.
     */
    suspend fun processQueuedRequest(request: QueuedRequest): Boolean {
        return try {
            when (request.type) {
                "reading" -> {
                    val reading = gson.fromJson(request.body, Reading::class.java)
                    val response = apiService.postReading(reading)
                    response.isSuccessful
                }
                "image_upload" -> {
                    // Assuming body contains a JSON with fileUri and storagePath
                    val data = gson.fromJson(request.body, Map::class.java)
                    val fileUriString = data["fileUri"] as? String
                    val storagePath = data["storagePath"] as? String
                    val projectId = data["projectId"] as? String
                    val meterId = data["meterId"] as? String

                    if (fileUriString != null && storagePath != null && projectId != null && meterId != null) {
                        val uploadSuccess = uploadImageToStorage(fileUriString, storagePath)
                        if (uploadSuccess) {
                            // If image upload is successful, also post metadata
                            val file = uriToFile(applicationContext, Uri.parse(fileUriString)) // Re-create file to get properties
                            val fileName = file.name
                            val fileSize = file.length()
                            val fileMimeType = applicationContext.contentResolver.getType(Uri.parse(fileUriString)) ?: "application/octet-stream"

                            val metadataSuccess = postFileMetadata(
                                fileName = fileName,
                                bucketName = storagePath.substringBefore("/"),
                                storagePath = storagePath,
                                fileSize = fileSize,
                                fileMimeType = fileMimeType,
                                entityId = meterId,
                                entityType = "meter",
                                documentType = "photo"
                            )
                            if (metadataSuccess) {
                                // Delete local file after successful upload and metadata post
                                if (file.exists()) {
                                    file.delete()
                                    Log.d(TAG, "Deleted local image file after successful upload and metadata post: ${file.name}")
                                }
                                true
                            } else {
                                Log.e(TAG, "Failed to post metadata for image: $storagePath")
                                false
                            }
                        } else {
                            Log.e(TAG, "Failed to upload image to storage: $storagePath")
                            false
                        }
                    } else {
                        Log.e(TAG, "Invalid data for image_upload request: ${request.body}")
                        false
                    }
                }
                "add_meter" -> {
                    val meter = gson.fromJson(request.body, Meter::class.java)
                    val response = apiService.addMeter(meter) // Call the new addMeter API endpoint
                    response.isSuccessful
                }
                "patch_meter" -> {
                    val meter = gson.fromJson(request.body, Meter::class.java)
                    // Extract meter ID from the endpoint string for the API call
                    val meterId = request.endpoint.substringAfter("id=eq.").substringBeforeLast("}") // Assuming format "meters?id=eq.UUID"
                    val response = apiService.patchMeter(meterId, meter) // Call the new patchMeter API endpoint
                    response.isSuccessful
                }
                "delete_meter" -> {
                    // Extract meter ID from the endpoint string for the API call
                    val meterId = request.endpoint.substringAfter("id=eq.")
                    val response = apiService.deleteMeter(meterId) // Call the new deleteMeter API endpoint
                    response.isSuccessful
                }
                else -> {
                    Log.w(TAG, "Unknown queued request type: ${request.type}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing queued request ${request.id} (Type: ${request.type}): ${e.message}", e)
            false
        }
    }
}
