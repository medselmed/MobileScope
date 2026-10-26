plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Numéro de build fourni par GitHub Actions : il devient versionCode (1.0.N)
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val githubRepo = System.getenv("GITHUB_REPOSITORY") ?: "OWNER/REPO"
val webClientId = System.getenv("WEB_CLIENT_ID") ?: "REPLACE_ME"

android {
    namespace = "com.medsel.mobilescope"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.medsel.mobilescope"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "mobilescope"
            keyAlias = "mobilescope"
            keyPassword = "mobilescope"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
