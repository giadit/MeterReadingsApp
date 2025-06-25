package com.example.meterreadingsapp.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.example.meterreadingsapp.BuildConfig
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.data.LocationDao
import com.example.meterreadingsapp.data.QueuedRequest // Ensure QueuedRequest is imported
import com.example.meterreadingsapp.data.QueuedRequestDao
import com.example.meterreadingsapp.workers.SyncWorker
import com.example.meterreadingsapp.workers.S3UploadWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
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

    private val s3Client: AmazonS3Client
    private val transferUtility: TransferUtility

    private val S3_BUCKET_NAME = "project-documents" // Updated S3 bucket name

    init {
        val credentials = BasicAWSCredentials(
            BuildConfig.AWS_ACCESS_KEY_ID,
            BuildConfig.AWS_SECRET_ACCESS_KEY
        )

        s3Client = AmazonS3Client(credentials, Region.getRegion(Regions.fromName(BuildConfig.AWS_REGION)))
        TransferNetworkLossHandler.getInstance(appContext)
        transferUtility = TransferUtility.builder()
            .context(appContext)
            .s3Client(s3Client)
            .defaultBucket(S3_BUCKET_NAME)
            .build()

        Log.d(TAG, "AWS S3 Client and TransferUtility initialized for region: ${BuildConfig.AWS_REGION}")
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
                // FIX: Pass the type "reading" to the queueRequest function
                queueRequest(
                    type = "reading", // ADDED: type parameter
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

    /**
     * Queues an S3 image upload request for later sending using WorkManager.
     *
     * @param imageUri The local URI of the image file to upload.
     * @param s3Key The full S3 object key (path and filename) where the image should be stored.
     * Format: "project_documents/{project_id}/{date_metername}.jpg"
     * @param projectId The project ID associated with the meter.
     */
    fun queueImageUpload(imageUri: Uri, s3Key: String, projectId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = workDataOf(
            S3UploadWorker.KEY_IMAGE_URI to imageUri.toString(),
            S3UploadWorker.KEY_S3_KEY to s3Key,
            S3UploadWorker.KEY_PROJECT_ID to projectId
        )

        val uploadWorkRequest = OneTimeWorkRequestBuilder<S3UploadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        workManager.enqueue(uploadWorkRequest)
        Log.d(TAG, "S3UploadWorker scheduled for image: $s3Key")
    }

    suspend fun uploadFileToS3(imageUri: Uri, s3Key: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(imageUri.path ?: throw IOException("Invalid image URI path"))
                if (!file.exists()) {
                    throw IOException("File does not exist at URI: $imageUri")
                }
                Log.d(TAG, "Starting S3 upload for file: ${file.name} to key: $s3Key")

                val uploadObserver = transferUtility.upload(s3Key, file)

                var isUploadComplete = false
                var uploadError: Exception? = null

                uploadObserver.setTransferListener(object : TransferListener {
                    override fun onStateChanged(id: Int, state: TransferState) {
                        Log.d(TAG, "Transfer ID $id: State changed to $state")
                        if (state == TransferState.COMPLETED) {
                            isUploadComplete = true
                            Log.d(TAG, "S3 upload completed for key: $s3Key")
                        } else if (state == TransferState.FAILED) {
                            uploadError = Exception("S3 upload failed for key: $s3Key, State: $state")
                            Log.e(TAG, "S3 upload failed for key: $s3Key, State: $state")
                            isUploadComplete = true
                        }
                    }

                    override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                        val percent = (bytesCurrent.toDouble() / bytesTotal * 100).toInt()
                        Log.d(TAG, "Transfer ID $id: Progress $percent%")
                    }

                    override fun onError(id: Int, ex: Exception) {
                        Log.e(TAG, "Error in S3 Transfer ID $id: ${ex.message}", ex)
                        uploadError = ex
                        isUploadComplete = true
                    }
                })

                while (!isUploadComplete) {
                    kotlinx.coroutines.delay(100)
                }

                uploadError?.let { throw it }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file to S3: ${e.message}", e)
                throw e
            }
        }
    }


    /**
     * Queues an API request for later sending using WorkManager.
     * @param type The type of request (e.g., "reading", "image_upload").
     * @param endpoint The API endpoint.
     * @param method The HTTP method.
     * @param body The JSON string body of the request.
     */
    private suspend fun queueRequest(type: String, endpoint: String, method: String, body: String) { // FIX: Added 'type' parameter
        val queuedRequest = QueuedRequest(type = type, endpoint = endpoint, method = method, body = body) // FIX: Pass 'type' to constructor
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
