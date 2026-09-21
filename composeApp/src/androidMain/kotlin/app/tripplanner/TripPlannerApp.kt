package app.tripplanner

import android.app.Application
import android.content.pm.PackageManager
import app.tripplanner.auth.CurrentActivity
import app.tripplanner.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.android.ext.android.get
import org.koin.core.context.startKoin

/**
 * Process-wide startup (design §6.2, §6.4). Firebase itself is initialised by the
 * google-services plugin's FirebaseInitProvider from `google-services.json`; here we
 * only start Koin. The Places key is the same platform-restricted key as the Maps key
 * (§11), read from the manifest so it never lives in source.
 */
class TripPlannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@TripPlannerApp)
            modules(sharedModule(placesApiKey = mapsApiKeyFromManifest()), androidModule())
        }
        registerActivityLifecycleCallbacks(get<CurrentActivity>())
    }

    private fun mapsApiKeyFromManifest(): String =
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
}
