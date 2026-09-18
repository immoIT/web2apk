plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun prop(name: String, default: String): String =
    project.findProperty(name)?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: default

fun booleanProp(name: String, default: Boolean): String {
    val raw = project.findProperty(name)?.toString()?.trim()?.lowercase()
    return when (raw) {
        null, "" -> default.toString()
        "true", "1", "yes", "y", "on" -> "true"
        "false", "0", "no", "n", "off" -> "false"
        else -> error("Invalid boolean value for $name: '$raw'. Use true/false, yes/no, on/off, or 1/0.")
    }
}

fun javaStringLiteral(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> append(ch)
        }
    }
    append('"')
}

val applicationIdValue = prop("PACKAGE_NAME", "com.example.websitetopk").also {
    require(Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+").matches(it)) {
        "Invalid PACKAGE_NAME '$it'. Use a Java-style application id such as com.example.myapp."
    }
}

val minSdkValue = prop("MIN_SDK", "24").toIntOrNull()
    ?: error("MIN_SDK must be an integer")
val targetSdkValue = prop("TARGET_SDK", "35").toIntOrNull()
    ?: error("TARGET_SDK must be an integer")
val versionCodeValue = prop("VERSION_CODE", "1").toIntOrNull()
    ?: error("VERSION_CODE must be an integer")
require(minSdkValue >= 21) { "MIN_SDK must be at least 21" }
require(targetSdkValue >= minSdkValue) { "TARGET_SDK must be >= MIN_SDK" }
require(versionCodeValue > 0) { "VERSION_CODE must be > 0" }

val orientationValue = prop("ORIENTATION", "unspecified").lowercase()
val validOrientations = setOf(
    "unspecified", "behind", "landscape", "portrait", "reverse_landscape",
    "reverse_portrait", "sensor_landscape", "sensor_portrait", "user_landscape",
    "user_portrait", "full_sensor", "locked", "nosensor", "user"
)
require(orientationValue in validOrientations) {
    "Invalid ORIENTATION '$orientationValue'. Allowed values: ${validOrientations.joinToString()}."
}

android {
    namespace = "com.example.websitetopk"
    compileSdk = 35

    defaultConfig {
        applicationId = applicationIdValue
        minSdk = minSdkValue
        targetSdk = targetSdkValue
        versionCode = versionCodeValue
        versionName = prop("VERSION_NAME", "1.0.0")

        manifestPlaceholders["orientation"] = orientationValue

        buildConfigField("String", "WEB_URL", javaStringLiteral(prop("WEB_URL", "https://example.com")))
        buildConfigField("String", "APP_NAME", javaStringLiteral(prop("APP_NAME", "Website To APK")))
        buildConfigField("boolean", "ENABLE_JAVASCRIPT", booleanProp("ENABLE_JAVASCRIPT", true))
        buildConfigField("boolean", "ENABLE_DOM_STORAGE", booleanProp("ENABLE_DOM_STORAGE", true))
        buildConfigField("boolean", "ENABLE_PULL_TO_REFRESH", booleanProp("ENABLE_PULL_TO_REFRESH", true))
        buildConfigField("boolean", "ALLOW_EXTERNAL_LINKS", booleanProp("ALLOW_EXTERNAL_LINKS", true))
        buildConfigField("boolean", "ENABLE_DOWNLOADS", booleanProp("ENABLE_DOWNLOADS", true))
        buildConfigField("boolean", "ENABLE_FILE_UPLOAD", booleanProp("ENABLE_FILE_UPLOAD", true))
        buildConfigField("boolean", "ENABLE_FULLSCREEN_VIDEO", booleanProp("ENABLE_FULLSCREEN_VIDEO", true))
        buildConfigField("boolean", "ENABLE_LOGGING", booleanProp("ENABLE_LOGGING", true))
        buildConfigField("boolean", "PERMISSION_CAMERA", booleanProp("PERMISSION_CAMERA", false))
        buildConfigField("boolean", "PERMISSION_MICROPHONE", booleanProp("PERMISSION_MICROPHONE", false))
        buildConfigField("boolean", "PERMISSION_LOCATION", booleanProp("PERMISSION_LOCATION", false))
        buildConfigField("boolean", "PERMISSION_NOTIFICATIONS", booleanProp("PERMISSION_NOTIFICATIONS", false))
        buildConfigField("boolean", "PERMISSION_STORAGE", booleanProp("PERMISSION_STORAGE", false))
        buildConfigField("boolean", "PERMISSION_CONTACTS", booleanProp("PERMISSION_CONTACTS", false))
        buildConfigField("boolean", "PERMISSION_PHONE", booleanProp("PERMISSION_PHONE", false))
        buildConfigField("boolean", "PERMISSION_CALENDAR", booleanProp("PERMISSION_CALENDAR", false))
        buildConfigField("boolean", "PERMISSION_BLUETOOTH", booleanProp("PERMISSION_BLUETOOTH", false))
        buildConfigField("boolean", "PERMISSION_VIBRATE", booleanProp("PERMISSION_VIBRATE", true))
        buildConfigField("boolean", "PERMISSION_NFC", booleanProp("PERMISSION_NFC", false))
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
