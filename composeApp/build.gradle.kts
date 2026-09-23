import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization) // type-safe navigation routes
    alias(libs.plugins.googleServices) apply false
}

// Local secrets: environment first (CI), then local.properties (gitignored), else empty.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String): String = System.getenv(name) ?: localProperties.getProperty(name) ?: ""

// google-services.json is gitignored (README). Apply the plugin only when it is present so a
// clean checkout still builds (Phase 0 exit criterion E1); Firebase init then needs the file.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
} else {
    logger.warn("composeApp: google-services.json not found - Google Services plugin skipped")
}

kotlin {
    androidTarget()
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.serialization.json) // @Serializable routes
            implementation(libs.kotlinx.datetime)
            implementation(libs.reorderable)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.maps.compose)
            implementation(libs.play.services.maps)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
        }
    }
}

android {
    namespace = "app.tripplanner"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.tripplanner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Injected into AndroidManifest; set MAPS_API_KEY in local.properties or the CI env.
        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY")
        // Firebase Hosting domain that serves /join/{code} + .well-known/assetlinks.json (design §8.2).
        // Defaults to the dev project's site; prod flavor / CI overrides via APP_LINK_HOST.
        manifestPlaceholders["APP_LINK_HOST"] = System.getenv("APP_LINK_HOST") ?: "tripplanner-dev-fe0a4.web.app"
    }
    buildTypes { release { isMinifyEnabled = false } }
}
