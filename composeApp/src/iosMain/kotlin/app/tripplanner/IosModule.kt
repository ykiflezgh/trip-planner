package app.tripplanner

import app.tripplanner.shared.di.sharedModule
import app.tripplanner.shared.platform.GoogleSignInProvider
import app.tripplanner.shared.platform.GoogleTokens
import app.tripplanner.shared.platform.SignInCancelledException
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.context.startKoin
import org.koin.dsl.module
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implemented in Swift (iosApp/GoogleSignInBridge.swift) over the GoogleSignIn-iOS SDK; the
 * Kotlin side never links it directly (design §6.4). Exactly one of the callback's outcomes
 * is set: tokens, `cancelled`, or an `error` message.
 */
interface GoogleSignInBridge {
    fun signIn(callback: (idToken: String?, accessToken: String?, cancelled: Boolean, error: String?) -> Unit)
    fun signOut()
}

/**
 * iOS actuals for the platform boundaries in shared/platform (design §6.4).
 *
 * Complexity:
 * - **Time:** O(1) module definition.
 * - **Space:** O(1).
 */
fun iosModule(googleSignIn: GoogleSignInBridge) = module {
    single<GoogleSignInProvider> { IosGoogleSignInProvider(googleSignIn) }
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
fun initKoin(placesApiKey: String, googleSignIn: GoogleSignInBridge) {
    startKoin { modules(sharedModule(placesApiKey), iosModule(googleSignIn)) }
}

/**
 * Adapts the callback-style Swift bridge to the suspend boundary the shared code expects.
 *
 * Complexity:
 * - **Time:** O(1) plus the interactive sign-in flow.
 * - **Space:** O(1).
 */
private class IosGoogleSignInProvider(private val bridge: GoogleSignInBridge) : GoogleSignInProvider {
    override suspend fun signIn(): GoogleTokens = suspendCancellableCoroutine { cont ->
        bridge.signIn { idToken, accessToken, cancelled, error ->
            when {
                !cont.isActive -> Unit
                cancelled -> cont.resumeWithException(SignInCancelledException())
                idToken != null -> cont.resume(GoogleTokens(idToken, accessToken))
                else -> cont.resumeWithException(IllegalStateException(error ?: "Google Sign-In failed"))
            }
        }
    }

    override suspend fun signOut() = bridge.signOut()
}
