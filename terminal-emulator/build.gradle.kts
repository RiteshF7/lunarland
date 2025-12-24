import com.android.build.gradle.LibraryExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

plugins {
    id("com.android.library")
    id("maven-publish")
    id("org.jetbrains.kotlin.android")
}

val androidExtension = extensions.getByType<LibraryExtension>()

android {
    namespace = "com.termux.terminal"
    compileSdk = project.property("compileSdkVersion").toString().toInt()
    ndkVersion = System.getenv("JITPACK_NDK_VERSION") ?: project.property("ndkVersion").toString()

    defaultConfig {
        minSdk = project.property("minSdkVersion").toString().toInt()
        targetSdk = project.property("targetSdkVersion").toString().toInt()

        externalNativeBuild {
            ndkBuild {
                cFlags += listOf(
                    "-std=c11",
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-Os",
                    "-fno-stack-protector",
                    "-Wl,--gc-sections"
                )
            }
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

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

val sourceJar = tasks.register<Jar>("sourceJar") {
    from(androidExtension.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("started", "passed", "skipped", "failed")
    }
}

dependencies {
    implementation("androidx.annotation:annotation:1.3.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.termux"
                artifactId = "terminal-emulator"
                version = "0.118.0"
                artifact(sourceJar.get())
            }
        }
    }
}
