package com.example.meterreadingsapp.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.*
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.api.S3Client
import com.example.meterreadingsapp.data.*
import com.example.meterreadingsapp.workers.S3UploadWorker
import com.example.meterreadingsapp.workers.SyncWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.math.roundToInt

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

    fun getAllProjectsFromDb(): Flow<List<Project>> = projectDao.getAllProjects()
    fun getBuildingsByProjectIdFromDb(projectId: String): Flow<List<Building>> = buildingDao.getBuildingsByProjectId(projectId)
    fun getMetersByBuildingIdFromDb(buildingId: String): Flow<List<Meter>> = meterDao.getMetersByBuildingId(buildingId)

    suspend fun refreshAllData() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching projects...")
                val projectsResponse = apiService.getProjects()
                if (projectsResponse.isSuccessful) {
                    val projects = projectsResponse.body() ?: emptyList()
                    projectDao.deleteAllProjects()
                    projectDao.insertAll(projects)
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
                }
                Log.d(TAG, "Fetching all meters...")
                val metersResponse = apiService.getAllMeters()
                if (metersResponse.isSuccessful) {
                    val meters = metersResponse.body() ?: emptyList()
                    meterDao.deleteAllMeters()
                    meterDao.insertAll(meters)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during full data refresh: ${e.message}", e)
            }
        }
    }

    suspend fun createNewMeter(newMeterRequest: NewMeterRequest, initialReading: Reading): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Step 1: Creating new meter with number ${newMeterRequest.number}")
                val createMeterResponse = apiService.createMeter(newMeterRequest)
                val newMeter = createMeterResponse.body()?.firstOrNull()
                    ?: throw Exception("Failed to create new meter. Error: ${createMeterResponse.errorBody()?.string()}")
                Log.d(TAG, "New meter created with ID: ${newMeter.id}")
                val finalInitialReading = initialReading.copy(meter_id = newMeter.id)
                Log.d(TAG, "Step 2: Posting initial reading for new meter ${newMeter.id}")
                val postReadingResponse = apiService.postReading(finalInitialReading)
                if (!postReadingResponse.isSuccessful) {
                    throw Exception("Failed to post initial reading. Error: ${postReadingResponse.errorBody()?.string()}")
                }
                Log.d(TAG, "New meter and initial reading created successfully.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Create new meter failed: ${e.message}", e)
                false
            }
        }
    }

    suspend fun performMeterExchange(
        oldMeter: Meter,
        oldMeterLastReading: Reading,
        newMeterNumber: String,
        newMeterInitialReading: Reading
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Step 1: Posting final reading for old meter ${oldMeter.id}")
                val postOldReadingResponse = apiService.postReading(oldMeterLastReading)
                if (!postOldReadingResponse.isSuccessful) throw Exception("Failed to post final reading. Error: ${postOldReadingResponse.errorBody()?.string()}")
                Log.d(TAG, "Step 2: Updating status for old meter ${oldMeter.id}")
                val updateStatusRequest = UpdateMeterRequest(status = "Exchanged")
                val updateStatusResponse = apiService.updateMeter("eq.${oldMeter.id}", updateStatusRequest)
                if (!updateStatusResponse.isSuccessful) throw Exception("Failed to update old meter status. Error: ${updateStatusResponse.errorBody()?.string()}")
                Log.d(TAG, "Step 3: Creating new meter with number $newMeterNumber")
                val newMeterRequest = NewMeterRequest(
                    number = newMeterNumber,
                    projectId = oldMeter.projectId,
                    buildingId = oldMeter.buildingId,
                    energyType = oldMeter.energyType,
                    type = oldMeter.type,
                    replacedOldMeterId = oldMeter.id,
                    street = oldMeter.street,
                    postalCode = oldMeter.postalCode,
                    city = oldMeter.city,
                    houseNumber = oldMeter.houseNumber,
                    houseNumberAddition = oldMeter.houseNumberAddition
                )
                val createMeterResponse = apiService.createMeter(newMeterRequest)
                val newMeter = createMeterResponse.body()?.firstOrNull() ?: throw Exception("Failed to create new meter. Error: ${createMeterResponse.errorBody()?.string()}")
                Log.d(TAG, "New meter created with ID: ${newMeter.id}")
                Log.d(TAG, "Step 4: Linking old meter ${oldMeter.id} to new meter ${newMeter.id}")
                val linkMetersRequest = UpdateMeterRequest(exchangedWithNewMeterId = newMeter.id)
                val linkMetersResponse = apiService.updateMeter("eq.${oldMeter.id}", linkMetersRequest)
                if (!linkMetersResponse.isSuccessful) throw Exception("Failed to link old meter to new meter. Error: ${linkMetersResponse.errorBody()?.string()}")
                Log.d(TAG, "Step 5: Posting initial reading for new meter ${newMeter.id}")
                val finalNewMeterReading = newMeterInitialReading.copy(meter_id = newMeter.id)
                val postNewReadingResponse = apiService.postReading(finalNewMeterReading)
                if (!postNewReadingResponse.isSuccessful) throw Exception("Failed to post initial reading for new meter. Error: ${postNewReadingResponse.errorBody()?.string()}")
                Log.d(TAG, "Meter exchange sequence completed successfully.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Meter exchange failed: ${e.message}", e)
                false
            }
        }
    }

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

    // UPDATED: This function now compresses the image before uploading
    suspend fun uploadFileToS3(imageUri: Uri, fullStoragePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                // --- COMPRESSION LOGIC ---
                val inputStream = appContext.contentResolver.openInputStream(imageUri)
                    ?: throw IOException("Could not open input stream for URI: $imageUri")

                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                // Resize logic
                val maxHeight = 1280.0
                val maxWidth = 1280.0
                val width = originalBitmap.width
                val height = originalBitmap.height
                val ratio: Double = if (width > height) {
                    maxWidth / width
                } else {
                    maxHeight / height
                }
                val newWidth = (width * ratio).roundToInt()
                val newHeight = (height * ratio).roundToInt()
                val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, false)

                // Create a temporary file to hold the compressed image
                tempFile = File.createTempFile("compressed_", ".jpg", appContext.cacheDir)
                val outputStream = FileOutputStream(tempFile)
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()
                // --- END COMPRESSION LOGIC ---

                val s3Client = S3Client.getInstance()

                val metadata = ObjectMetadata()
                metadata.contentLength = tempFile.length()
                metadata.contentType = "image/jpeg"

                val bucketName = fullStoragePath.substringBefore('/')
                val key = fullStoragePath.substringAfter('/')

                Log.d(TAG, "Uploading compressed image to Minio. Bucket: $bucketName, Key: $key, Size: ${tempFile.length() / 1024} KB")

                val putObjectRequest = PutObjectRequest(bucketName, key, tempFile.inputStream(), metadata)
                s3Client.putObject(putObjectRequest)

                Log.d(TAG, "Minio upload successful for key: $key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file to Minio S3: ${e.message}", e)
                false
            } finally {
                // Clean up the temporary file
                tempFile?.delete()
            }
        }
    }

    fun queueImageUpload(imageUri: Uri, fullStoragePath: String, projectId: String, meterId: String) {
        val inputData = workDataOf(S3UploadWorker.KEY_IMAGE_URI to imageUri.toString(), S3UploadWorker.KEY_S3_KEY to fullStoragePath, S3UploadWorker.KEY_PROJECT_ID to projectId, S3UploadWorker.KEY_METER_ID to meterId)
        val uploadWorkRequest = OneTimeWorkRequestBuilder<S3UploadWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(inputData).build()
        workManager.enqueue(uploadWorkRequest)
        Log.d(TAG, "S3UploadWorker scheduled for image: $fullStoragePath")
    }

    suspend fun postFileMetadata(fileName: String, bucketName: String, storagePath: String, fileSize: Long, fileMimeType: String, entityId: String, entityType: String, documentType: String = "other"): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val fileMetadata = FileMetadata(id = UUID.randomUUID().toString(), name = fileName, bucket = bucketName, storage_path = storagePath, size = fileSize, type = fileMimeType, metadata = mapOf("entity_id" to entityId, "entity_type" to entityType, "document_type" to documentType))
                val response = apiService.postFileMetadata(fileMetadata)
                if (!response.isSuccessful) {
                    queueRequest("file_metadata", "files", "POST", gson.toJson(fileMetadata))
                }
                true
            } catch (e: IOException) {
                val fileMetadata = FileMetadata(id = UUID.randomUUID().toString(), name = fileName, bucket = bucketName, storage_path = storagePath, size = fileSize, type = fileMimeType, metadata = mapOf("entity_id" to entityId, "entity_type" to entityType, "document_type" to documentType))
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
        val syncWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        workManager.enqueue(syncWorkRequest)
    }
}

