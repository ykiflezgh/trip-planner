package app.tripplanner.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.tripplanner.shared.platform.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Validated-internet reachability from [ConnectivityManager] (design §9).
 *
 * Complexity:
 * - **Time:** O(1) per network callback.
 * - **Space:** O(1).
 */
class AndroidConnectivityMonitor(context: Context) : ConnectivityMonitor {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(manager.isOnlineNow())
    override val online: StateFlow<Boolean> = _online

    init {
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _online.value = caps.hasInternet()
            }
            override fun onLost(network: Network) { _online.value = manager.isOnlineNow() }
            override fun onUnavailable() { _online.value = false }
        })
    }

    private fun ConnectivityManager.isOnlineNow(): Boolean =
        activeNetwork?.let { getNetworkCapabilities(it) }?.hasInternet() == true

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
