package app.tripplanner.shared.data

import app.tripplanner.shared.core.model.Stop
import app.tripplanner.shared.core.model.Trip
import app.tripplanner.shared.core.util.FractionalIndex
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TripRepository {
    fun myTrips(uid: String): Flow<List<Trip>>
    fun stops(tripId: String): Flow<List<Stop>>
    suspend fun createTrip(trip: Trip): String
    suspend fun addStop(tripId: String, stop: Stop, afterOrder: String?, beforeOrder: String?)
    suspend fun moveStop(tripId: String, stopId: String, day: Int, afterOrder: String?, beforeOrder: String?)
    suspend fun deleteStop(tripId: String, stopId: String)
}

/**
 * Firestore implementation over the GitLive SDK (design SS5, SS7).
 *
 * STATUS: reference implementation written against GitLive 2.x from memory —
 * verify each call against https://github.com/GitLiveApp/firebase-kotlin-sdk
 * during the Phase 0 spike before trusting it. Offline persistence is on by
 * default on Android/iOS.
 */
class FirestoreTripRepository : TripRepository {
    private val db = Firebase.firestore

    /**
     * Observes real-time trips belonging to the user [uid].
     *
     * Complexity:
     * - **Time:** O(N) per snapshot emission, where N is the number of trips matching the query.
     * - **Space:** O(N) heap allocation to instantiate the list of [Trip] objects.
     */
    override fun myTrips(uid: String): Flow<List<Trip>> =
        db.collection("trips")
            .where { "memberIds" contains uid }
            .snapshots
            .map { qs -> qs.documents.map { it.data<Trip>().copy(id = it.id) } }

    /**
     * Observes real-time stops for [tripId] sorted by day and fractional index order.
     *
     * Complexity:
     * - **Time:** O(S log S) per snapshot emission, where S is the total number of stops in the trip (sorting by day and order).
     * - **Space:** O(S) heap allocation to instantiate and sort the list of [Stop] objects.
     */
    override fun stops(tripId: String): Flow<List<Stop>> =
        db.collection("trips").document(tripId).collection("stops")
            .snapshots // client-side sort keeps the composite index optional on the read path
            .map { qs ->
                qs.documents.map { it.data<Stop>().copy(id = it.id) }
                    .sortedWith(compareBy({ it.day }, { it.order }))
            }

    /**
     * Creates a new trip document.
     *
     * Complexity:
     * - **Time:** O(1) network request / document write.
     * - **Space:** O(1) auxiliary memory.
     */
    override suspend fun createTrip(trip: Trip): String {
        val doc = db.collection("trips").document
        doc.set(trip.copy(id = doc.id, createdAt = Timestamp.ServerTimestamp, updatedAt = Timestamp.ServerTimestamp))
        return doc.id
    }

    /**
     * Adds a stop with a calculated fractional index between [afterOrder] and [beforeOrder].
     *
     * Complexity:
     * - **Time:** O(L) where L is the fractional index key length (effectively O(1)) + O(1) document write.
     * - **Space:** O(L) auxiliary space for key generation.
     */
    override suspend fun addStop(tripId: String, stop: Stop, afterOrder: String?, beforeOrder: String?) {
        val doc = db.collection("trips").document(tripId).collection("stops").document
        doc.set(stop.copy(id = doc.id, order = FractionalIndex.between(afterOrder, beforeOrder)))
    }

    /**
     * Moves a stop to [day] and reorders it between [afterOrder] and [beforeOrder].
     *
     * Complexity:
     * - **Time:** O(L) for index calculation + O(1) single-document field update.
     * - **Space:** O(L) auxiliary space for the index key.
     */
    override suspend fun moveStop(tripId: String, stopId: String, day: Int, afterOrder: String?, beforeOrder: String?) {
        db.collection("trips").document(tripId).collection("stops").document(stopId)
            .update("day" to day, "order" to FractionalIndex.between(afterOrder, beforeOrder))
        // NOT_FOUND here means another member deleted the stop: drop the local change (design SS7).
    }

    /**
     * Deletes a stop document by [stopId].
     *
     * Complexity:
     * - **Time:** O(1) single-document deletion.
     * - **Space:** O(1) auxiliary memory.
     */
    override suspend fun deleteStop(tripId: String, stopId: String) {
        db.collection("trips").document(tripId).collection("stops").document(stopId).delete()
    }
}
