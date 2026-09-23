package app.tripplanner.shared.data

import app.tripplanner.shared.platform.ConnectivityMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreNetworkSyncTest {
    private class FakeConnectivity(initial: Boolean) : ConnectivityMonitor {
        override val online = MutableStateFlow(initial)
    }
    private class FakeNetwork : FirestoreNetwork {
        val calls = mutableListOf<String>()
        override suspend fun enable() { calls += "enable" }
        override suspend fun disable() { calls += "disable" }
    }

    @Test
    fun ignoresInitialStateAndReconnectsOnlyOnTransitions() = runTest {
        val connectivity = FakeConnectivity(initial = true)
        val network = FakeNetwork()
        FirestoreNetworkSync(connectivity, network, TestScope(StandardTestDispatcher(testScheduler))).start()
        advanceUntilIdle()
        assertEquals(emptyList(), network.calls, "no kick on start")

        connectivity.online.value = false
        advanceUntilIdle()
        assertEquals(listOf("disable"), network.calls)

        connectivity.online.value = true
        advanceUntilIdle()
        assertEquals(listOf("disable", "disable", "enable"), network.calls, "back online = fresh connection")
    }

    @Test
    fun retryForcesDisableThenEnable() = runTest {
        val network = FakeNetwork()
        val sync = FirestoreNetworkSync(FakeConnectivity(true), network, TestScope(StandardTestDispatcher(testScheduler)))
        sync.retry()
        advanceUntilIdle()
        assertEquals(listOf("disable", "enable"), network.calls)
    }
}
