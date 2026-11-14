import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun requiredLocalProperty(key: String, example: String): String {
    val value = localProperties.getProperty(key)?.trim()
    if (value == null || value.isEmpty()) {
        throw GradleException(
            "Missing '$key' in ${localPropertiesFile.absolutePath}. " +
                "Add it to local.properties, for example:\n$key=$example"
        )
    }
    return value
}

val sdl2Dir = requiredLocalProperty("sdl2.dir", "D:/path/to/SDL2-2.32.8")
val freetypeDir = requiredLocalProperty("freetype.dir", "D:/path/to/freetype-2.15.0")
val engineDir = requiredLocalProperty("engine.dir", "D:/path/to/openbgai/openbgi-sdl")

android {
    namespace = "app.suba"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.suba"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        
        externalNativeBuild {
            cmake {
                arguments.addAll(
                    listOf(
                        "-DSDL2_DIR=$sdl2Dir",
                        "-DFREETYPE_DIR=$freetypeDir",
                        "-DANDROID_PLATFORM=android-28"
                    )
                )
            }
        }
    }

    
    externalNativeBuild {
        cmake {
            path = file(engineDir).resolve("android/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
