import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun readGoogleApiKeyFromLocalProperties(): String? {
    val localPropsFile = File(project.rootDir, "local.properties")
    if (localPropsFile.exists()) {
        val props = Properties()
        localPropsFile.inputStream().use { props.load(it) }
        return props.getProperty("GOOGLE_API_KEY")?.takeIf { it.isNotBlank() }
    }
    return null
}

android {
    namespace = "com.termux"
    compileSdk = project.property("compileSdkVersion").toString().toInt()

    defaultConfig {
        applicationId = "com.termux"
        minSdk = project.property("minSdkVersion").toString().toInt()
        targetSdk = project.property("targetSdkVersion").toString().toInt()
        versionCode = 118
        versionName = "0.118.0"
        
        // Read GOOGLE_API_KEY from local.properties
        val googleApiKey = readGoogleApiKeyFromLocalProperties() ?: ""
        buildConfigField("String", "GOOGLE_API_KEY", "\"$googleApiKey\"")

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.termux"
        manifestPlaceholders["TERMUX_APP_NAME"] = "Termux"
        manifestPlaceholders["TERMUX_API_APP_NAME"] = "Termux:API"
        manifestPlaceholders["TERMUX_BOOT_APP_NAME"] = "Termux:Boot"
        manifestPlaceholders["TERMUX_FLOAT_APP_NAME"] = "Termux:Float"
        manifestPlaceholders["TERMUX_STYLING_APP_NAME"] = "Termux:Styling"
        manifestPlaceholders["TERMUX_TASKER_APP_NAME"] = "Termux:Tasker"
        manifestPlaceholders["TERMUX_WIDGET_APP_NAME"] = "Termux:Widget"
        // No ABI filter here so the APK supports all configured ABIs (arm64, arm, x86, x86_64).
        
        // For development builds, only package native libraries for the emulator/device ABI
        // to keep APK size smaller. Adjust the ABI below if you test on a different arch.
        //ndk {
        //    abiFilters += listOf("x86_64")
        //}
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("testkey_untrusted.jks")
            keyAlias = "alias"
            storePassword = "xrj45yWGLbsO7W0v"
            keyPassword = "xrj45yWGLbsO7W0v"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    aaptOptions {
        // Don't try to recompress already-compressed bootstrap zips; this saves memory during :compressDebugAssets.
        noCompress("zip")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.core:core:1.6.0")
    implementation("androidx.lifecycle:lifecycle-runtime:2.5.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.1")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    implementation(project(":termux-core"))
    implementation(project(":termux-shared"))
    implementation(project(":terminal-emulator"))
    implementation(project(":lunar-ui"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Compose dependencies for lunar-ui
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}

configurations.all {
    exclude(group = "com.google.guava", module = "listenablefuture")
}
