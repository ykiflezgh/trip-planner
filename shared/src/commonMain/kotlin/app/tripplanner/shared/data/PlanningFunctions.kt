package app.tripplanner.shared.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.FirebaseFunctionsException
import dev.gitlive.firebase.functions.code
import dev.gitlive.firebase.functions.functions
import kotlinx.serialization.Serializable

/**
 * Callable Cloud Functions (design §8.2, §8.5). The server holds the Routes and Gemini keys;
 * membership changes only happen server-side. Errors carry the Function's user-facing message.
 */
class PlanningFunctions {
    private val fns = Firebase.functions

    @Serializable data class InviteCreated(val code: String)
    @Serializable data class InviteRedeemed(val tripId: String, val alreadyMember: Boolean = false)
    @Serializable data class DayOrder(val orderedStopIds: List<String>, val rationale: String = "")
    @Serializable data class FeedCreated(val url: String)
    @Serializable data class FeedRevoked(val revoked: Int = 0)

    /** Thrown with the message the Function chose for the user (e.g. "This invite link has expired."). */
    class FunctionException(val code: String, message: String) : Exception(message)

    /**
     * Complexity:
     * - **Time:** O(1) callable round trip.
     * - **Space:** O(1).
     */
    suspend fun createInvite(tripId: String): InviteCreated = call {
        fns.httpsCallable("createInvite").invoke(mapOf("tripId" to tripId)).data()
    }

    /**
     * Complexity:
     * - **Time:** O(1) callable round trip (a transaction server-side).
     * - **Space:** O(1).
     */
    suspend fun redeemInvite(code: String): InviteRedeemed = call {
        fns.httpsCallable("redeemInvite").invoke(mapOf("code" to code)).data()
    }

    /**
     * Complexity:
     * - **Time:** O(K) for the K stop ids returned.
     * - **Space:** O(K).
     */
    suspend fun suggestDayOrder(tripId: String, day: Int): DayOrder = call {
        fns.httpsCallable("suggestDayOrder").invoke(mapOf("tripId" to tripId, "day" to day)).data()
    }

    /**
     * Mints a private ICS feed link for this member and trip (design §8.6); the token is returned once.
     *
     * Complexity:
     * - **Time:** O(1) callable round trip.
     * - **Space:** O(1).
     */
    suspend fun createCalendarFeed(tripId: String): FeedCreated = call {
        fns.httpsCallable("createCalendarFeed").invoke(mapOf("tripId" to tripId)).data()
    }

    /**
     * Revokes every feed link this member created for the trip (design §8.6).
     *
     * Complexity:
     * - **Time:** O(1) callable round trip (O(F) server-side for the F links).
     * - **Space:** O(1).
     */
    suspend fun revokeCalendarFeed(tripId: String): FeedRevoked = call {
        fns.httpsCallable("revokeCalendarFeed").invoke(mapOf("tripId" to tripId)).data()
    }

    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: FirebaseFunctionsException) {
        throw FunctionException(e.code.toString(), e.message ?: "Something went wrong")
    }
}
