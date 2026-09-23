package app.tripplanner.shared.platform

/**
 * Platform boundaries from design SS6.4. Implemented in androidMain / iosApp,
 * bound via Koin at app startup. Everything else in `shared` is pure common code.
 */
/**
 * Tokens from the platform Google sign-in. Firebase Auth needs the ID token everywhere; on
 * iOS the access token is required as well (FIRGoogleAuthProvider), on Android it is unused.
 */
data class GoogleTokens(val idToken: String, val accessToken: String? = null)

interface GoogleSignInProvider {
    /** Runs the platform sign-in UI and returns Google tokens for Firebase Auth. */
    suspend fun signIn(): GoogleTokens
    suspend fun signOut()
}

interface GoogleAuthorizer {
    /**
     * Incremental authorization: request an OAuth access token for [scopes]
     * (e.g. https://www.googleapis.com/auth/calendar.events) at the moment of use.
     * Tokens are held in memory only — never persisted (design SS8.4).
     */
    suspend fun requestAccessToken(scopes: List<String>): String
}

interface PushTokenProvider {
    /** Current FCM registration token, refreshed by platform callbacks. */
    suspend fun currentToken(): String?
}

/**
 * Network reachability (design §9): search and export need connectivity, so the UI disables
 * them with a message while offline. Firestore writes never wait for this - they queue locally.
 */
interface ConnectivityMonitor {
    val online: kotlinx.coroutines.flow.StateFlow<Boolean>
}

/** Platform share sheet for invite links (design §8.2). */
interface ShareSheet {
    fun share(text: String, title: String)
}

/** Thrown by [GoogleSignInProvider.signIn] when the user dismisses the platform UI. */
class SignInCancelledException : Exception("Sign-in cancelled")
