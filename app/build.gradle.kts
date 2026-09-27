import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun localProp(key: String): String =
    (localProperties.getProperty(key) ?: "").replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.rmltd.workhourstracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.rmltd.workhourstracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 37
        versionName = "1.3.35"

        // Cloud OAuth client IDs from local.properties (never commit secrets).
        // See app/CLOUD_SYNC.md — keys: DRIVE_CLIENT_ID, DROPBOX_APP_KEY, ONEDRIVE_CLIENT_ID
        buildConfigField("String", "DRIVE_CLIENT_ID", "\"${localProp("DRIVE_CLIENT_ID")}\"")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"${localProp("DROPBOX_APP_KEY")}\"")
        buildConfigField("String", "ONEDRIVE_CLIENT_ID", "\"${localProp("ONEDRIVE_CLIENT_ID")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // EncryptedSharedPreferences for OAuth tokens (1.3.34 cloud sync)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
