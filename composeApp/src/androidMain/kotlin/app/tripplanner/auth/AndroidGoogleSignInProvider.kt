package app.tripplanner.auth

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import app.tripplanner.shared.platform.GoogleSignInProvider
import app.tripplanner.shared.platform.GoogleTokens
import app.tripplanner.shared.platform.SignInCancelledException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.lang.ref.WeakReference

/** Tracks the foreground Activity: Credential Manager needs one to host its bottom sheet. */
class CurrentActivity : Application.ActivityLifecycleCallbacks {
    private var ref = WeakReference<Activity>(null)
    val activity: Activity? get() = ref.get()

    override fun onActivityResumed(activity: Activity) { ref = WeakReference(activity) }
    override fun onActivityPaused(activity: Activity) { if (ref.get() === activity) ref = WeakReference(null) }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * Sign in with Google through Credential Manager + googleid (design §6.4).
 * [serverClientId] is the Firebase project's *web* OAuth client (`default_web_client_id`,
 * generated from google-services.json); ID tokens minted for it are what Firebase Auth's
 * Google provider accepts. The Android OAuth client (package + SHA-1) must also exist in
 * the same project, or Google returns "developer console not set up correctly".
 */
class AndroidGoogleSignInProvider(
    context: Context,
    private val currentActivity: CurrentActivity,
    private val serverClientId: String,
) : GoogleSignInProvider {

    private val credentialManager = CredentialManager.create(context)

    override suspend fun signIn(): GoogleTokens {
        require(serverClientId.isNotBlank()) { "default_web_client_id is missing - is google-services.json present?" }
        val activity = currentActivity.activity ?: error("No foreground Activity to show the sign-in UI")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(serverClientId).build())
            .build()
        val credential = try {
            credentialManager.getCredential(activity, request).credential
        } catch (_: GetCredentialCancellationException) {
            throw SignInCancelledException()
        } catch (e: NoCredentialException) {
            throw IllegalStateException("No Google account available on this device", e)
        }
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleTokens(idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken)
        }
        error("Unexpected credential type: ${credential.type}")
    }

    override suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}
