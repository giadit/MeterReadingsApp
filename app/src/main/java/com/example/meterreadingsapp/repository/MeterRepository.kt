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
import com.example.meterreadingsapp.data.Building
import com.example.meterreadingsapp.data.BuildingDao
import com.example.meterreadingsapp.data.FileMetadata
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.LocationDao
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.Project
import com.example.meterreadingsapp.data.ProjectDao
import com.example.meterreadingsapp.data.QueuedRequest
import com.example.meterreadingsapp.data.QueuedRequestDao
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.workers.S3UploadWorker
import com.example.meterreadingsapp.workers.SyncWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Repository class that abstracts the data sources (API and local database).
 * This has been updated to handle the new Project -> Building -> Meter hierarchy.
 */
class MeterRepository(
    private val apiService: ApiService,
    private val meterDao: MeterDao,
    private val readingDao: ReadingDao,
    private val projectDao: ProjectDao,
    private val buildingDao: BuildingDao, // ADDED: DAO for buildings
    private val queuedRequestDao: QueuedRequestDao,
    // REMOVED: LocationDao is now obsolete for the primary navigation flow.
    // We keep it for now to prevent breaking other parts of the app, but it will be removed.
    private val locationDao: LocationDao,
    private val appContext: Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(appContext)

    private val storageApiService: StorageApiService = RetrofitClient.getService(StorageApiService::class.java, appContext)
    private val SUPABASE_BUCKET_NAME = "project-documents"

    // --- Data Fetching from Database (for ViewModel) ---

    fun getAllProjectsFromDb(): Flow<List<Project>> {
        return projectDao.getAllProjects()
    }

    fun getBuildingsByProjectIdFromDb(projectId: String): Flow<List<Building>> {
        return buildingDao.getBuildingsByProjectId(projectId)
    }

    fun getMetersByBuildingIdFromDb(buildingId: String): Flow<List<Meter>> {
        return meterDao.getMetersByBuildingId(buildingId)
    }


    /**
     * Refreshes all data from the API according to the new hierarchy.
     * 1. Fetches all projects.
     * 2. For each project, fetches its associated buildings.
     * 3. Fetches all meters and saves them.
     */
    suspend fun refreshAllData() {
        withContext(Dispatchers.IO) {
            try {
                // 1. Refresh Projects
                Log.d(TAG, "Attempting to fetch all projects from API...")
                val projectsResponse = apiService.getProjects()
                if (projectsResponse.isSuccessful) {
                    val projects = projectsResponse.body() ?: emptyList()
                    Log.d(TAG, "Successfully fetched ${projects.size} projects.")
                    projectDao.deleteAllProjects()
                    projectDao.insertAll(projects)
                    Log.d(TAG, "Projects refreshed in local database.")

                    // 2. Refresh Buildings for each project
                    buildingDao.deleteAllBuildings() // Clear out all old buildings
                    for (project in projects) {
                        Log.d(TAG, "Fetching buildings for project: ${project.name}")
                        val buildingsResponse = apiService.getBuildings("eq.${project.id}")
                        if (buildingsResponse.isSuccessful) {
                            val buildings = buildingsResponse.body() ?: emptyList()
                            Log.d(TAG, "Fetched ${buildings.size} buildings for project ${project.name}.")
                            if (buildings.isNotEmpty()) {
                                buildingDao.insertAll(buildings)
                            }
                        } else {
                            Log.e(TAG, "Failed to fetch buildings for project ${project.id}: ${buildingsResponse.message()}")
                        }
                    }
                    Log.d(TAG, "All buildings refreshed in local database.")

                } else {
                    Log.e(TAG, "Failed to fetch projects: ${projectsResponse.message()}")
                }

                // 3. Refresh All Meters
                // This is still a broad fetch, but ensures our meter cache is up-to-date.
                Log.d(TAG, "Attempting to fetch all meters from API...")
                val metersResponse = apiService.getAllMeters()
                if (metersResponse.isSuccessful) {
                    val meters = metersResponse.body() ?: emptyList()
                    Log.d(TAG, "Successfully fetched ${meters.size} meters.")
                    meterDao.deleteAllMeters()
                    meterDao.insertAll(meters)
                    Log.d(TAG, "All meters refreshed in local database.")
                } else {
                    Log.e(TAG, "Failed to fetch all meters: ${metersResponse.message()}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during full data refresh: ${e.message}", e)
            }
        }
    }


    // --- Reading and Image Upload Logic (largely unchanged) ---

    suspend fun postMeterReading(reading: Reading): Response<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to POST new meter reading for meter ID: ${reading.meter_id}")
                val response = apiService.postReading(reading)
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully POSTed reading.")
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Failed to POST meter reading: ${response.code()} - $errorBody")
                    queueRequest("reading", "readings", "POST", gson.toJson(reading))
                }
                response
            } catch (e: IOException) {
                Log.e(TAG, "Network error during POST, queuing request: ${e.message}")
                queueRequest("reading", "readings", "POST", gson.toJson(reading))
                // Return a synthetic success to the UI, as it's been queued.
                Response.success(Unit)
            }
        }
    }

    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String, meterId: String) {
        val inputData = workDataOf(
            S3UploadWorker.KEY_IMAGE_URI to imageUri.toString(),
            S3UploadWorker.KEY_S3_KEY to fullStoragePath,
            S3UploadWorker.KEY_PROJECT_ID to projectId,
            S3UploadWorker.KEY_METER_ID to meterId
        )
        val uploadWorkRequest = OneTimeWorkRequestBuilder<S3UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(inputData)
            .build()
        workManager.enqueue(uploadWorkRequest)
        Log.d(TAG, "S3UploadWorker scheduled for image: $fullStoragePath")
    }

    // ... (uploadFileToS3, postFileMetadata, processQueuedRequest, and queueRequest methods remain the same)
    // You do not need to edit anything below this line in this file.

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

