import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()
    // Web spike (design v1.1.1 §18 #10 / companion §6.5): the Kotlin core compiled for the browser,
    // consumed by the React client through jsMain facades. Node is kept for running commonTest.
    js(IR) {
        useEsModules() // Vite loads ES modules natively in dev and prod; UMD only works once bundled
        browser()
        nodejs { testTask { useMocha { timeout = "30s" } } }
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.schedule) // schedule engine (design v1.1 §6.5)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.koin.core)
            implementation(libs.koin.core.viewmodel) // viewModel {} DSL for KMP
            implementation(libs.kermit)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.gitlive.firebase.auth)
            api(libs.gitlive.firebase.firestore)
            api(libs.gitlive.firebase.functions)
            api(libs.gitlive.firebase.config)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // GitLive's Android artifacts declare Firebase SDK deps without versions;
            // the BoM supplies them (must match the BoM GitLive was built against).
            api(project.dependencies.platform(libs.firebase.bom))
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
    }
}

// GitLive's iOS klibs link against the native Firebase frameworks (FirebaseCore, FirebaseAuth, ...)
// which only exist inside the Xcode project via SPM (iosApp/README.md). A Kotlin/Native *test*
// executable cannot resolve them from Gradle alone, so commonTest runs on the JVM through the
// Android unit-test tasks (design §13); the iOS test binary is not linked or run.
tasks.withType<KotlinNativeLink>().configureEach {
    if (binary is TestExecutable) enabled = false
}
tasks.withType<KotlinNativeTest>().configureEach { enabled = false }

android {
    namespace = "app.tripplanner.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
}

// Design v1.1.1 §14: the browser library build is copied into web/vendor/shared (gitignored) so the
// React client imports the Kotlin core from the same commit as the apps and the feed.
val packageForWeb by tasks.registering(Sync::class) {
    dependsOn("jsBrowserProductionLibraryDistribution")
    from(layout.buildDirectory.dir("dist/js/productionLibrary"))
    into(rootProject.layout.projectDirectory.dir("web/vendor/shared"))
}
