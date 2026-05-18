plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    buildFeatures {
        buildConfig = true
    }
    namespace = "com.ckzhang.remotecap.wear"
    compileSdk = 34

    defaultConfig {
        buildConfigField("long", "BUILD_TIME", "System.currentTimeMillis()")
        // MUST MATCH phone app applicationId for Data Layer to work!
        applicationId = "com.ckzhang.remotecap" 
        minSdk = 30
        targetSdk = 33
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
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Wear OS Data Layer API (Watch side)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
}



