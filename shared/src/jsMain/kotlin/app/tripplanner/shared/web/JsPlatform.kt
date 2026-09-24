package app.tripplanner.shared.web

import app.tripplanner.shared.platform.ConnectivityMonitor
import app.tripplanner.shared.platform.GoogleSignInProvider
import app.tripplanner.shared.platform.GoogleTokens
import app.tripplanner.shared.platform.PushTokenProvider
import app.tripplanner.shared.platform.Reminder
import app.tripplanner.shared.platform.ReminderScheduler
import app.tripplanner.shared.platform.ShareSheet
import app.tripplanner.shared.platform.SignInCancelledException
import app.tripplanner.shared.web.externals.GoogleAuthProvider
import app.tripplanner.shared.web.externals.getAuth
import app.tripplanner.shared.web.externals.signInWithPopup
import app.tripplanner.shared.web.externals.signInWithRedirect
import app.tripplanner.shared.web.externals.signOut
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.dsl.module

/**
 * Browser actuals for the platform seams in `shared/platform` (design v1.1.1 §6.5, companion §6.2).
 *
 * Complexity:
 * - **Time:** O(1) module definition.
 * - **Space:** O(1).
 */
fun jsModule() = module {
    single<GoogleSignInProvider> { JsGoogleSignInProvider() }
    single<ConnectivityMonitor> { JsConnectivityMonitor() }
    single<ShareSheet> { JsShareSheet() }
    single<PushTokenProvider> { JsPushTokenProvider() }
    single<ReminderScheduler> { JsReminderScheduler() }
}

/**
 * Google sign-in through the Firebase Web SDK: popup first, redirect where the browser blocks
 * popups (companion §8.1, §11). The popup already signs the Firebase user in; the ID token is
 * handed back so [app.tripplanner.shared.data.AuthRepository] follows the same path as the apps.
 */
class JsGoogleSignInProvider : GoogleSignInProvider {
    /**
     * Complexity:
     * - **Time:** O(1) round trip to Google.
     * - **Space:** O(1).
     */
    override suspend fun signIn(): GoogleTokens {
        val auth = getAuth()
        val provider = GoogleAuthProvider()
        val result: dynamic = try {
            signInWithPopup(auth, provider).await()
        } catch (e: Throwable) {
            val code = (e.asDynamic().code as? String).orEmpty()
            when {
                code.endsWith("popup-closed-by-user") || code.endsWith("cancelled-popup-request") -> throw SignInCancelledException()
                code.endsWith("popup-blocked") || code.endsWith("operation-not-supported-in-this-environment") -> {
                    signInWithRedirect(auth, provider).await() // navigates away; resolved by resumeRedirect() on return
                    throw SignInCancelledException()
                }
                else -> throw e
            }
        }
        val credential = GoogleAuthProvider.credentialFromResult(result)
        val idToken = credential?.idToken as? String ?: throw IllegalStateException("Google returned no ID token")
        return GoogleTokens(idToken = idToken, accessToken = credential.accessToken as? String)
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    override suspend fun signOut() {
        signOut(getAuth()).await()
    }

}

/** `navigator.onLine` plus the window's online/offline events (design §9). */
class JsConnectivityMonitor : ConnectivityMonitor {
    private val state = MutableStateFlow(window.navigator.onLine)
    override val online: StateFlow<Boolean> get() = state

    init {
        window.addEventListener("online", { state.value = true })
        window.addEventListener("offline", { state.value = false })
    }
}

/** Web Share API where available, clipboard otherwise (design §8.2). */
class JsShareSheet : ShareSheet {
    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    override fun share(text: String, title: String) {
        val nav = window.navigator.asDynamic()
        if (nav.share != undefined) {
            val data: dynamic = js("({})")
            data.title = title
            data.text = text
            nav.share(data)
        } else {
            nav.clipboard?.writeText(text)
        }
    }
}

/** Web push arrives in a later phase (companion §10); until then no token and no permission. */
class JsPushTokenProvider : PushTokenProvider {
    override val token: StateFlow<String?> = MutableStateFlow(null)
    override suspend fun requestPermission(): Boolean = false
}

/** Browsers cannot schedule background notifications (companion §10): reminders are a no-op on the web. */
class JsReminderScheduler : ReminderScheduler {
    override val supported: Boolean get() = false
    override fun replace(reminders: List<Reminder>) = Unit
}
