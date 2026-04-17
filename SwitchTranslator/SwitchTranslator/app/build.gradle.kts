plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vibe.switchtranslator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vibe.switchtranslator"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
            pickFirsts += "**/libjpeg.so"
            pickFirsts += "**/libpng16.so"
            pickFirsts += "**/libtesseract.so"
            pickFirsts += "**/libtess.so"
            pickFirsts += "**/libleptonica.so"
            pickFirsts += "**/liblept.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
    }
}

configurations.all {
    resolutionStrategy {
        force(
            "com.google.mlkit:translate:17.0.3",
            "com.google.mlkit:common:18.11.0",
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    val cameraX = "1.3.1"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")

    implementation("com.rmtheis:tess-two:9.1.0")
    // 17.0.3: Google Maven; nltranslate siniflari derleme classpath'inde tutarli
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:common:18.11.0")
}
