plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose.compiler)
}

android {
    namespace = "dev.bmcreations.phantom"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.bmcreations.phantom.connect.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    flavorDimensions += "server"
    productFlavors {
        create("mock") {
            dimension = "server"
            buildConfigField("String", "PHANTOM_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "PHANTOM_LOGIN_BASE_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "PHANTOM_APP_ID", "\"test-app-id\"")
        }
        create("production") {
            dimension = "server"
            buildConfigField("String", "PHANTOM_BASE_URL", "\"https://api.phantom.app\"")
            buildConfigField("String", "PHANTOM_LOGIN_BASE_URL", "\"https://connect.phantom.app\"")
            buildConfigField("String", "PHANTOM_APP_ID", "\"f2f3406a-0ecf-4a20-96e4-18293772da65\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation(project(":phantom-connect"))
    implementation(project(":phantom-connect-wallet"))
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.material.icons)
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.ui.tooling)
}
