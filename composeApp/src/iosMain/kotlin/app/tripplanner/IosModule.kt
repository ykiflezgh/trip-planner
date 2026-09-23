package app.tripplanner

import app.tripplanner.net.IosConnectivityMonitor
import app.tripplanner.share.IosShareSheet
import app.tripplanner.shared.di.AppConfig
import app.tripplanner.push.IosPushTokenProvider
import app.tripplanner.push.PushBridge
import app.tripplanner.reminders.IosReminderScheduler
import app.tripplanner.reminders.ReminderBridge
import app.tripplanner.shared.feature.reminders.SyncReminders
import app.tripplanner.shared.platform.ReminderScheduler
import app.tripplanner.shared.feature.links.DeepLinkIntake
import app.tripplanner.shared.platform.PushTokenProvider
import app.tripplanner.shared.platform.ShareSheet
import org.koin.mp.KoinPlatform
import app.tripplanner.shared.di.sharedModule
import app.tripplanner.shared.platform.ConnectivityMonitor
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
fun iosModule(googleSignIn: GoogleSignInBridge, push: PushBridge, reminders: ReminderBridge) = module {
    single<ReminderScheduler> { IosReminderScheduler(reminders) }
    single<GoogleSignInProvider> { IosGoogleSignInProvider(googleSignIn) }
    single<ConnectivityMonitor> { IosConnectivityMonitor() }
    single<ShareSheet> { IosShareSheet() }
    single<PushTokenProvider> { IosPushTokenProvider(push) }
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
fun initKoin(placesApiKey: String, googleSignIn: GoogleSignInBridge, push: PushBridge, reminders: ReminderBridge, appLinkHost: String) {
    val config = if (appLinkHost.isBlank()) AppConfig() else AppConfig(appLinkHost = appLinkHost)
    startKoin { modules(sharedModule(placesApiKey, config), iosModule(googleSignIn, push, reminders)) }
}

/**
 * Foreground / launch hook (design v1.1 §8.5): resyncs local reminders.
 *
 * Complexity:
 * - **Time:** O(1) to request; the sync itself is O(T · S).
 * - **Space:** O(1).
 */
fun syncReminders(reason: String) = KoinPlatform.getKoin().get<SyncReminders>().request(reason)

/**
 * Entry for `onOpenURL` / Universal Link continuation (design §8.2). Returns false for URLs
 * that are not invite links so the caller can pass them on.
 *
 * Complexity:
 * - **Time:** O(N) for an N-character URL.
 * - **Space:** O(1).
 */
fun offerInviteUrl(url: String): Boolean = KoinPlatform.getKoin().get<DeepLinkIntake>().offer(url)

/**
 * Notification tap (design §10): the APNs payload carries the trip and stop ids.
 *
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
fun offerTripLink(tripId: String, stopId: String?): Boolean = KoinPlatform.getKoin().get<DeepLinkIntake>().offerTrip(tripId, stopId)

/**
 * FCM token from `MessagingDelegate` (design §6.4); `null` when Firebase Messaging drops it.
 *
 * Complexity:
 * - **Time:** O(1).
 * - **Space:** O(1).
 */
fun setPushToken(token: String?) = IosPushTokenProvider.setToken(token)

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
