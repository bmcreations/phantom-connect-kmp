import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.konan.target.Family

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kmmbridge)
    alias(libs.plugins.maven.publish)
}

group = "dev.bmcreations"
version = project.findProperty("VERSION_NAME")?.toString() ?: "0.0.0-SNAPSHOT"

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

    val xcf = XCFramework()
    targets
        .filterIsInstance<KotlinNativeTarget>()
        .filter { it.konanTarget.family == Family.IOS }
        .forEach {
            it.binaries.framework {
                binaryOption("bundleId", "dev.bmcreations.phantom.connect")
                baseName = "PhantomConnectKMP"
                xcf.add(this)
            }
        }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.libsodium)
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.runtime)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        // Android unit tests run on the host JVM, which needs the JVM variant of libsodium
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.multiplatform.crypto.libsodium.bindings.jvm)
            }
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.security.crypto)
            implementation(libs.browser)
            implementation(libs.activity.compose)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "dev.bmcreations.phantom.connect"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

kmmbridge {
    buildType.set(NativeBuildType.RELEASE)
    spm(
        spmDirectory = "${project.rootDir}/Internal/PhantomConnectSDK",
        swiftToolVersion = "5.9"
    ) {
        iOS { v("16") }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    pom {
        name.set("Phantom Connect SDK")
        description.set("Kotlin Multiplatform SDK for Phantom Connect embedded wallets")
        url.set("https://github.com/bmcreations/phantom-connect-kmp")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("bmcreations")
                name.set("bmcreations")
                url.set("https://github.com/bmcreations")
            }
        }
        scm {
            url.set("https://github.com/bmcreations/phantom-connect-kmp")
            connection.set("scm:git:git://github.com/bmcreations/phantom-connect-kmp.git")
            developerConnection.set("scm:git:ssh://git@github.com/bmcreations/phantom-connect-kmp.git")
        }
    }
}
