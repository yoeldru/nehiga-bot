// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 21:00 (גרסה 2: עיצוב Material, עדכונים, מספר גרסה מ-GitHub)
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
        // מספר הגרסה מגיע מהבנייה ב-GitHub (100 + מספר הבנייה), כך שכל בנייה חדשה "גבוהה" מהקודמת
        versionCode = (project.findProperty("vCode") as String?)?.toInt() ?: 100
        versionName = (project.findProperty("vName") as String?) ?: "2.0-dev"
    }

    buildFeatures {
        buildConfig = true
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
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
