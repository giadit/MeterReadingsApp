package com.example.meterreadingsapp.api

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.S3ClientOptions
import com.example.meterreadingsapp.BuildConfig

/**
 * A singleton object to manage and provide an instance of the AmazonS3 client,
 * specifically configured to connect to your self-hosted Minio server.
 * This version uses the correct AWS SDK for Android classes.
 */
object S3Client {

    private var s3Client: AmazonS3? = null
    private const val MINIO_ENDPOINT = "https://minio-nkwcs0s8sws80sg4wwgc0gwg.116.202.104.65.sslip.io"

    /**
     * Creates and returns a configured AmazonS3 client instance.
     * It uses the credentials stored in BuildConfig and sets the custom endpoint for Minio.
     */
    fun getInstance(): AmazonS3 {
        if (s3Client == null) {
            // 1. Set up the credentials using the values from build.gradle.kts
            val credentials = BasicAWSCredentials(
                BuildConfig.AWS_ACCESS_KEY_ID,
                BuildConfig.AWS_SECRET_ACCESS_KEY
            )

            // 2. Create the S3 client directly with the credentials
            val client = AmazonS3Client(credentials)

            // 3. Set the custom endpoint for the Minio server
            client.setEndpoint(MINIO_ENDPOINT)

            // 4. Enable path-style access, which is crucial for Minio to work correctly
            val clientOptions = S3ClientOptions.builder().setPathStyleAccess(true).build()
            client.setS3ClientOptions(clientOptions)

            s3Client = client
        }
        return s3Client!!
    }
}

