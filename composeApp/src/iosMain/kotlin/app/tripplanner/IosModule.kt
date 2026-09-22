package app.tripplanner

import app.tripplanner.shared.di.sharedModule
import app.tripplanner.shared.platform.GoogleSignInProvider
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * iOS actuals for the platform boundaries in shared/platform (design §6.4).
 *
 * Complexity:
 * - **Time:** O(1) module definition.
 * - **Space:** O(1).
 */
fun iosModule() = module {
    single<GoogleSignInProvider> { IosGoogleSignInProvider() }
}

/**
 * Process-wide startup, called once from `iOSApp.init` after `FirebaseApp.configure()`
 * (GitLive's `Firebase.auth` needs the default app to exist). [placesApiKey] comes from the
 * app's plist, not source (design §11).
 *
 * Complexity:
 * - **Time:** O(D) where D is the number of Koin definitions registered.
 * - **Space:** O(D) for the definition registry.
 */
fun initKoin(placesApiKey: String) {
    startKoin { modules(sharedModule(placesApiKey), iosModule()) }
}

/**
 * Placeholder until the GoogleSignIn SDK is wired through Swift (spike task 5, iOS half):
 * the button works, the failure surfaces as a snackbar instead of a crash.
 *
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
private class IosGoogleSignInProvider : GoogleSignInProvider {
    override suspend fun signIn(): String =
        throw UnsupportedOperationException("Google Sign-In on iOS is not wired yet (spike task 5)")

    override suspend fun signOut() = Unit
}
