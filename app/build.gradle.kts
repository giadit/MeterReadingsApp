plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.meterreadingsapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.meterreadingsapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "SUPABASE_API_KEY", "\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc1NjQ1ODY2MCwiZXhwIjo0OTEyMTMyMjYwLCJyb2xlIjoiYW5vbiJ9.yIY7ONDFIdlRFwa2Q-ksaGbTkB7z2iIPi7F-_FHKJKQ\"")
            // UPDATED with real credentials
            buildConfigField("String", "AWS_ACCESS_KEY_ID", "\"YGGegwAD6rGrqMka\"")
            buildConfigField("String", "AWS_SECRET_ACCESS_KEY", "\"Sij7GyLLXQMPBnMzf4Jqp3MavnYCgUs6\"")
            buildConfigField("String", "AWS_REGION", "\"eu-central-1\"") // Region can be a placeholder
            buildConfigField("String", "AWS_SERVICE_NAME", "\"s3\"")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "SUPABASE_API_KEY", "\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc1NjQ1ODY2MCwiZXhwIjo0OTEyMTMyMjYwLCJyb2xlIjoiYW5vbiJ9.yIY7ONDFIdlRFwa2Q-ksaGbTkB7z2iIPi7F-_FHKJKQ\"")
            // UPDATED with real credentials
            buildConfigField("String", "AWS_ACCESS_KEY_ID", "\"YGGegwAD6rGrqMka\"")
            buildConfigField("String", "AWS_SECRET_ACCESS_KEY", "\"Sij7GyLLXQMPBnMzf4Jqp3MavnYCgUs6\"")
            buildConfigField("String", "AWS_REGION", "\"eu-central-1\"") // Region can be a placeholder
            buildConfigField("String", "AWS_SERVICE_NAME", "\"s3\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }
    // ADDED: packagingOptions to resolve conflicts from AWS SDK
    packagingOptions {
        pickFirst("META-INF/INDEX.LIST")
        pickFirst("META-INF/io.netty.versions.properties")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Retrofit for API communication
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")

    // Room for local database
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Kotlin Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Lifecycle components (ViewModel and LiveData)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    ksp("androidx.lifecycle:lifecycle-compiler:2.7.0")

    // Optional: for better logging of network requests
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // For RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    // UI Components
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

    // WorkManager for background processing
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ADDED BACK: AWS SDK for S3, required for Minio integration
    implementation("com.amazonaws:aws-android-sdk-s3:2.62.0")
    implementation("com.amazonaws:aws-android-sdk-core:2.62.0")
}

