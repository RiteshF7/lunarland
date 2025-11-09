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

val androidExtension = extensions.getByType<LibraryExtension>()

android {
    namespace = "com.termux.view"
    compileSdk = project.property("compileSdkVersion").toString().toInt()

    defaultConfig {
        minSdk = project.property("minSdkVersion").toString().toInt()
        targetSdk = project.property("targetSdkVersion").toString().toInt()
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

val sourceJar = tasks.register<Jar>("sourceJar") {
    from(androidExtension.sourceSets["main"].java.srcDirs)
    archiveClassifier.set("sources")
}

dependencies {
    implementation("androidx.annotation:annotation:1.3.0")
    api(project(":terminal-emulator"))
    testImplementation("junit:junit:4.13.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
}

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.termux"
                artifactId = "terminal-view"
                version = "0.118.0"
                artifact(sourceJar.get())
            }
        }
    }
}
