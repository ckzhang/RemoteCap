plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ckzhang.remotecap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ckzhang.remotecap"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    
    // Wear OS Data Layer API (Phone side)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")

    // Google Play Billing Library
    implementation("com.android.billingclient:billing-ktx:6.1.0")
}