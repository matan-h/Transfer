plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    kotlin("plugin.serialization") version "1.8.0"
}

android {
    namespace = "com.matanh.transfer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.matanh.transfer"
        minSdk = 29
        targetSdk = 35
        versionCode = 601
        versionName = "0.6.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true // make it possible to import BuildConfig for VERSION_NAME
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/transfer-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

        }
    }

    //  metadata encrypted with a public key belonging to Google. see  https://android.izzysoft.de/articles/named/iod-scan-apkchecks#blobs
    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }

}

dependencies {
    implementation(libs.ktor.serialization.kotlinx.json.jvm) // For JSON
    implementation(libs.ktor.serialization.kotlinx.json) // important for kt "Serializable"

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.preference.ktx)
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.cio.jvm)

    // Ktor Features
    implementation(libs.ktor.server.content.negotiation.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages.jvm)
    implementation(libs.ktor.server.auth.jvm) // For Basic Auth
    implementation(libs.ktor.server.cors.jvm)
    implementation(libs.ktor.server.call.logging.jvm)
    implementation(libs.timber)
    implementation(libs.androidx.activity)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation (libs.androidx.runner)
    androidTestImplementation (libs.androidx.rules)

    // Espresso
    androidTestImplementation (libs.androidx.espresso.contrib)

    // UI Automator
    androidTestImplementation (libs.androidx.uiautomator)

    // OkHttp for making network requests in the test
    androidTestImplementation (libs.okhttp)

    androidTestImplementation (libs.awaitility.kotlin)

    // new added libraries
    implementation(libs.mmkv)
    implementation(libs.glide)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.qr.generator)

}