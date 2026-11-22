plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chessbuddy"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chessbuddy"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.github.bhlangonijr:chesslib:1.3.4")
}
