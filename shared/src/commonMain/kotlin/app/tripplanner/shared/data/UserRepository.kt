package app.tripplanner.shared.data

import app.tripplanner.shared.core.model.NotificationPrefs
import app.tripplanner.shared.core.model.UserProfile
import co.touchlab.kermit.Logger
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * `users/{uid}`: display profile, FCM tokens and notification preferences (design §7, §10).
 * Only the user writes their own document (rules); Functions read it for fan-out.
 */
interface UserRepository {
    /** Live preferences of [uid]; defaults when the document does not exist yet. */
    fun prefs(uid: String): Flow<NotificationPrefs>
    /** Merges name/photo so Functions can denormalize `actorName` into activity events. */
    suspend fun ensureProfile(uid: String, displayName: String?, photoUrl: String?)
    /** Adds [token], keeping at most [MAX_TOKENS] per user (oldest dropped). */
    suspend fun registerToken(uid: String, token: String)
    suspend fun unregisterToken(uid: String, token: String)
    suspend fun setPushEnabled(uid: String, enabled: Boolean)
    suspend fun setTripMuted(uid: String, tripId: String, muted: Boolean)
    suspend fun setReminders(uid: String, enabled: Boolean, leadMin: Int)

    companion object {
        /** Design §10: "keep at most five per user". */
        const val MAX_TOKENS = 5

        /**
         * Tokens to keep after adding [token]: existing order is registration order (arrayUnion
         * appends), so the oldest are dropped from the front.
         *
         * Complexity:
         * - **Time:** O(T) for T existing tokens.
         * - **Space:** O(T).
         */
        fun trimTokens(existing: List<String>, token: String): List<String> =
            (existing.filter { it != token } + token).takeLast(MAX_TOKENS)
    }
}

class FirestoreUserRepository : UserRepository {
    private val db = Firebase.firestore
    private val log = Logger.withTag("Users")
    private fun doc(uid: String) = db.collection("users").document(uid)

    /**
     * Complexity:
     * - **Time:** O(1) per snapshot (single document decode).
     * - **Space:** O(T) for the T tokens in the document.
     */
    override fun prefs(uid: String): Flow<NotificationPrefs> =
        doc(uid).snapshots.map { snap -> if (snap.exists) snap.data<UserProfile>().notificationPrefs else NotificationPrefs() }

    /**
     * Complexity:
     * - **Time:** O(1) merge write.
     * - **Space:** O(1).
     */
    override suspend fun ensureProfile(uid: String, displayName: String?, photoUrl: String?) {
        doc(uid).set(mapOf("displayName" to displayName.orEmpty(), "photoUrl" to photoUrl.orEmpty()), merge = true)
    }

    /**
     * Read-then-write rather than a bare arrayUnion so the cap holds without a Function.
     *
     * Complexity:
     * - **Time:** O(T) one read + one write.
     * - **Space:** O(T).
     */
    override suspend fun registerToken(uid: String, token: String) {
        val snap = doc(uid).get()
        val existing = if (snap.exists) snap.data<UserProfile>().fcmTokens else emptyList()
        if (existing.lastOrNull() == token && existing.size <= UserRepository.MAX_TOKENS) return
        doc(uid).set(mapOf("fcmTokens" to UserRepository.trimTokens(existing, token)), merge = true)
        log.i { "registered push token (${existing.size} -> ${minOf(existing.size + 1, UserRepository.MAX_TOKENS)})" }
    }

    /**
     * Complexity:
     * - **Time:** O(1) field update.
     * - **Space:** O(1).
     */
    override suspend fun unregisterToken(uid: String, token: String) {
        doc(uid).set(mapOf("fcmTokens" to FieldValue.arrayRemove(token)), merge = true)
    }

    /**
     * Complexity:
     * - **Time:** O(1) merge write.
     * - **Space:** O(1).
     */
    override suspend fun setPushEnabled(uid: String, enabled: Boolean) {
        doc(uid).set(mapOf("notificationPrefs" to mapOf("push" to enabled)), merge = true)
    }

    /**
     * Complexity:
     * - **Time:** O(1) merge write.
     * - **Space:** O(1).
     */
    /**
     * Complexity:
     * - **Time:** O(1) merge write.
     * - **Space:** O(1).
     */
    override suspend fun setReminders(uid: String, enabled: Boolean, leadMin: Int) {
        doc(uid).set(mapOf("notificationPrefs" to mapOf("reminders" to mapOf("enabled" to enabled, "leadMin" to leadMin))), merge = true)
    }

    override suspend fun setTripMuted(uid: String, tripId: String, muted: Boolean) {
        val op = if (muted) FieldValue.arrayUnion(tripId) else FieldValue.arrayRemove(tripId)
        doc(uid).set(mapOf("notificationPrefs" to mapOf("mutedTripIds" to op)), merge = true)
    }
}
