import com.android.build.gradle.LibraryExtension
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.configureEach
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.regex.Pattern

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val markwonVersion: String by project

val packageVariant = System.getenv("TERMUX_PACKAGE_VARIANT") ?: "apt-android-7"
val libraryVersionCode = 118
val libraryVersionName = System.getenv("TERMUX_APP_VERSION_NAME").takeUnless { it.isNullOrEmpty() } ?: "0.118.0"
validateVersionName(libraryVersionName)
val androidExtension = extensions.getByType<LibraryExtension>()

android {
    namespace = "com.termux.core"
    compileSdk = project.property("compileSdkVersion").toString().toInt()
    ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.property("ndkVersion").toString()

    defaultConfig {
        minSdk = project.property("minSdkVersion").toString().toInt()
        targetSdk = project.property("targetSdkVersion").toString().toInt()

        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"$packageVariant\"")
        buildConfigField("int", "TERMUX_APP_VERSION_CODE", libraryVersionCode.toString())
        buildConfigField("String", "TERMUX_APP_VERSION_NAME", "\"$libraryVersionName\"")

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.termux"

        consumerProguardFiles("proguard-rules.pro")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        disable += setOf("ProtectedPermissions")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.3.0")
    implementation("androidx.core:core:1.6.0")
    implementation("androidx.drawerlayout:drawerlayout:1.1.1")
    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.viewpager:viewpager:1.0.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.google.guava:guava:24.1-jre")
    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:ext-strikethrough:$markwonVersion")
    implementation("io.noties.markwon:linkify:$markwonVersion")
    implementation("io.noties.markwon:recycler:$markwonVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")

    implementation(project(":terminal-view"))
    api(project(":termux-shared"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.10")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}

tasks.register("versionName") {
    doLast {
        print(libraryVersionName)
    }
}

fun validateVersionName(versionName: String) {
    if (!Pattern.matches("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?\$", versionName)) {
        throw GradleException("The versionName '$versionName' is not a valid version as per semantic version '2.0.0' spec in the format 'major.minor.patch(-prerelease)(+buildmetadata)'. https://semver.org/spec/v2.0.0.html.")
    }
}

// All bootstrap binaries are now hosted remotely; no native bootstrap library is built here.
