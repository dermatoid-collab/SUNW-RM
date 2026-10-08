plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "it.sunw.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.sunw.widget"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // MeteoBlue key from the CI secret METEOBLUE_API_KEY; without it the weather card says so.
        buildConfigField("String", "METEOBLUE_API_KEY", "\"${System.getenv("METEOBLUE_API_KEY") ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key so the release APK is installable as-is.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        // Robolectric UI tests need merged resources and the manifest.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
