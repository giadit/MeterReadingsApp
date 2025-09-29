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
            // Define your API key for release builds (still keeping this for main API calls)
            buildConfigField("String", "SUPABASE_API_KEY", "\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc1NjQ1ODY2MCwiZXhwIjo0OTEyMTMyMjYwLCJyb2xlIjoiYW5vbiJ9.yIY7ONDFIdlRFwa2Q-ksaGbTkB7z2iIPi7F-_FHKJKQ\"")
            // RE-ADD: AWS Access Key ID for authentication (will be used in headers via Retrofit)
            buildConfigField("String", "AWS_ACCESS_KEY_ID", "\"186ae8da1a085f58821956c34a50357c\"") // Replace with actual Access Key ID
            // RE-ADD: AWS Secret Access Key (might be needed for some manual signing, though typically not put directly in header)
            buildConfigField("String", "AWS_SECRET_ACCESS_KEY", "\"57131e3b6293a82c21814191b6d69ff515193c79d95d8d95c8b919c188ceea96\"") // Replace with actual Secret Access Key
            // RE-ADD: AWS Region (might be needed for some manual signing)
            buildConfigField("String", "AWS_REGION", "\"eu-central-1\"") // Replace with your S3 bucket region
            // FIX: Add AWS Service Name
            buildConfigField("String", "AWS_SERVICE_NAME", "\"s3\"")
        }
        debug {
            isMinifyEnabled = false
            // Define your API key for debug builds (still keeping this for main API calls)
            buildConfigField("String", "SUPABASE_API_KEY", "\"eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc1NjQ1ODY2MCwiZXhwIjo0OTEyMTMyMjYwLCJyb2xlIjoiYW5vbiJ9.yIY7ONDFIdlRFwa2Q-ksaGbTkB7z2iIPi7F-_FHKJKQ\"")
            // RE-ADD: AWS Access Key ID for authentication (will be used in headers via Retrofit)
            buildConfigField("String", "AWS_ACCESS_KEY_ID", "\"186ae8da1a085f58821956c34a50357c\"") // Replace with actual Access Key ID
            // RE-ADD: AWS Secret Access Key
            buildConfigField("String", "AWS_SECRET_ACCESS_KEY", "\"57131e3b6293a82c21814191b6d69ff515193c79d95d8d95c8b919c188ceea96\"") // Replace with actual Secret Access Key
            // RE-ADD: AWS Region
            buildConfigField("String", "AWS_REGION", "\"eu-central-1\"") // Replace with your S3 bucket region
            // FIX: Add AWS Service Name
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

    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.5.1")

    // REMOVED: AWS Mobile SDK for S3
    // implementation("com.amazonaws:aws-android-sdk-s3:2.62.0")
    // implementation("com.amazonaws:aws-android-sdk-core:2.62.0")
}
