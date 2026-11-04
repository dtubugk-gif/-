plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.myvpn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.myvpn.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing comes from environment variables when they are set (e.g. in CI);
    // otherwise the release build is signed with the debug key so it can be sideloaded.
    val releaseKeystore = System.getenv("MYVPN_KEYSTORE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("MYVPN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MYVPN_KEY_ALIAS")
                keyPassword = System.getenv("MYVPN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Shrinking is off: the WireGuard native library calls into Java classes by name.
            isMinifyEnabled = false
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("com.google.android.material:material:1.12.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
