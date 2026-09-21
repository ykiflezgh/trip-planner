package app.tripplanner.shared.data

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.functions.functions

/**
 * Callable Cloud Functions (design SS8.2, SS8.5). Server holds the Routes and
 * Gemini keys; App Check is enforced backend-side.
 * STATUS: verify GitLive callable/data() shapes during the spike.
 */
class PlanningFunctions {
    private val fns = Firebase.functions

    suspend fun createInvite(tripId: String): String =
        fns.httpsCallable("createInvite").invoke(mapOf("tripId" to tripId)).data()

    suspend fun redeemInvite(code: String): String =
        fns.httpsCallable("redeemInvite").invoke(mapOf("code" to code)).data()

    suspend fun suggestDayOrder(tripId: String, day: Int): List<String> =
        fns.httpsCallable("suggestDayOrder").invoke(mapOf("tripId" to tripId, "day" to day)).data()
}
