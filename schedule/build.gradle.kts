import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

/*
 * Schedule engine (design v1.1 §6.5): pure Kotlin, no Firebase or UI dependencies, so the same
 * code computes the calendar on Android, iOS and - through the Node.js library build packaged
 * into firebase/functions - the ICS feed. Golden fixtures in commonTest run on every target.
 */
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        nodejs {
            // First use of the js-joda zone database takes a few seconds under mocha.
            testTask { useMocha { timeout = "30s" } }
        }
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jsMain.dependencies {
            implementation(npm("@js-joda/timezone", "2.22.0")) // named-zone data for kotlinx-datetime on Node
        }
    }
}

// Same policy as :shared - commonTest runs on the JVM (Android unit tests) and JS; the iOS test
// binary is not linked so this module's tests never wait on the Kotlin/Native toolchain.
tasks.withType<KotlinNativeLink>().configureEach { if (binary is TestExecutable) enabled = false }
tasks.withType<KotlinNativeTest>().configureEach { enabled = false }

android {
    namespace = "app.tripplanner.schedule"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}
