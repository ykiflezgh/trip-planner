@file:JsModule("firebase/auth")

package app.tripplanner.shared.web.externals

import kotlin.js.Promise

/**
 * The few Firebase Web SDK auth entry points the browser sign-in needs (design companion §8.1).
 * GitLive wraps the same `firebase` package, so this is the same default app and the same
 * `Auth` instance the shared repositories observe.
 */
external fun getAuth(app: dynamic = definedExternally): dynamic
external fun signInWithPopup(auth: dynamic, provider: dynamic): Promise<dynamic>
external fun signInWithRedirect(auth: dynamic, provider: dynamic): Promise<Unit>
external fun getRedirectResult(auth: dynamic): Promise<dynamic>
external fun signOut(auth: dynamic): Promise<Unit>

external class GoogleAuthProvider {
    fun setCustomParameters(params: dynamic)
    companion object {
        fun credentialFromResult(result: dynamic): dynamic
    }
}
