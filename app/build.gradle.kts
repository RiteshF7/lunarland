plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.termux"
        manifestPlaceholders["TERMUX_APP_NAME"] = "Termux"
        manifestPlaceholders["TERMUX_API_APP_NAME"] = "Termux:API"
        manifestPlaceholders["TERMUX_BOOT_APP_NAME"] = "Termux:Boot"
        manifestPlaceholders["TERMUX_FLOAT_APP_NAME"] = "Termux:Float"
        manifestPlaceholders["TERMUX_STYLING_APP_NAME"] = "Termux:Styling"
        manifestPlaceholders["TERMUX_TASKER_APP_NAME"] = "Termux:Tasker"
        manifestPlaceholders["TERMUX_WIDGET_APP_NAME"] = "Termux:Widget"
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

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.core:core:1.6.0")
    implementation(project(":termux-core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}
