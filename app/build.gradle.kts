plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.husmer.hanzigate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.husmer.hanzigate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // The release key lives in CI secrets (see README), never in the repo. Every CI build is signed
    // with it, so new APKs install over the old one without losing progress. Without it (local builds),
    // the standard debug key is used.
    val keystore = System.getenv("HANZIGATE_KEYSTORE")?.let { file(it) }
    val shared = keystore?.let {
        signingConfigs.create("shared") {
            storeFile = it
            storePassword = System.getenv("HANZIGATE_KEYSTORE_PASSWORD")
            keyAlias = "hanzigate"
            keyPassword = System.getenv("HANZIGATE_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            if (shared != null) signingConfig = shared
        }
        release {
            isMinifyEnabled = false
            if (shared != null) signingConfig = shared
        }
    }

    sourceSets {
        getByName("main").assets.srcDirs("../wordlists")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
