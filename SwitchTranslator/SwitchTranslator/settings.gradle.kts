pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "8.2.2"
        id("org.jetbrains.kotlin.android") version "1.9.22"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // ML Kit: Maven Central'daki metadata eksik/yanlis olunca Kotlin nltranslate gormez; sadece Google Maven kullan.
        exclusiveContent {
            forRepository {
                google()
            }
            filter {
                includeGroupByRegex("com\\.google\\.mlkit.*")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "SwitchTranslator"
include(":app")
