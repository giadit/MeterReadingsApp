package com.example.meterreadingsapp.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.S3Client
import com.example.meterreadingsapp.data.*
import com.example.meterreadingsapp.workers.S3UploadWorker
import com.example.meterreadingsapp.workers.SyncWorker // ADDED: This line fixes the build error
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.util.*

class MeterRepository(
    private val apiService: ApiService,
    private val meterDao: MeterDao,
    private val readingDao: ReadingDao,
    private val projectDao: ProjectDao,
    private val buildingDao: BuildingDao,
    private val queuedRequestDao: QueuedRequestDao,
    private val appContext: Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(appContext)

    // --- Data Fetching from Database ---
    fun getAllProjectsFromDb(): Flow<List<Project>> = projectDao.getAllProjects()
    fun getBuildingsByProjectIdFromDb(projectId: String): Flow<List<Building>> = buildingDao.getBuildingsByProjectId(projectId)
    fun getMetersByBuildingIdFromDb(buildingId: String): Flow<List<Meter>> = meterDao.getMetersByBuildingId(buildingId)

    // --- Full Data Refresh Logic ---
    suspend fun refreshAllData() {
        withContext(Dispatchers.IO) {
            try {
                // Refresh Projects
                Log.d(TAG, "Fetching projects...")
                val projectsResponse = apiService.getProjects()
                if (projectsResponse.isSuccessful) {
                    val projects = projectsResponse.body() ?: emptyList()
                    projectDao.deleteAllProjects()
                    projectDao.insertAll(projects)
                    Log.d(TAG, "Refreshed ${projects.size} projects.")

                    // Refresh Buildings for each project
                    buildingDao.deleteAllBuildings()
                    for (project in projects) {
                        val buildingsResponse = apiService.getBuildings("eq.${project.id}")
                        if (buildingsResponse.isSuccessful) {
                            val buildings = buildingsResponse.body() ?: emptyList()
                            if (buildings.isNotEmpty()) {
                                buildingDao.insertAll(buildings)
                            }
                        }
                    }
                    Log.d(TAG, "Refreshed all buildings.")
                }

                // Refresh All Meters
                Log.d(TAG, "Fetching all meters...")
                val metersResponse = apiService.getAllMeters()
                if (metersResponse.isSuccessful) {
                    val meters = metersResponse.body() ?: emptyList()
                    meterDao.deleteAllMeters()
                    meterDao.insertAll(meters)
                    Log.d(TAG, "Refreshed ${meters.size} meters.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during full data refresh: ${e.message}", e)
            }
        }
    }

    // --- REWRITTEN: uploadFileToS3 using the new S3Client for Minio ---
    suspend fun uploadFileToS3(imageUri: Uri, fullStoragePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val s3Client = S3Client.getInstance()
                val inputStream = appContext.contentResolver.openInputStream(imageUri)
                    ?: throw IOException("Could not open input stream for URI: $imageUri")

                // Get file metadata (size and type) from the Uri
                val metadata = ObjectMetadata()
                appContext.contentResolver.query(imageUri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (!cursor.isNull(sizeIndex)) {
                            metadata.contentLength = cursor.getLong(sizeIndex)
                        }
                    }
                }
                metadata.contentType = appContext.contentResolver.getType(imageUri)

                // The fullStoragePath from the worker is "bucketName/path/to/file.jpg"
                val bucketName = fullStoragePath.substringBefore('/')
                val key = fullStoragePath.substringAfter('/')

                Log.d(TAG, "Uploading to Minio. Bucket: $bucketName, Key: $key")

                // Create the request and upload the file
                val putObjectRequest = PutObjectRequest(bucketName, key, inputStream, metadata)
                s3Client.putObject(putObjectRequest)

                Log.d(TAG, "Minio upload successful for key: $key")
                true // Indicate success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file to Minio S3: ${e.message}", e)
                false // Indicate failure
            }
        }
    }


    // --- Other methods (unchanged) ---

    suspend fun postMeterReading(reading: Reading): Response<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to POST new meter reading for meter ID: ${reading.meter_id}")
                val response = apiService.postReading(reading)
                if (!response.isSuccessful) {
                    queueRequest("reading", "readings", "POST", gson.toJson(reading))
                }
                response
            } catch (e: IOException) {
                Log.e(TAG, "Network error during POST, queuing request: ${e.message}")
                queueRequest("reading", "readings", "POST", gson.toJson(reading))
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
                val response = apiService.postFileMetadata(fileMetadata)
                if (!response.isSuccessful) {
                    queueRequest("file_metadata", "files", "POST", gson.toJson(fileMetadata))
                }
                true
            } catch (e: IOException) {
                val fileMetadata = FileMetadata(
                    id = UUID.randomUUID().toString(),
                    name = fileName,
                    bucket = bucketName,
                    storage_path = storagePath,
                    size = fileSize,
                    type = fileMimeType,
                    metadata = mapOf("entity_id" to entityId, "entity_type" to entityType, "document_type" to documentType)
                )
                queueRequest("file_metadata", "files", "POST", gson.toJson(fileMetadata))
                true
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error posting file metadata: ${e.message}", e)
                false
            }
        }
    }

    suspend fun processQueuedRequest(request: QueuedRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                when (request.type) {
                    "reading" -> {
                        val reading = gson.fromJson(request.body, Reading::class.java)
                        apiService.postReading(reading).isSuccessful
                    }
                    "file_metadata" -> {
                        val fileMetadata = gson.fromJson(request.body, FileMetadata::class.java)
                        apiService.postFileMetadata(fileMetadata).isSuccessful
                    }
                    else -> false
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
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueue(syncWorkRequest)
    }
}

