plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dtubugk.shakelight"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dtubugk.shakelight"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.2.1"
        // Only ship the languages the app is translated to (drops unused library translations).
        resourceConfigurations += listOf("en", "iw")
    }

    // One shared key so every build (local or GitHub Actions) installs as an update
    // over the previous one. Replace with a private key before publishing to Google Play.
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/shakelight.jks")
            storePassword = "shakelight"
            keyAlias = "shakelight"
            keyPassword = "shakelight"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
