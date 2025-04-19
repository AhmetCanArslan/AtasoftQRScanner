plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Apply Google Services plugin only if google-services.json exists
    id("com.google.gms.google-services") apply false
}

// Apply Google Services plugin conditionally after plugin block
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.canceng.atasoftqrscanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.canceng.atasoftqrscanner"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Add freeCompilerArgs to enable experimental features if needed in the future
        freeCompilerArgs = listOf("-Xuse-experimental=kotlin.ExperimentalStdlibApi")
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Create a variable to check if we have Firebase configuration
val hasGoogleServices = file("google-services.json").exists()

dependencies {
    // AndroidX libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    // ML Kit
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Firebase dependencies based on whether google-services.json exists
    if (hasGoogleServices) {
        // Firebase
        implementation(platform("com.google.firebase:firebase-bom:32.5.0"))
        implementation("com.google.firebase:firebase-firestore")
        implementation("com.google.firebase:firebase-analytics")
    } else {
        // Add stub implementations for Firebase if needed
        implementation("org.mockito:mockito-core:5.8.0")
    }

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
