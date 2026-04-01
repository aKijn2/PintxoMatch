import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

val localImageBaseUrl = localProperties.getProperty(
    "LOCAL_IMAGE_BASE_URL",
    "https://preserve-immigrants-cakes-york.trycloudflare.com"
)

val localImageApiKey = localProperties.getProperty(
    "LOCAL_IMAGE_API_KEY",
    "pintxomatch-local-dev-key"
)

android {
    namespace = "com.example.pintxomatch"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.pintxomatch"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Image storage backend selector: "cloudinary" or "local".
        buildConfigField("String", "IMAGE_PROVIDER", "\"local\"")
        // Cloudflare tunnel base URL for local image server access from physical devices.
        buildConfigField("String", "LOCAL_IMAGE_BASE_URL", "\"$localImageBaseUrl\"")
        buildConfigField("String", "LOCAL_IMAGE_API_KEY", "\"$localImageApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.animation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation(platform("com.google.firebase:firebase-bom:34.9.0"))
    implementation("com.google.firebase:firebase-firestore") // Base de datos (texto, precios, coordenadas)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-database")
}