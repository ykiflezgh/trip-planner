package app.tripplanner.shared.data

import app.tripplanner.shared.core.model.ActivityEvent
import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

/** `trips/{tripId}/activity`, newest first (design §7, §10). Read-only: Functions write it. */
interface ActivityRepository {
    fun activity(tripId: String, limit: Int = DEFAULT_LIMIT): Flow<List<ActivityEvent>>

    companion object { const val DEFAULT_LIMIT = 100 }
}

class FirestoreActivityRepository : ActivityRepository {
    private val db = Firebase.firestore
    private val log = Logger.withTag("Firestore")

    /**
     * Complexity:
     * - **Time:** O(E) per snapshot for the E events in the window (server-side ordered).
     * - **Space:** O(E).
     */
    override fun activity(tripId: String, limit: Int): Flow<List<ActivityEvent>> =
        db.collection("trips").document(tripId).collection("activity")
            .orderBy("createdAt", Direction.DESCENDING)
            .limit(limit)
            .snapshots
            .map { qs -> qs.documents.map { it.data<ActivityEvent>().copy(id = it.id) } }
            .onStart { log.i { "listen trips/$tripId/activity" } }
            .onCompletion { log.i { "unlisten trips/$tripId/activity" } }
}
