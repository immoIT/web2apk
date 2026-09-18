import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun prop(name: String, default: String): String =
    project.findProperty(name)?.toString()?.takeIf { it.isNotBlank() } ?: default

android {
    namespace = "com.example.websitetopk"
    compileSdk = 35

    defaultConfig {
        applicationId = prop("PACKAGE_NAME", "com.example.websitetopk")
        minSdk = prop("MIN_SDK", "23").toInt()
        targetSdk = prop("TARGET_SDK", "35").toInt()
        versionCode = prop("VERSION_CODE", "1").toInt()
        versionName = prop("VERSION_NAME", "1.0.0")

        manifestPlaceholders["orientation"] = prop("ORIENTATION", "unspecified")

    buildConfigField("String", "WEB_URL", "\"${prop("WEB_URL", "https://example.com")}\"")
    buildConfigField("String", "APP_NAME", "\"${prop("APP_NAME", "Website To APK")}\"")
    buildConfigField("boolean", "ENABLE_JAVASCRIPT", prop("ENABLE_JAVASCRIPT", "true"))
    buildConfigField("boolean", "ENABLE_DOM_STORAGE", prop("ENABLE_DOM_STORAGE", "true"))
    buildConfigField("boolean", "ENABLE_PULL_TO_REFRESH", prop("ENABLE_PULL_TO_REFRESH", "false"))
    buildConfigField("boolean", "ALLOW_EXTERNAL_LINKS", prop("ALLOW_EXTERNAL_LINKS", "true"))
    buildConfigField("boolean", "ENABLE_DOWNLOADS", prop("ENABLE_DOWNLOADS", "true"))
    buildConfigField("boolean", "ENABLE_FILE_UPLOAD", prop("ENABLE_FILE_UPLOAD", "true"))
    buildConfigField("boolean", "ENABLE_FULLSCREEN_VIDEO", prop("ENABLE_FULLSCREEN_VIDEO", "true"))
    buildConfigField("boolean", "PERMISSION_CAMERA", prop("PERMISSION_CAMERA", "false"))
    buildConfigField("boolean", "PERMISSION_MICROPHONE", prop("PERMISSION_MICROPHONE", "false"))
    buildConfigField("boolean", "PERMISSION_LOCATION", prop("PERMISSION_LOCATION", "false"))
    buildConfigField("boolean", "PERMISSION_NOTIFICATIONS", prop("PERMISSION_NOTIFICATIONS", "false"))
    buildConfigField("boolean", "PERMISSION_STORAGE", prop("PERMISSION_STORAGE", "false"))
    buildConfigField("boolean", "PERMISSION_CONTACTS", prop("PERMISSION_CONTACTS", "false"))
    buildConfigField("boolean", "PERMISSION_PHONE", prop("PERMISSION_PHONE", "false"))
    buildConfigField("boolean", "PERMISSION_CALENDAR", prop("PERMISSION_CALENDAR", "false"))
    buildConfigField("boolean", "PERMISSION_BLUETOOTH", prop("PERMISSION_BLUETOOTH", "false"))
    buildConfigField("boolean", "PERMISSION_VIBRATE", prop("PERMISSION_VIBRATE", "false"))
    buildConfigField("boolean", "PERMISSION_NFC", prop("PERMISSION_NFC", "false"))
    }

    buildFeatures {
        buildConfig = true
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
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
