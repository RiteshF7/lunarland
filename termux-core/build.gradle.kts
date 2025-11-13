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
                prepareBootstrap("aarch64", "ee87171b8ec44a6d47c90b24eb6215f95be243a11671040221f7291bf0b062d1", version)
                prepareBootstrap("arm", "1fa5d45d183f744a79cd994753131dd097f8bf648d6e17ef5eb72892aa23fc0d", version)
                prepareBootstrap("i686", "83b801d0c07959af89f4820aa0db82214b046f08b8cab1020015943c8c251bf7", version)
                prepareBootstrap("x86_64", "0b54d3c12f6e1515b5a39d983d0609eafe1369f97d0ae776dbd6cecbc661a1cf", version)
            }

            "apt-android-5" -> {
                val version = "2022.04.28-r6+$packageVariant"
                prepareBootstrap("aarch64", "ee87171b8ec44a6d47c90b24eb6215f95be243a11671040221f7291bf0b062d1", version)
                prepareBootstrap("arm", "1fa5d45d183f744a79cd994753131dd097f8bf648d6e17ef5eb72892aa23fc0d", version)
                prepareBootstrap("i686", "83b801d0c07959af89f4820aa0db82214b046f08b8cab1020015943c8c251bf7", version)
                prepareBootstrap("x86_64", "0b54d3c12f6e1515b5a39d983d0609eafe1369f97d0ae776dbd6cecbc661a1cf", version)
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

private fun prepareBootstrap(arch: String, expectedChecksum: String, version: String) {
    if (copyLocalBootstrap(arch)) {
        return
    }
    downloadBootstrap(arch, expectedChecksum, version)
}

private fun copyLocalBootstrap(arch: String): Boolean {
    val localDir = File(projectDir, "../app/src/main/assets/bootstrap")
    val localFile = File(localDir, "bootstrap-$arch.zip")
    if (!localFile.exists()) {
        return false
    }

    val target = File(projectDir, "src/main/cpp/bootstrap-$arch.zip")
    target.parentFile?.mkdirs()

    if (!target.exists() || localFile.length() != target.length() || localFile.lastModified() > target.lastModified()) {
        localFile.copyTo(target, overwrite = true)
        logger.quiet("Using locally generated bootstrap archive for $arch from ${localFile.absolutePath}")
    } else {
        logger.quiet("Keeping existing bootstrap archive for $arch at ${target.absolutePath}")
    }

    return true
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
