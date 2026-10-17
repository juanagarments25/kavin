plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.linkcontrol.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linkcontrol.app"
        minSdk = 26          // Android 8.0 — required for foreground-service + MediaProjection flow used here
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Default signaling server. Override per-build or change in Security settings at runtime.
        buildConfigField("String", "DEFAULT_SIGNALING_URL", "\"wss://your-signaling-host.example\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // handy for LAN testing against a local server
            buildConfigField("String", "DEFAULT_SIGNALING_URL", "\"ws://10.0.2.2:8080\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.1")

    // WebRTC prebuilt (Stream's maintained build of Google libwebrtc). Free / Apache-2.0.
    implementation("io.getstream:stream-webrtc-android:1.1.3")

    // QR: generate (ZXing core) + scan (CameraX + MLKit barcode)
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // WebSocket client for signaling
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON handling uses Android's built-in org.json (no extra dependency / compiler plugin).

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
