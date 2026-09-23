package app.tripplanner.shared.data

import app.tripplanner.shared.platform.ConnectivityMonitor
import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The two Firestore calls the sync needs, behind an interface so the policy is unit-testable. */
interface FirestoreNetwork {
    suspend fun enable()
    suspend fun disable()
}

/**
 * Complexity:
 * - **Time:** O(1) per call (SDK round trip).
 * - **Space:** O(1).
 */
class GitLiveFirestoreNetwork : FirestoreNetwork {
    override suspend fun enable() = Firebase.firestore.enableNetwork()
    override suspend fun disable() = Firebase.firestore.disableNetwork()
}

/**
 * Keeps Firestore's connection honest about the device's reachability (design §9). After an
 * abrupt network transition the SDK's gRPC streams can stay closed for many minutes with
 * writes queued locally ("syncing…" forever); disabling and re-enabling the network forces a
 * fresh connection. Runs for the process lifetime from Koin (`createdAtStart`).
 */
class FirestoreNetworkSync(
    private val connectivity: ConnectivityMonitor,
    private val network: FirestoreNetwork,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val log = Logger.withTag("FirestoreNetworkSync")
    private val lock = Mutex()

    /**
     * Starts following [ConnectivityMonitor.online]: offline -> disable, back online -> reconnect.
     * The current value is skipped; only transitions act.
     *
     * Complexity:
     * - **Time:** O(1) per connectivity change.
     * - **Space:** O(1).
     */
    fun start() {
        connectivity.online
            .drop(1)
            .onEach { online -> if (online) reconnect() else disableQuietly() }
            .launchIn(scope)
    }

    /**
     * Manual kick (the "Retry" affordance): disable then enable, serialized against other kicks.
     *
     * Complexity:
     * - **Time:** O(1) plus two SDK round trips.
     * - **Space:** O(1).
     */
    fun retry() {
        scope.launch { reconnect() }
    }

    private suspend fun reconnect() = lock.withLock {
        log.i { "reconnecting Firestore" }
        runCatching { network.disable() }.onFailure { log.w { "disable failed: ${it.message}" } }
        runCatching { network.enable() }.onFailure { log.w { "enable failed: ${it.message}" } }
    }

    private suspend fun disableQuietly() = lock.withLock {
        log.i { "offline: disabling Firestore network" }
        runCatching { network.disable() }.onFailure { log.w { "disable failed: ${it.message}" } }
    }
}
