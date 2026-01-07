import org.gradle.api.tasks.Delete

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.11.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

// Repositories are now managed in settings.gradle.kts via dependencyResolutionManagement
// allprojects {
//     repositories {
//         google()
//         mavenCentral()
//         maven(url = "https://jitpack.io")
//     }
// }

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

// Apply bootstrap upload task
apply(from = "gradle/upload-bootstraps.gradle.kts")
