package app.tripplanner.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Places API (New), called client-direct with a platform-restricted key (design SS5).
 * Session tokens tie autocomplete + the following details call into one billed session.
 * Field masks are mandatory — keep them minimal.
 */
class PlacesApi(private val http: HttpClient, private val apiKey: String) {

    @Serializable data class AutocompleteRequest(val input: String, val sessionToken: String)
    @Serializable data class Suggestion(val placePrediction: PlacePrediction? = null)
    @Serializable data class PlacePrediction(val placeId: String = "", val text: Text = Text())
    @Serializable data class Text(val text: String = "")
    @Serializable data class AutocompleteResponse(val suggestions: List<Suggestion> = emptyList())

    @Serializable data class Place(
        val id: String = "",
        val displayName: Text = Text(),
        val formattedAddress: String = "",
        val location: LatLng = LatLng(),
    ) { @Serializable data class LatLng(val latitude: Double = 0.0, val longitude: Double = 0.0) }

    /**
     * Fetches autocomplete suggestions for query [input].
     *
     * Complexity:
     * - **Time:** O(K) where K is the serialized request/response payload size (network I/O and JSON parsing).
     * - **Space:** O(P) auxiliary memory to hold the list of parsed predictions/suggestions.
     */
    suspend fun autocomplete(input: String, sessionToken: String): AutocompleteResponse =
        http.post("https://places.googleapis.com/v1/places:autocomplete") {
            contentType(ContentType.Application.Json)
            header("X-Goog-Api-Key", apiKey)
            setBody(AutocompleteRequest(input, sessionToken))
        }.body()

    /**
     * Fetches detailed information for [placeId].
     *
     * Complexity:
     * - **Time:** O(B) where B is the response byte payload size for the requested field mask.
     * - **Space:** O(1) auxiliary allocation (single [Place] model).
     */
    suspend fun details(placeId: String, sessionToken: String): Place =
        http.get("https://places.googleapis.com/v1/places/$placeId") {
            header("X-Goog-Api-Key", apiKey)
            header("X-Goog-FieldMask", "id,displayName,formattedAddress,location")
            header("X-Goog-Session-Token", sessionToken)
        }.body()
}
