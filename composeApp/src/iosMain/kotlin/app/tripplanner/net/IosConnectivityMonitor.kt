package app.tripplanner.net

import app.tripplanner.shared.platform.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

/**
 * Reachability from the Network framework's path monitor (design §9). Starts optimistic and
 * corrects itself on the first path update, which arrives immediately after start.
 *
 * Complexity:
 * - **Time:** O(1) per path update.
 * - **Space:** O(1).
 */
class IosConnectivityMonitor : ConnectivityMonitor {
    private val _online = MutableStateFlow(true)
    override val online: StateFlow<Boolean> = _online
    private val monitor = nw_path_monitor_create()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            _online.value = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }
}
