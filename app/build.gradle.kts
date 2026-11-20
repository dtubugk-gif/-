plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fontmaker.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fontmaker.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        // A fixed, non-secret key kept in the repo so every build can install over the last one.
        create("shared") {
            storeFile = rootProject.file("signing/fontmaker.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
}

tasks.withType<Test>().configureEach {
    systemProperty("ttfOut", layout.buildDirectory.file("test-font.ttf").get().asFile.absolutePath)
    systemProperty("screensOut", layout.buildDirectory.dir("screens").get().asFile.absolutePath)
}
