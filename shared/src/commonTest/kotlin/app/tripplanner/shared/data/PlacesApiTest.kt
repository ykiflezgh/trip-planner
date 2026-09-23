package app.tripplanner.shared.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlacesApiTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String): PlacesApi {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return PlacesApi(http, apiKey = "test-key")
    }

    @Test
    fun autocompleteSendsKeyAndSessionTokenAndFlattensSuggestions() = runTest {
        val api = api(body = """{"suggestions":[
            {"placePrediction":{"placeId":"p1","text":{"text":"Torre de Belém, Lisbon"},
              "structuredFormat":{"mainText":{"text":"Torre de Belém"},"secondaryText":{"text":"Lisbon, Portugal"}}}},
            {"queryPrediction":{"text":{"text":"belem pastries"}}}
        ]}""")
        val out = api.autocomplete("Torre", sessionToken = "s-1")

        assertEquals(listOf(PlacesApi.PlaceSuggestion("p1", "Torre de Belém", "Lisbon, Portugal")), out)
        val req = requests.single()
        assertEquals(HttpMethod.Post, req.method)
        assertEquals("${PlacesApi.BASE}/places:autocomplete", req.url.toString())
        assertEquals("test-key", req.headers[PlacesApi.API_KEY_HEADER])
        val sent = Json.decodeFromString<PlacesApi.AutocompleteRequest>(req.body.toByteArray().decodeToString())
        assertEquals(PlacesApi.AutocompleteRequest("Torre", "s-1"), sent)
    }

    @Test
    fun detailsSendsSameSessionTokenAsQueryParamWithMinimalFieldMask() = runTest {
        val api = api(body = """{"id":"p1","displayName":{"text":"Torre de Belém"},
            "formattedAddress":"Av. Brasília, Lisboa","location":{"latitude":38.6916,"longitude":-9.216}}""")
        val place = api.details("p1", sessionToken = "s-1")

        assertEquals("Torre de Belém", place.displayName.text)
        assertEquals(38.6916, place.location.latitude)
        val req = requests.single()
        assertEquals(HttpMethod.Get, req.method)
        assertTrue(req.url.toString().startsWith("${PlacesApi.BASE}/places/p1"))
        assertEquals("s-1", req.url.parameters["sessionToken"])   // one billed session with the autocomplete
        assertEquals(PlacesApi.DETAILS_FIELD_MASK, req.headers[PlacesApi.FIELD_MASK_HEADER])
        assertEquals("test-key", req.headers[PlacesApi.API_KEY_HEADER])
    }

    @Test
    fun apiErrorsSurfaceGoogleMessage() = runTest {
        val api = api(HttpStatusCode.Forbidden, """{"error":{"code":403,"message":"Requests to this API are blocked.","status":"PERMISSION_DENIED"}}""")
        val e = assertFailsWith<PlacesApi.PlacesException> { api.autocomplete("Torre", "s-1") }
        assertEquals(403, e.status)
        assertEquals("Requests to this API are blocked.", e.message)
    }
}

private fun io.ktor.http.content.OutgoingContent.toByteArray(): ByteArray = when (this) {
    is io.ktor.http.content.OutgoingContent.ByteArrayContent -> bytes()
    is io.ktor.http.content.TextContent -> text.toByteArray()
    else -> error("unexpected body type ${this::class.simpleName}")
}
