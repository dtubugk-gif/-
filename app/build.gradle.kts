plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.paparazzi)
}

android {
    namespace = "io.github.dtubugk.island"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.dtubugk.island"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Sideload-only app: one committed key so every build (local or CI) can update the
    // installed app in place. Generate a private key before ever publishing to a store.
    signingConfigs {
        create("island") {
            storeFile = rootProject.file("signing/island.keystore")
            storePassword = "islandkey"
            keyAlias = "island"
            keyPassword = "islandkey"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("island")
        }
        debug {
            signingConfig = signingConfigs.getByName("island")
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
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.dynamicanimation)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // Optional local mockup background for IslandSnapshotTest.onUserScreenshot.
    systemProperty("island.mockupBackground", providers.gradleProperty("mockupBackground").getOrElse(""))
}
