package app.tripplanner.shared.feature.events

import app.tripplanner.shared.core.model.Event
import app.tripplanner.shared.core.util.FractionalIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlacesSearchTest {
    @Test
    fun queriesOnlyFromThreeTrimmedCharacters() {
        assertFalse(PlacesSearch.shouldQuery(""))
        assertFalse(PlacesSearch.shouldQuery("  ab "))
        assertTrue(PlacesSearch.shouldQuery("abc"))
        assertTrue(PlacesSearch.shouldQuery(" Torre "))
    }

    @Test
    fun sessionTokensAreUnique() {
        assertNotEquals(PlacesSearch.newSessionToken(), PlacesSearch.newSessionToken())
    }

    @Test
    fun appendKeySortsAfterEveryExistingStopOfTheDay() {
        val keys = FractionalIndex.spread(3)
        val day = keys.shuffled().map { Event(id = it, order = it) }
        val appended = PlacesSearch.appendOrderKey(day)
        assertTrue(keys.all { it < appended }, "$appended must sort after ${keys.max()}")
    }

    @Test
    fun appendKeyOnEmptyDayIsTheFirstKey() {
        assertEquals(FractionalIndex.first(), PlacesSearch.appendOrderKey(emptyList()))
    }
}
