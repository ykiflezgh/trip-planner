package app.tripplanner.shared.feature.trips

import app.tripplanner.shared.core.model.TravelLeg

/** Wording for travel legs between stops (design §8.3). */
object TravelText {
    /**
     * "12 min · 4.2 km"; hours when long ("1 h 05 min"); "—" for a leg Routes could not answer.
     *
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun format(leg: TravelLeg): String {
        val seconds = leg.seconds ?: return "\u2014"
        if (leg.error != null) return "\u2014"
        return duration(seconds) + " \u00b7 " + distance(leg.meters ?: 0)
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun duration(seconds: Int): String {
        val minutes = (seconds + 30) / 60
        return when {
            minutes < 1 -> "<1 min"
            minutes < 60 -> "$minutes min"
            else -> "${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min"
        }
    }

    /**
     * Complexity:
     * - **Time:** O(1).
     * - **Space:** O(1).
     */
    fun distance(meters: Int): String = when {
        meters < 1000 -> "$meters m"
        else -> {
            val km = meters / 1000.0
            val rounded = if (km >= 10) km.toInt().toString() else ((km * 10).toInt() / 10.0).toString()
            "$rounded km"
        }
    }
}
