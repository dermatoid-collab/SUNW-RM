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

    // A fixed signing key (CI secrets SIGNING_KEYSTORE_BASE64 + SIGNING_PASSWORD, decoded by the
    // workflow into SIGNING_STORE_FILE) lets each new APK install over the previous one, keeping
    // the app's settings. Without it, builds fall back to the per-machine debug key.
    val signingStore = System.getenv("SIGNING_STORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (signingStore != null) {
            create("stable") {
                storeFile = signingStore
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "sunw"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }
    val appSigning = if (signingStore != null) signingConfigs.getByName("stable") else signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = appSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = appSigning
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
