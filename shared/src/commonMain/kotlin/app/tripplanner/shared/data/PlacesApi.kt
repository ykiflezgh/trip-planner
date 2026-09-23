package app.tripplanner.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Places API (New), called client-direct with a platform-restricted key (design §5, §8.1).
 * A session token ties one autocomplete series + the following details call into a single
 * billed session: it goes in the autocomplete body and as the `sessionToken` query parameter
 * of the details request. Field masks are mandatory - keep them minimal.
 */
class PlacesApi(private val http: HttpClient, private val apiKey: String) {

    @Serializable data class AutocompleteRequest(val input: String, val sessionToken: String)
    @Serializable data class Suggestion(val placePrediction: PlacePrediction? = null)
    @Serializable data class PlacePrediction(
        val placeId: String = "",
        val text: Text = Text(),
        val structuredFormat: StructuredFormat? = null,
    )
    @Serializable data class StructuredFormat(val mainText: Text = Text(), val secondaryText: Text = Text())
    @Serializable data class Text(val text: String = "")
    @Serializable data class AutocompleteResponse(val suggestions: List<Suggestion> = emptyList())

    @Serializable data class Place(
        val id: String = "",
        val displayName: Text = Text(),
        val formattedAddress: String = "",
        val location: LatLng = LatLng(),
    ) { @Serializable data class LatLng(val latitude: Double = 0.0, val longitude: Double = 0.0) }

    /** Autocomplete result flattened for the UI. */
    data class PlaceSuggestion(val placeId: String, val primaryText: String, val secondaryText: String)

    /** Raised for non-2xx responses with Google's own message (key/API problems read clearly). */
    class PlacesException(val status: Int, message: String) : Exception(message)

    /**
     * Fetches autocomplete suggestions for [input] within the session [sessionToken].
     *
     * Complexity:
     * - **Time:** O(K) where K is the serialized request/response payload size (network I/O and JSON parsing).
     * - **Space:** O(P) for the P parsed suggestions.
     */
    suspend fun autocomplete(input: String, sessionToken: String): List<PlaceSuggestion> = call {
        http.post("$BASE/places:autocomplete") {
            contentType(ContentType.Application.Json)
            header(API_KEY_HEADER, apiKey)
            setBody(AutocompleteRequest(input, sessionToken))
        }.body<AutocompleteResponse>().suggestions.mapNotNull { s ->
            val p = s.placePrediction ?: return@mapNotNull null
            PlaceSuggestion(
                placeId = p.placeId,
                primaryText = p.structuredFormat?.mainText?.text?.ifBlank { null } ?: p.text.text,
                secondaryText = p.structuredFormat?.secondaryText?.text.orEmpty(),
            )
        }
    }

    /**
     * Fetches the minimal details for [placeId], closing the session [sessionToken].
     *
     * Complexity:
     * - **Time:** O(B) where B is the response byte payload size for the requested field mask.
     * - **Space:** O(1) auxiliary allocation (single [Place] model).
     */
    suspend fun details(placeId: String, sessionToken: String): Place = call {
        http.get("$BASE/places/$placeId") {
            header(API_KEY_HEADER, apiKey)
            header(FIELD_MASK_HEADER, DETAILS_FIELD_MASK)
            parameter("sessionToken", sessionToken)
        }.body()
    }

    private suspend inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: ClientRequestException) {
        val text = e.response.bodyAsText()
        val message = runCatching {
            Json.parseToJsonElement(text).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        }.getOrDefault(text.take(200))
        throw PlacesException(e.response.status.value, message)
    }

    companion object {
        const val BASE = "https://places.googleapis.com/v1"
        const val API_KEY_HEADER = "X-Goog-Api-Key"
        const val FIELD_MASK_HEADER = "X-Goog-FieldMask"
        const val DETAILS_FIELD_MASK = "id,displayName,formattedAddress,location"
    }
}
