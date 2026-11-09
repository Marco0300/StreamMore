package com.marco.streammore.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Proves the four new endpoint groups hit exactly the paths, methods, query
 * parameters and bodies the browser uses. Mirrors DeviceAuthApiTest: a throwaway
 * loopback server, no accounts and no real backend.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewEndpointsApiTest {

    private data class Captured(val method: String, val path: String, val body: String) {
        val line: String get() = "$method $path"
    }

    private fun withServer(
        routes: Map<String, Pair<Int, String>>,
        block: suspend (StreammoreApi, List<Captured>) -> Unit,
    ) = runBlocking {
        val captured = mutableListOf<Captured>()
        val server = ServerSocket(0)
        server.soTimeout = 8_000
        val worker = thread(isDaemon = true) {
            while (true) {
                val socket: Socket = try {
                    server.accept()
                } catch (_: Exception) {
                    return@thread
                }
                socket.use { handle(it, routes, captured) }
            }
        }
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val api = StreammoreApi(context, "http://127.0.0.1:${server.localPort}")
            block(api, captured)
        } finally {
            runCatching { server.close() }
            worker.join(2_000)
        }
    }

    private fun handle(socket: Socket, routes: Map<String, Pair<Int, String>>, captured: MutableList<Captured>) {
        val reader = socket.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        val method = parts.getOrElse(0) { "" }
        val path = parts.getOrElse(1) { "" }
        val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }
            .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = if (length > 0) {
            val chars = CharArray(length)
            var offset = 0
            while (offset < chars.size) {
                val read = reader.read(chars, offset, chars.size - offset)
                if (read < 0) break
                offset += read
            }
            String(chars, 0, offset)
        } else ""
        synchronized(captured) { captured.add(Captured(method, path, body)) }
        val (status, payload) = routes["$method $path"]
            ?: (500 to """{"error":"unexpected $method $path"}""")
        val bytes = payload.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().apply {
            write(
                (
                    "HTTP/1.1 $status OK\r\nContent-Type: application/json\r\n" +
                        "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    ).toByteArray(Charsets.UTF_8),
            )
            write(bytes)
            flush()
        }
    }

    private val calendarPayload = """
        {
          "days":[
            {"date":"2026-11-04","dayIndex":0,"items":[
              {"tmdbId":1396,"title":"Breaking Bad","poster":"https://image.tmdb.org/t/p/w500/bb.jpg","year":"2008","rating":8.9,
               "episode":{"season":5,"episode":16,"airDate":"2026-11-04","name":"Felina","dayIndex":0,"aired":false}}]}
          ],
          "recent":[],
          "counts":{"checked":1,"upcoming":1,"today":1,"recent":0},
          "profileId":"p1","followed":1,"fetched":1,"cached":true
        }
    """.trimIndent()

    private val personPayload = """
        {"personId":287,"name":"Brad Pitt","biography":"Actor.","profile":"https://image.tmdb.org/t/p/w300/brad.jpg",
         "knownFor":"Acting","birthday":"1963-12-18","placeOfBirth":"Shawnee, Oklahoma, USA",
         "credits":{"movie":[{"mediaType":"movie","tmdbId":550,"title":"Fight Club","year":"1999","rating":8.4}],
                    "tv":[{"mediaType":"tv","tmdbId":1396,"title":"Breaking Bad","year":"2008","rating":9.5}]}}
    """.trimIndent()

    private val profilesPayload = """
        {"profiles":[
           {"id":"p1","name":"Marco","avatar":"fox","kids":false,"hasPin":false,"prefs":{"source":"auto"}},
           {"id":"p2","name":"Kids","avatar":"kid","kids":true,"hasPin":true,"prefs":{"source":"cinepro"}}
         ],
         "sources":[
           {"key":"auto","label":"Automatic","off":false},
           {"key":"flyx","label":"Flyx add-on","off":false},
           {"key":"cinepro","label":"CinePro","off":false},
           {"key":"xtream","label":"Xtream panel","off":true},
           {"key":"streamline","label":"Streamline","off":false}
         ]}
    """.trimIndent()

    private val detailWithCollection = """
        {"mediaType":"movie","tmdbId":693134,"title":"Dune: Part Two","overview":"Paul Atreides.",
         "collection":{"id":726871,"name":"Dune Collection","overview":"The saga.","poster":null,
           "parts":[{"mediaType":"movie","tmdbId":438631,"title":"Dune","year":"2021","rating":7.8,"releaseDate":"2021-09-15"},
                    {"mediaType":"movie","tmdbId":693134,"title":"Dune: Part Two","year":"2024","rating":8.2,"releaseDate":"2024-02-27"}]}}
    """.trimIndent()

    @Test
    fun calendarUsesTheProfileQueryAndParsesThePayload() = withServer(
        mapOf("GET /api/calendar?profileId=p1" to (200 to calendarPayload)),
    ) { api, captured ->
        val data = api.calendar("p1")

        assertEquals(listOf("GET /api/calendar?profileId=p1"), captured.map { it.line })
        assertEquals(1, data.days.size)
        assertEquals("Breaking Bad", data.days[0].items[0].title)
        assertTrue(data.cached)
    }

    @Test
    fun liveFavouritesGetPostAndDeleteUseTheExactContract() = withServer(
        mapOf(
            "GET /api/livetv/favourites?profileId=p1" to (200 to """{"channelIds":["12","34"]}"""),
            "POST /api/livetv/favourites" to (200 to """{"added":true,"channelIds":["12","34","56"]}"""),
            "DELETE /api/livetv/favourites/34?profileId=p1" to (200 to """{"ok":true,"channelIds":["12"]}"""),
        ),
    ) { api, captured ->
        assertEquals(listOf("12", "34"), api.liveFavourites("p1"))
        assertEquals(listOf("12", "34", "56"), api.addLiveFavourite("p1", "56"))
        assertEquals(listOf("12"), api.removeLiveFavourite("p1", "34"))

        assertEquals(
            listOf(
                "GET /api/livetv/favourites?profileId=p1",
                "POST /api/livetv/favourites",
                "DELETE /api/livetv/favourites/34?profileId=p1",
            ),
            captured.map { it.line },
        )
        val post = JSONObject(captured[1].body)
        assertEquals(2, post.length())
        assertEquals("p1", post.getString("profileId"))
        assertEquals("56", post.getString("channelId"))
    }

    @Test
    fun aChannelMissingFromTheCatalogueSurfacesAsA404WithoutCrashing() = withServer(
        mapOf("POST /api/livetv/favourites" to (404 to """{"error":"Unknown channel"}""")),
    ) { api, _ ->
        val failure = runCatching { api.addLiveFavourite("p1", "999") }.exceptionOrNull()

        assertTrue("expected ApiException, got $failure", failure is ApiException)
        assertEquals(404, (failure as ApiException).status)
        assertEquals("Unknown channel", failure.message)
    }

    @Test
    fun personUsesTheIdPathAndParsesCredits() = withServer(
        mapOf("GET /api/person/287?profileId=p1" to (200 to personPayload)),
    ) { api, captured ->
        val person = api.person(287, "p1")

        assertEquals(listOf("GET /api/person/287?profileId=p1"), captured.map { it.line })
        assertEquals("Brad Pitt", person.name)
        assertEquals(listOf("Fight Club"), person.movies.map { it.title })
        assertEquals(listOf("Breaking Bad"), person.shows.map { it.title })
    }

    @Test
    fun movieDetailCarriesTheCollectionThrough() = withServer(
        mapOf("GET /api/title/movie/693134?profileId=p1" to (200 to detailWithCollection)),
    ) { api, captured ->
        val detail = api.detail("movie", 693134, "p1")

        assertEquals(listOf("GET /api/title/movie/693134?profileId=p1"), captured.map { it.line })
        assertEquals("Dune Collection", detail.collection?.name)
        assertEquals(listOf("Dune", "Dune: Part Two"), detail.collection?.parts?.map { it.title })
    }

    @Test
    fun aTitleWithoutACollectionLeavesItNull() = withServer(
        mapOf("GET /api/title/movie/1?profileId=p1" to (200 to """{"mediaType":"movie","tmdbId":1,"title":"Solo","overview":""}""")),
    ) { api, _ ->
        assertEquals(null, api.detail("movie", 1, "p1").collection)
    }

    @Test
    fun profilesCarryTheirOwnSourceChoiceAndTheServersSourceOptions() = withServer(
        mapOf("GET /api/profiles" to (200 to profilesPayload)),
    ) { api, captured ->
        val payload = api.profilesPayload()

        assertEquals(listOf("GET /api/profiles"), captured.map { it.line })
        assertEquals(listOf("p1", "p2"), payload.profiles.map { it.id })
        assertEquals("auto", payload.profiles[0].preferredSource)
        assertEquals("cinepro", payload.profiles[1].preferredSource)
        assertEquals(
            listOf("auto", "flyx", "cinepro", "xtream", "streamline"),
            payload.sources.map { it.key },
        )
        assertEquals("CinePro", payload.sources[2].label)
        assertTrue("the deployment switched xtream off", payload.sources[3].off)
    }

    @Test
    fun savingAPreferredSourcePatchesTheProfileAndReadsTheValueBack() = withServer(
        mapOf(
            "PATCH /api/profiles/p2" to (
                200 to """{"id":"p2","name":"Kids","kids":true,"hasPin":true,"prefs":{"source":"cinepro"}}"""
                ),
        ),
    ) { api, captured ->
        val saved = api.updatePreferredSource("p2", "cinepro")

        assertEquals("cinepro", saved)
        assertEquals(listOf("PATCH /api/profiles/p2"), captured.map { it.line })
        assertEquals("""{"prefs":{"source":"cinepro"}}""", captured.single().body)
    }
}