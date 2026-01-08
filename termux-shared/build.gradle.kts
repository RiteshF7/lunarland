import com.android.build.gradle.LibraryExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

plugins {
    id("com.android.library")
    id("maven-publish")
    id("org.jetbrains.kotlin.android")
}

val markwonVersion: String by project
val androidExtension = extensions.getByType<LibraryExtension>()

android {
    namespace = "com.termux.shared"
    compileSdk = project.property("compileSdkVersion").toString().toInt()
    ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.property("ndkVersion").toString()

    defaultConfig {
        minSdk = project.property("minSdkVersion").toString().toInt()
        targetSdk = project.property("targetSdkVersion").toString().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            ndkBuild { }
        }
        
        ndk {
            // Build for arm64-v8a (and optionally x86_64 for emulator)
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
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
    externalNativeBuild {
        ndkBuild {
            path = file("src/main/cpp/Android.mk")
        }
    }
}

val sourceJar = tasks.register<Jar>("sourceJar") {
    from(androidExtension.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.annotation:annotation:1.3.0")
    implementation("androidx.core:core:1.6.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("com.google.guava:guava:24.1-jre")
    implementation("io.noties.markwon:core:$markwonVersion")
    implementation("io.noties.markwon:ext-strikethrough:$markwonVersion")
    implementation("io.noties.markwon:linkify:$markwonVersion")
    implementation("io.noties.markwon:recycler:$markwonVersion")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    implementation("androidx.window:window:1.0.0-alpha09")

    implementation("commons-io:commons-io:2.5")

    implementation(project(":terminal-view"))

    implementation("com.termux:termux-am-library:v2.0.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
}

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.termux"
                artifactId = "termux-shared"
                version = "0.118.0"
                artifact(sourceJar.get())
            }
        }
    }
}
