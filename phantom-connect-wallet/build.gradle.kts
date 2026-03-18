import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.konan.target.Family

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

kotlin {
    applyDefaultHierarchyTemplate()

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    val xcf = XCFramework("PhantomConnectWalletKMP")
    targets
        .filterIsInstance<KotlinNativeTarget>()
        .filter { it.konanTarget.family == Family.IOS }
        .forEach {
            it.binaries.framework {
                binaryOption("bundleId", "dev.bmcreations.phantom.connect.wallet")
                baseName = "PhantomConnectWalletKMP"
                export(project(":phantom-connect"))
                xcf.add(this)
            }
        }

    sourceSets {
        commonMain.dependencies {
            api(project(":phantom-connect"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.libsodium)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        // Android unit tests run on the host JVM, which needs the JVM variant of libsodium
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.multiplatform.crypto.libsodium.bindings.jvm)
            }
        }

        androidMain.dependencies {
            implementation(libs.activity.compose)
        }
    }
}

android {
    namespace = "dev.bmcreations.phantom.connect.wallet"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
