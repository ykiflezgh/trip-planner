package app.tripplanner.shared.data

import app.tripplanner.shared.platform.GoogleSignInProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.flow.Flow

/**
 * Firebase Auth over the platform sign-in boundary (design §5, §6.4): the platform
 * provider runs Google's UI and hands back an ID token; Firebase exchanges it for a
 * Firebase user. Requires the Google provider to be enabled in Firebase Auth.
 */
class AuthRepository(private val google: GoogleSignInProvider) {

    /** Emits on every auth change; `null` when signed out. */
    val user: Flow<FirebaseUser?> get() = Firebase.auth.authStateChanged

    val currentUser: FirebaseUser? get() = Firebase.auth.currentUser

    suspend fun signInWithGoogle(): FirebaseUser? {
        val tokens = google.signIn()
        val credential = GoogleAuthProvider.credential(idToken = tokens.idToken, accessToken = tokens.accessToken)
        return Firebase.auth.signInWithCredential(credential).user
    }

    suspend fun signOut() {
        Firebase.auth.signOut()
        // Clear Credential Manager / GoogleSignIn state so the next sign-in shows the chooser again.
        google.signOut()
    }
}
