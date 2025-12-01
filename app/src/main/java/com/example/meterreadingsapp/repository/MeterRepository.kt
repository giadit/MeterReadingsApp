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
    private val obisCodeDao: ObisCodeDao,
    private val meterObisDao: MeterObisDao,
    private val appContext: Context
) {
    private val TAG = "MeterRepository"
    private val gson = Gson()
    private val workManager = WorkManager.getInstance(appContext)

    fun getAllProjectsFromDb(): Flow<List<Project>> = projectDao.getAllProjects()
    fun getBuildingsByProjectIdFromDb(projectId: String): Flow<List<Building>> = buildingDao.getBuildingsByProjectId(projectId)
    fun getMetersWithObisByBuildingIdFromDb(buildingId: String): Flow<List<MeterWithObisPoints>> = meterDao.getMetersWithObisByBuildingId(buildingId)
    fun getAllObisCodesFromDb(): Flow<List<ObisCode>> = obisCodeDao.getAllObisCodes()

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

                Log.d(TAG, "Fetching OBIS codes...")
                val obisCodesResponse = apiService.getObisCodes()
                if (obisCodesResponse.isSuccessful) {
                    val obisCodes = obisCodesResponse.body() ?: emptyList()
                    obisCodeDao.deleteAllObisCodes()
                    obisCodeDao.insertAll(obisCodes)
                }

                Log.d(TAG, "Fetching Meter OBIS links...")
                val meterObisResponse = apiService.getMeterObis()
                if (meterObisResponse.isSuccessful) {
                    val meterObis = meterObisResponse.body() ?: emptyList()
                    meterObisDao.deleteAllMeterObis()
                    meterObisDao.insertAll(meterObis)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during full data refresh: ${e.message}", e)
            }
        }
    }

    suspend fun createNewMeter(
        newMeterRequest: NewMeterRequest,
        initialReadingsMap: Map<String, Reading>
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Create Meter
                Log.d(TAG, "Step 1: Creating new meter with number ${newMeterRequest.number}")
                val createMeterResponse = apiService.createMeter(newMeterRequest)
                val newMeter = createMeterResponse.body()?.firstOrNull()
                    ?: throw Exception("Failed to create new meter. Error: ${createMeterResponse.errorBody()?.string()}")
                Log.d(TAG, "New meter created with ID: ${newMeter.id}")

                // Step 2: Create OBIS Links
                if (initialReadingsMap.isNotEmpty()) {
                    val obisCodeIds = initialReadingsMap.keys.toList()
                    Log.d(TAG, "Step 2: Creating ${obisCodeIds.size} OBIS links")

                    val newObisLinks = obisCodeIds.map { obisId ->
                        NewMeterObisRequest(
                            meterId = newMeter.id,
                            obisCodeId = obisId
                        )
                    }
                    val createLinksResponse = apiService.createMeterObis(newObisLinks)
                    if (!createLinksResponse.isSuccessful) {
                        throw Exception("Failed to create OBIS links. Error: ${createLinksResponse.errorBody()?.string()}")
                    }

                    // Step 3: Fetch new links to get IDs
                    Log.d(TAG, "Step 3: Fetching new OBIS link IDs")
                    val fetchedLinksResponse = apiService.getMeterObisByMeterId("eq.${newMeter.id}")
                    if (!fetchedLinksResponse.isSuccessful) {
                        throw Exception("Failed to fetch new OBIS links. Error: ${fetchedLinksResponse.errorBody()?.string()}")
                    }
                    val fetchedLinks = fetchedLinksResponse.body() ?: emptyList()

                    // Step 4: Post Readings mapped to Link IDs
                    Log.d(TAG, "Step 4: Posting initial readings")
                    initialReadingsMap.forEach { (obisCodeId, reading) ->
                        val link = fetchedLinks.find { it.obisCodeId == obisCodeId }
                        if (link != null) {
                            val finalReading = reading.copy(
                                meter_id = newMeter.id,
                                meterObisId = link.id,
                                migrationStatus = null
                            )
                            val postResp = apiService.postReading(finalReading)
                            if (!postResp.isSuccessful) {
                                Log.e(TAG, "Failed to post reading for OBIS $obisCodeId. Error: ${postResp.errorBody()?.string()}")
                            }
                        } else {
                            Log.e(TAG, "Could not find created link for OBIS Code ID: $obisCodeId")
                        }
                    }
                } else {
                    Log.d(TAG, "No OBIS codes selected. Creating legacy reading if available (not supported in UI but handled here).")
                }

                Log.d(TAG, "New meter creation sequence completed.")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Create new meter sequence failed: ${e.message}", e)
                false
            }
        }
    }

    suspend fun performMeterExchange(
        oldMeter: Meter,
        oldMeterReadings: List<Reading>,
        newMeterNumber: String,
        newMeterReadingsMap: Map<String, Reading> // Key: ObisCodeId, Value: Reading (with temporary ID, no meter_id)
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Post final readings for old meter
                Log.d(TAG, "Step 1: Posting ${oldMeterReadings.size} final readings for old meter ${oldMeter.id}")
                oldMeterReadings.forEach { reading ->
                    val finalReading = reading.copy(migrationStatus = null) // Ensure clean state
                    val response = apiService.postReading(finalReading)
                    if (!response.isSuccessful) {
                        throw Exception("Failed to post final reading for old meter. Error: ${response.errorBody()?.string()}")
                    }
                }

                // Step 2: Update status for old meter
                Log.d(TAG, "Step 2: Updating status for old meter ${oldMeter.id}")
                val updateStatusRequest = UpdateMeterRequest(status = "Ausgetauscht")
                val updateStatusResponse = apiService.updateMeter("eq.${oldMeter.id}", updateStatusRequest)
                if (!updateStatusResponse.isSuccessful) throw Exception("Failed to update old meter status. Error: ${updateStatusResponse.errorBody()?.string()}")

                // Step 3: Create new meter
                Log.d(TAG, "Step 3: Creating new meter with number $newMeterNumber")
                val newMeterRequest = NewMeterRequest(
                    number = newMeterNumber,
                    projectId = oldMeter.projectId,
                    buildingId = oldMeter.buildingId,
                    energyType = oldMeter.energyType,
                    type = oldMeter.type,
                    status = "Aktiv",
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

                // Step 4: Copy OBIS links from old meter to new meter
                Log.d(TAG, "Step 4: Handling OBIS links")
                val oldObisLinksResponse = apiService.getMeterObisByMeterId("eq.${oldMeter.id}")
                val oldObisLinks = oldObisLinksResponse.body()

                if (oldObisLinks.isNullOrEmpty()) {
                    Log.d(TAG, "Old meter has no OBIS links to copy.")
                    // If there were legacy readings passed for the new meter (key "legacy_single"), handle them
                    val legacyReading = newMeterReadingsMap["legacy_single"]
                    if (legacyReading != null) {
                        val finalLegacyReading = legacyReading.copy(meter_id = newMeter.id)
                        val postResp = apiService.postReading(finalLegacyReading)
                        if (!postResp.isSuccessful) Log.e(TAG, "Failed to post legacy reading for new meter.")
                    }
                } else {
                    Log.d(TAG, "Copying ${oldObisLinks.size} OBIS links to new meter ${newMeter.id}")
                    val newObisLinkRequests = oldObisLinks.map {
                        NewMeterObisRequest(
                            meterId = newMeter.id,
                            obisCodeId = it.obisCodeId
                        )
                    }
                    val createLinksResponse = apiService.createMeterObis(newObisLinkRequests)
                    if (!createLinksResponse.isSuccessful) throw Exception("Failed to create new OBIS links. Error: ${createLinksResponse.errorBody()?.string()}")

                    // Step 5: Fetch newly created OBIS links to get their IDs
                    Log.d(TAG, "Step 5: Fetching new OBIS link IDs to map readings")
                    // Small delay might be needed for consistency, but typically Supabase is fast enough
                    val newObisLinksResponse = apiService.getMeterObisByMeterId("eq.${newMeter.id}")
                    if (!newObisLinksResponse.isSuccessful) throw Exception("Failed to fetch new OBIS links. Error: ${newObisLinksResponse.errorBody()?.string()}")
                    val newObisLinks = newObisLinksResponse.body() ?: emptyList()

                    // Step 6: Map readings to new OBIS links and post them
                    Log.d(TAG, "Step 6: Posting initial readings for new meter")
                    newMeterReadingsMap.forEach { (obisCodeId, reading) ->
                        if (obisCodeId != "legacy_single") {
                            val matchingLink = newObisLinks.find { it.obisCodeId == obisCodeId }
                            if (matchingLink != null) {
                                val finalReading = reading.copy(
                                    meter_id = newMeter.id,
                                    meterObisId = matchingLink.id,
                                    migrationStatus = null
                                )
                                val postResp = apiService.postReading(finalReading)
                                if (!postResp.isSuccessful) {
                                    Log.e(TAG, "Failed to post initial reading for OBIS $obisCodeId. Error: ${postResp.errorBody()?.string()}")
                                    // Don't throw, try to continue posting others
                                }
                            } else {
                                Log.e(TAG, "Could not find matching new OBIS link for code ID: $obisCodeId")
                            }
                        }
                    }
                }

                // Step 7: Link meters
                Log.d(TAG, "Step 7: Linking old meter to new meter")
                val linkMetersRequest = UpdateMeterRequest(exchangedWithNewMeterId = newMeter.id)
                val linkMetersResponse = apiService.updateMeter("eq.${oldMeter.id}", linkMetersRequest)
                if (!linkMetersResponse.isSuccessful) throw Exception("Failed to link old meter to new meter. Error: ${linkMetersResponse.errorBody()?.string()}")

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

    suspend fun uploadFileToS3(imageUri: Uri, fullStoragePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val inputStream = appContext.contentResolver.openInputStream(imageUri)
                    ?: throw IOException("Could not open input stream for URI: $imageUri")

                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

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

                tempFile = File.createTempFile("compressed_", ".jpg", appContext.cacheDir)
                val outputStream = FileOutputStream(tempFile)
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                outputStream.flush()
                outputStream.close()

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