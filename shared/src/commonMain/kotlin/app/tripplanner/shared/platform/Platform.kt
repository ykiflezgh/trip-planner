package app.tripplanner.shared.platform

/**
 * Platform boundaries from design SS6.4. Implemented in androidMain / iosApp,
 * bound via Koin at app startup. Everything else in `shared` is pure common code.
 */
interface GoogleSignInProvider {
    /** Runs the platform sign-in UI and returns a Google ID token for Firebase Auth. */
    suspend fun signIn(): String
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

/** Thrown by [GoogleSignInProvider.signIn] when the user dismisses the platform UI. */
class SignInCancelledException : Exception("Sign-in cancelled")
