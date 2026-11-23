import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// podpis release APK: kľúč je mimo repa (~/.config/digitalizator/android-keystore.properties)
val ksFile = File(System.getProperty("user.home"), ".config/digitalizator/android-keystore.properties")
val ks = Properties()
if (ksFile.exists()) ksFile.inputStream().use { ks.load(it) }

android {
    namespace = "sk.digitalizator.camera"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "sk.digitalizator.camera"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    if (ksFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(ks.getProperty("storeFile"))
                storePassword = ks.getProperty("storePassword")
                keyAlias = ks.getProperty("keyAlias")
                keyPassword = ks.getProperty("keyPassword")
            }
        }
        buildTypes {
            getByName("release") { signingConfig = signingConfigs.getByName("release") }
        }
    }
}

val cameraX = "1.4.1"
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
}
