// נכתב: 2026-10-29 02:10
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "il.yoel.nehiga"
    compileSdk = 35

    defaultConfig {
        applicationId = "il.yoel.nehiga"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // מפתח חתימה קבוע — כך כל גרסה חדשה מתעדכנת מעל הקודמת בלי למחוק את האפליקציה
    signingConfigs {
        create("fixed") {
            storeFile = file("nehiga.keystore")
            storePassword = "nehiga123"
            keyAlias = "nehiga"
            keyPassword = "nehiga123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
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
    implementation("androidx.core:core-ktx:1.13.1")
}
