import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val defaultsFile = rootProject.file("config/defaults.properties")
val defaults = Properties().apply {
    if (defaultsFile.isFile) {
        defaultsFile.inputStream().use { input -> load(input) }
    }
}

fun prop(name: String, default: String): String =
    project.findProperty(name)?.toString()?.trim()?.takeIf { value -> value.isNotEmpty() }
        ?: defaults.getProperty(name)?.trim()?.takeIf { value -> value.isNotEmpty() }
        ?: default

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

// Files placed in `custom/` are optional and are intended for manual customization.
// They are generated into Android's build directories so the source tree stays clean.
val customDir = rootProject.file("custom")
val customScriptsDir = customDir.resolve("scripts")
val generatedCustomResDir = layout.buildDirectory.dir("generated/web2apk/res")
val generatedCustomAssetsDir = layout.buildDirectory.dir("generated/web2apk/assets")

val syncWeb2ApkCustomResources = tasks.register("syncWeb2ApkCustomResources") {
    outputs.dirs(generatedCustomResDir, generatedCustomAssetsDir)

    doLast {
        val generatedRes = generatedCustomResDir.get().asFile
        val generatedAssets = generatedCustomAssetsDir.get().asFile

        project.delete(generatedRes, generatedAssets)
        generatedRes.mkdirs()
        generatedAssets.mkdirs()

        // Optional launcher icon:
        // custom/icon.png (or .jpg/.jpeg/.webp) -> @drawable/web2apk_icon
        val icon = listOf("icon.png", "icon.webp", "icon.jpg", "icon.jpeg")
            .map(customDir::resolve)
            .firstOrNull { it.isFile }

        val drawableDir = generatedRes.resolve("drawable").apply { mkdirs() }
        if (icon != null) {
            project.copy {
                from(icon)
                into(drawableDir)
                rename { "web2apk_icon.${icon.extension.lowercase()}" }
            }
        } else {
            drawableDir.resolve("web2apk_icon.xml").writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <vector xmlns:android="http://schemas.android.com/apk/res/android"
                    android:width="108dp"
                    android:height="108dp"
                    android:viewportWidth="108"
                    android:viewportHeight="108">
                    <path
                        android:fillColor="#FFFFFF"
                        android:pathData="M0,0h108v108h-108z" />
                    <path
                        android:fillColor="#3F51B5"
                        android:pathData="M24,20h60c2.2,0 4,1.8 4,4v60c0,2.2 -1.8,4 -4,4h-60c-2.2,0 -4,-1.8 -4,-4v-60c0,-2.2 1.8,-4 4,-4z" />
                    <path
                        android:fillColor="#FFFFFF"
                        android:pathData="M34,36h40v6h-40zM34,51h40v6h-40zM34,66h26v6h-26z" />
                </vector>
            """.trimIndent())
        }

        // Optional WebView JavaScript files. Every *.js file is combined into
        // one asset and injected after the page finishes loading.
        if (customScriptsDir.isDirectory) {
            val scripts = customScriptsDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("js", ignoreCase = true) }
                .sortedBy { it.relativeTo(customScriptsDir).path }
                .toList()

            if (scripts.isNotEmpty()) {
                generatedAssets.resolve("web2apk-custom.js").writeText(
                    scripts.joinToString("\n\n") { file ->
                        "// --- web2apk/custom/scripts/${file.relativeTo(customScriptsDir).path} ---\n" +
                            file.readText()
                    }
                )
            }
        }
    }
}

android.sourceSets.getByName("main").res.srcDir(generatedCustomResDir)
android.sourceSets.getByName("main").assets.srcDir(generatedCustomAssetsDir)

tasks.named("preBuild").configure {
    dependsOn(syncWeb2ApkCustomResources)
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
        val appNameValue = prop("APP_NAME", "Website To APK")
        buildConfigField("String", "APP_NAME", javaStringLiteral(appNameValue))
        manifestPlaceholders["appName"] = appNameValue
        buildConfigField("boolean", "ENABLE_JAVASCRIPT", booleanProp("ENABLE_JAVASCRIPT", true))
        buildConfigField("boolean", "ENABLE_DOM_STORAGE", booleanProp("ENABLE_DOM_STORAGE", true))
        buildConfigField("boolean", "ENABLE_PULL_TO_REFRESH", booleanProp("ENABLE_PULL_TO_REFRESH", true))
        buildConfigField("boolean", "ALLOW_EXTERNAL_LINKS", booleanProp("ALLOW_EXTERNAL_LINKS", true))
        buildConfigField("boolean", "ENABLE_DOWNLOADS", booleanProp("ENABLE_DOWNLOADS", true))
        buildConfigField("boolean", "ENABLE_FILE_UPLOAD", booleanProp("ENABLE_FILE_UPLOAD", true))
        buildConfigField("boolean", "ENABLE_FULLSCREEN_VIDEO", booleanProp("ENABLE_FULLSCREEN_VIDEO", true))
        buildConfigField("boolean", "ENABLE_FULLSCREEN", booleanProp("ENABLE_FULLSCREEN", true))
        buildConfigField("boolean", "ENABLE_CUSTOM_SCRIPTS", booleanProp("ENABLE_CUSTOM_SCRIPTS", true))
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
