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
    namespace = "com.termux"
    compileSdk = project.property("compileSdkVersion").toString().toInt()
    ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.property("ndkVersion").toString()

    defaultConfig {
        minSdk = project.property("minSdkVersion").toString().toInt()
        targetSdk = project.property("targetSdkVersion").toString().toInt()

        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"$packageVariant\"")
        buildConfigField("int", "TERMUX_APP_VERSION_CODE", libraryVersionCode.toString())
        buildConfigField("String", "TERMUX_APP_VERSION_NAME", "\"$libraryVersionName\"")

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

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
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

val downloadBootstraps = tasks.register("downloadBootstraps") {
    doLast {
        when (packageVariant) {
            "apt-android-7" -> {
                val version = "2022.04.28-r5+$packageVariant"
                downloadBootstrap("aarch64", "4a51a7eb209fe82efc24d52e3cccc13165f27377290687cb82038cbd8e948430", version)
                downloadBootstrap("arm", "6459a786acbae50d4c8a36fa1c3de6a4dd2d482572f6d54f73274709bd627325", version)
                downloadBootstrap("i686", "919d212b2f19e08600938db4079e794e947365022dbfd50ac342c50fcedcd7be", version)
                downloadBootstrap("x86_64", "61b02fdc03ea4f5d9da8d8cf018013fdc6659e6da6cbf44e9b24d1c623580b89", version)
            }

            "apt-android-5" -> {
                val version = "2022.04.28-r6+$packageVariant"
                downloadBootstrap("aarch64", "913609d439415c828c5640be1b0561467e539cb1c7080662decaaca2fb4820e7", version)
                downloadBootstrap("arm", "26bfb45304c946170db69108e5eb6e3641aad751406ce106c80df80cad2eccf8", version)
                downloadBootstrap("i686", "46dcfeb5eef67ba765498db9fe4c50dc4690805139aa0dd141a9d8ee0693cd27", version)
                downloadBootstrap("x86_64", "615b590679ee6cd885b7fd2ff9473c845e920f9b422f790bb158c63fe42b8481", version)
            }

            else -> throw GradleException("Unsupported TERMUX_PACKAGE_VARIANT \"$packageVariant\"")
        }
    }
}

tasks.register("versionName") {
    doLast {
        print(libraryVersionName)
    }
}

tasks.named("clean") {
    doLast {
        fileTree(File(projectDir, "src/main/cpp")) {
            include("bootstrap-*.zip")
        }.forEach(File::delete)
    }
}

afterEvaluate {
    androidExtension.libraryVariants.configureEach {
        javaCompileProvider.configure {
            dependsOn(downloadBootstraps)
        }
    }
}

private fun validateVersionName(versionName: String) {
    if (!Pattern.matches("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?\$", versionName)) {
        throw GradleException("The versionName '$versionName' is not a valid version as per semantic version '2.0.0' spec in the format 'major.minor.patch(-prerelease)(+buildmetadata)'. https://semver.org/spec/v2.0.0.html.")
    }
}

private fun downloadBootstrap(arch: String, expectedChecksum: String, version: String) {
    val localPath = "src/main/cpp/bootstrap-$arch.zip"
    val target = File(projectDir, localPath)

    if (target.exists()) {
        val digest = MessageDigest.getInstance("SHA-256")
        target.inputStream().buffered().use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val checksum = digest.digest().toHex()
        if (checksum.equals(expectedChecksum, ignoreCase = true)) {
            return
        } else {
            logger.quiet("Deleting old local file with wrong hash: $localPath: expected: $expectedChecksum, actual: $checksum")
            target.delete()
        }
    }

    val remoteUrl = "https://github.com/termux/termux-packages/releases/download/bootstrap-$version/bootstrap-$arch.zip"
    logger.quiet("Downloading $remoteUrl ...")

    target.parentFile?.mkdirs()
    val connection = URL(remoteUrl).openConnection().apply {
        if (this is HttpURLConnection) {
            instanceFollowRedirects = true
        }
    }

    val digest = MessageDigest.getInstance("SHA-256")
    DigestInputStream(connection.getInputStream(), digest).use { input ->
        target.outputStream().buffered().use { output ->
            input.copyTo(output)
        }
    }

    val checksum = digest.digest().toHex()
    if (!checksum.equals(expectedChecksum, ignoreCase = true)) {
        target.delete()
        throw GradleException("Wrong checksum for $remoteUrl: expected: $expectedChecksum, actual: $checksum")
    }
}

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
}.padStart(64, '0')
