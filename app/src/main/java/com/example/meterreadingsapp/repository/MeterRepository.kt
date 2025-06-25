package com.example.meterreadingsapp.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.amazonaws.auth.BasicAWSCredentials // Import for AWS credentials
import com.amazonaws.mobileconnectors.s3.transferutility.TransferListener // For TransferUtility progress
import com.amazonaws.mobileconnectors.s3.transferutility.TransferNetworkLossHandler // For TransferUtility network handling
import com.amazonaws.mobileconnectors.s3.transferutility.TransferState // For TransferUtility progress
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility // For S3 uploads
import com.amazonaws.regions.Region // Import for AWS Region
import com.amazonaws.regions.Regions // Import for AWS Regions enum
import com.amazonaws.services.s3.AmazonS3Client // Import for S3 client
import com.amazonaws.services.s3.model.CannedAccessControlList // For setting public read access (if desired)
import com.example.meterreadingsapp.BuildConfig // Import BuildConfig for AWS keys
import com.example.meterreadingsapp.api.ApiService
import com.example.meterreadingsapp.data.Location
import com.example.meterreadingsapp.data.Meter
import com.example.meterreadingsapp.data.MeterDao
import com.example.meterreadingsapp.data.Reading
import com.example.meterreadingsapp.data.ReadingDao
import com.example.meterreadingsapp.data.LocationDao
import com.example.meterreadingsapp.data.QueuedRequest
import com.example.meterreadingsapp.data.QueuedRequestDao
import com.example.meterreadingsapp.workers.SyncWorker
import com.example.meterreadingsapp.workers.S3UploadWorker // FIX: Import S3UploadWorker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import okhttp3.ResponseBody
import java.io.File // For file operations
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

    // FIX: AWS S3 Client and TransferUtility initialization
    private val s3Client: AmazonS3Client
    private val transferUtility: TransferUtility

    // FIX: Define your S3 bucket name here
    // IMPORTANT: Replace "YOUR_S3_BUCKET_NAME" with your actual S3 bucket name
    private val S3_BUCKET_NAME = "project-documents" // FIX: Updated S3 bucket name

    init {
        // Initialize AWS credentials
        val credentials = BasicAWSCredentials(
            BuildConfig.AWS_ACCESS_KEY_ID,
            BuildConfig.AWS_SECRET_ACCESS_KEY
        )

        // Initialize S3 Client
        s3Client = AmazonS3Client(credentials, Region.getRegion(Regions.fromName(BuildConfig.AWS_REGION)))
        // Optional: Enable network loss handler for TransferUtility for better robustness
        TransferNetworkLossHandler.getInstance(appContext)
        transferUtility = TransferUtility.builder()
            .context(appContext)
            .s3Client(s3Client)
            .defaultBucket(S3_BUCKET_NAME) // Set default bucket name
            .build()

        Log.d(TAG, "AWS S3 Client and TransferUtility initialized for region: ${BuildConfig.AWS_REGION}")
    }

    /**
     * Private helper function to generate a unique ID for a Location, now including house_number and house_number_addition.
     * Ensure all components of the ID are non-null for this composite key.
     */
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

    /**
     * Provides a Flow of all meters from the local database.
     * This Flow will emit new lists of meters whenever the database changes.
     * @return A Flow emitting a list of Meter objects.
     */
    fun getAllMetersFromDb(): Flow<List<Meter>> {
        return meterDao.getAllMeters()
    }

    /**
     * Retrieves a Flow of unique Location objects (addresses) from the locally stored locations.
     * This function now directly fetches from the local LocationDao.
     * @return A Flow emitting a list of unique Location objects.
     */
    fun getUniqueLocations(): Flow<List<Location>> {
        return locationDao.getAllLocations()
    }

    /**
     * Retrieves a Flow of meters associated with a specific location (address, postal code, city, house_number, house_number_addition).
     * @param address The street address to filter by.
     * @param postalCode The postal code to filter by (nullable).
     * @param city The city to filter by (nullable).
     * @param houseNumber The house number to filter by (nullable).
     * @param houseNumberAddition The house number addition to filter by (nullable).
     * @return A Flow emitting a list of Meter objects matching the given address details.
     */
    fun getMetersForLocation(
        address: String,
        postalCode: String?,
        city: String?,
        houseNumber: String?,
        houseNumberAddition: String?
    ): Flow<List<Meter>> {
        return meterDao.getMetersByAddress(address, postalCode, city, houseNumber, houseNumberAddition)
    }

    /**
     * Refreshes all meters from the API and updates the local database.
     * This method clears existing meters and then inserts the newly fetched ones.
     * It runs on the IO dispatcher to prevent blocking the main thread.
     */
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

    /**
     * Helper function to build a user-friendly display name for a location,
     * including street, house number, and addition. Consumer is explicitly excluded.
     */
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

    /**
     * Posts a new meter reading to the API.
     * If offline, it queues the request for later.
     * @param reading The Reading object to be sent to the API.
     * @return A Retrofit Response object with Unit type (204 No Content) or a custom success/failure indicator.
     */
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
     * FIX: Queues an S3 image upload request for later sending using WorkManager.
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

        // Create WorkData to pass arguments to the worker
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

    // FIX: Function to perform the actual S3 upload from a worker
    // This is called by S3UploadWorker.
    suspend fun uploadFileToS3(imageUri: Uri, s3Key: String) {
        withContext(Dispatchers.IO) {
            try {
                // Resolve the URI to a File path
                val file = File(imageUri.path ?: throw IOException("Invalid image URI path"))
                if (!file.exists()) {
                    throw IOException("File does not exist at URI: $imageUri")
                }
                Log.d(TAG, "Starting S3 upload for file: ${file.name} to key: $s3Key")

                // Start the upload using TransferUtility
                val uploadObserver = transferUtility.upload(s3Key, file)

                // Wait for the upload to complete (this will block the coroutine until done)
                // In a real app, you might want more sophisticated progress tracking and error handling
                // directly within the TransferUtility's TransferListener, reporting back via LiveData/Flow.
                var isUploadComplete = false
                var uploadError: Exception? = null

                uploadObserver.setTransferListener(object : TransferListener {
                    override fun onStateChanged(id: Int, state: TransferState) {
                        Log.d(TAG, "Transfer ID $id: State changed to $state")
                        if (state == TransferState.COMPLETED) {
                            isUploadComplete = true
                            Log.d(TAG, "S3 upload completed for key: $s3Key")
                        } else if (state == TransferState.FAILED) {
                            // TransferUtility itself logs the error, but we can capture it here
                            uploadError = Exception("S3 upload failed for key: $s3Key, State: $state")
                            Log.e(TAG, "S3 upload failed for key: $s3Key, State: $state")
                            isUploadComplete = true // Mark as complete to unblock
                        }
                    }

                    override fun onProgressChanged(id: Int, bytesCurrent: Long, bytesTotal: Long) {
                        val percent = (bytesCurrent.toDouble() / bytesTotal * 100).toInt()
                        Log.d(TAG, "Transfer ID $id: Progress $percent%")
                    }

                    override fun onError(id: Int, ex: Exception) {
                        Log.e(TAG, "Error in S3 Transfer ID $id: ${ex.message}", ex)
                        uploadError = ex
                        isUploadComplete = true // Mark as complete to unblock
                    }
                })

                // Await completion in a non-blocking way using suspendCancellableCoroutine if needed for full integration
                // For simplicity here, we're relying on the blocking nature of waiting for the listener to fire.
                // In a WorkManager context, the worker's doWork() will typically run until this completes or throws.
                while (!isUploadComplete) {
                    kotlinx.coroutines.delay(100) // Small delay to prevent busy-waiting
                }

                uploadError?.let { throw it } // Re-throw any upload errors

            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload file to S3: ${e.message}", e)
                throw e // Re-throw the exception to be handled by the worker
            }
        }
    }


    /**
     * Queues an API request for later sending using WorkManager.
     * @param endpoint The API endpoint.
     * @param method The HTTP method.
     * @param body The JSON string body of the request.
     */
    private suspend fun queueRequest(endpoint: String, method: String, body: String) {
        val queuedRequest = QueuedRequest(endpoint = endpoint, method = method, body = body)
        queuedRequestDao.insert(queuedRequest)
        Log.d(TAG, "Request queued locally: ${queuedRequest.id}")

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
