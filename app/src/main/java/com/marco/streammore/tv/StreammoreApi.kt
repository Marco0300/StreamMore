package com.marco.streammore.tv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, message: String) : Exception(message)

class StreammoreApi(
    context: Context,
    private val baseUrl: String = BuildConfig.STREAMMORE_BASE_URL,
) {
    private val sessionPrefs = context.applicationContext.getSharedPreferences("streammore_session", Context.MODE_PRIVATE)
    private var cookie: String? = sessionPrefs.getString("cookie", null)

    /** The stored session cookie, so ExoPlayer can authenticate media requests. */
    fun sessionCookie(): String? = cookie

    suspend fun me(): JSONObject = get("/api/auth/me")

    suspend fun login(email: String, password: String): JSONObject =
        post("/api/auth/login", JSONObject().apply {
            put("email", email)
            put("password", password)
        })

    suspend fun profiles(): List<Profile> = withContext(Dispatchers.IO) {
        val body = get("/api/profiles")
        val values = body.optJSONArray("profiles") ?: JSONArray()
        List(values.length()) { values.getJSONObject(it).toProfile() }
    }

    suspend fun home(profileId: String): HomeData = withContext(Dispatchers.IO) {
        val body = get("/api/home?profileId=${enc(profileId)}")
        HomeData(
            rows = parseRows(body.optJSONArray("rows") ?: JSONArray()),
            billboard = body.optJSONObject("billboard")?.toBillboard(),
            // Autoplay previews are a per-profile preference, on by default.
            autoplayPreviews = body.optJSONObject("prefs")?.optBoolean("autoplayPreviews", true) ?: true,
        )
    }

    suspend fun detail(mediaType: String, tmdbId: Int, profileId: String): TitleDetail =
        withContext(Dispatchers.IO) {
            val body = get("/api/title/$mediaType/$tmdbId?profileId=${enc(profileId)}")
            val seasonsJson = body.optJSONArray("seasons") ?: JSONArray()
            val castJson = body.optJSONArray("cast") ?: JSONArray()
            TitleDetail(
                mediaType = body.optString("mediaType", mediaType),
                tmdbId = body.optInt("tmdbId", tmdbId),
                title = body.optString("title", "Untitled"),
                overview = body.optString("overview", ""),
                backdrop = body.optString("backdrop", null),
                poster = body.optString("poster", null),
                year = body.optString("year", null),
                rating = if (body.has("rating") && !body.isNull("rating")) body.optDouble("rating") else null,
                runtime = if (body.has("runtime") && !body.isNull("runtime")) body.optInt("runtime") else null,
                tagline = body.optString("tagline", null),
                inMyList = body.optBoolean("inMyList", false),
                myRating = body.optString("myRating", null),
                progress = body.optJSONObject("progress")?.optDouble("percent", 0.0) ?: 0.0,
                seasons = List(seasonsJson.length()) {
                    val s = seasonsJson.getJSONObject(it)
                    Season(s.optInt("seasonNumber"), s.optString("name"), s.optInt("episodeCount"))
                },
                cast = List(castJson.length()) { castJson.getJSONObject(it).toPerson() },
            )
        }

    suspend fun season(tmdbId: Int, season: Int, profileId: String): List<Episode> =
        withContext(Dispatchers.IO) {
            val body = get("/api/title/tv/$tmdbId/season/$season?profileId=${enc(profileId)}")
            val episodes = body.optJSONArray("episodes") ?: JSONArray()
            List(episodes.length()) {
                val e = episodes.getJSONObject(it)
                Episode(
                    number = e.optInt("number"),
                    name = e.optString("name", "Episode ${it + 1}"),
                    overview = e.optString("overview", null),
                    still = e.optString("still", null),
                )
            }
        }

    /**
     * Resolve a title's trailer to a directly playable URL.
     *
     * TMDB only ever supplies a YouTube key, which the backend resolves. A WebView
     * YouTube embed renders as a blank surface on Android TV and never starts, so
     * the trailer is played natively instead.
     */
    suspend fun trailer(mediaType: String, tmdbId: Int): String? = withContext(Dispatchers.IO) {
        val body = get("/api/trailer/$mediaType/$tmdbId")
        body.optString("url", null)?.takeIf { it.isNotBlank() }
    }

    suspend fun streams(mediaType: String, tmdbId: Int, profileId: String, season: Int? = null, episode: Int? = null): List<StreamSource> =
        withContext(Dispatchers.IO) {
            val params = buildString {
                append("mediaType=${enc(mediaType)}&tmdbId=$tmdbId&profileId=${enc(profileId)}")
                if (season != null) append("&season=$season")
                if (episode != null) append("&episode=$episode")
            }
            val body = get("/api/streams?$params")
            val values = body.optJSONArray("streams") ?: JSONArray()
            List(values.length()) {
                val s = values.getJSONObject(it)
                StreamSource(
                    name = s.optString("name", "Source ${it + 1}"),
                    quality = s.optString("quality", "Auto"),
                    url = s.optString("url"),
                )
            }
        }

    suspend fun browse(mediaType: String, profileId: String, page: Int = 1): List<MediaCard> =
        withContext(Dispatchers.IO) {
            val body = get("/api/browse/$mediaType?profileId=${enc(profileId)}&page=$page")
            val values = body.optJSONArray("items") ?: JSONArray()
            List(values.length()) { values.getJSONObject(it).toMediaCard() }
        }

    suspend fun search(query: String, profileId: String): List<MediaCard> = withContext(Dispatchers.IO) {
        val body = get("/api/search?q=${enc(query)}&profileId=${enc(profileId)}")
        val values = body.optJSONArray("results") ?: JSONArray()
        List(values.length()) { values.getJSONObject(it).toMediaCard() }
    }

    suspend fun myList(profileId: String): List<MediaCard> = withContext(Dispatchers.IO) {
        val body = get("/api/list?profileId=${enc(profileId)}")
        val values = body.optJSONArray("items") ?: JSONArray()
        List(values.length()) { values.getJSONObject(it).toMediaCard() }
    }

    suspend fun toggleList(profileId: String, card: MediaCard): Boolean = withContext(Dispatchers.IO) {
        val body = post("/api/list", JSONObject().apply {
            put("profileId", profileId)
            put("item", JSONObject().apply {
                put("mediaType", card.mediaType)
                put("tmdbId", card.tmdbId)
                put("title", card.title)
                card.poster?.let { put("poster", it) }
                card.backdrop?.let { put("backdrop", it) }
                card.year?.let { put("year", it) }
            })
        })
        body.optBoolean("added", false)
    }

    suspend fun rate(profileId: String, mediaType: String, tmdbId: Int, value: String?): String? = withContext(Dispatchers.IO) {
        val body = post("/api/rating", JSONObject().apply {
            put("profileId", profileId)
            put("mediaType", mediaType)
            put("tmdbId", tmdbId)
            if (value == null) put("value", JSONObject.NULL) else put("value", value)
        })
        if (body.isNull("rating")) null else body.optString("rating", null)
    }

    suspend fun subtitles(mediaType: String, tmdbId: Int, season: Int? = null, episode: Int? = null): List<SubtitleTrack> = withContext(Dispatchers.IO) {
        val params = buildString {
            append("mediaType=${enc(mediaType)}&tmdbId=$tmdbId")
            if (season != null) append("&season=$season")
            if (episode != null) append("&episode=$episode")
        }
        val body = get("/api/subtitles?$params")
        val values = body.optJSONArray("subtitles") ?: body.optJSONArray("tracks") ?: JSONArray()
        List(values.length()) {
            val item = values.getJSONObject(it)
            SubtitleTrack(
                label = item.optString("label", "Subtitle"),
                language = item.optString("language", "eng"),
                url = absoluteUrl(item.optString("url")),
            )
        }
    }

    suspend fun newHot(profileId: String): List<HomeRow> = withContext(Dispatchers.IO) {
        val body = get("/api/new-hot?profileId=${enc(profileId)}")
        parseRows(body.optJSONArray("rows") ?: JSONArray())
    }

    suspend fun activity(profileId: String): List<ActivityEntry> = withContext(Dispatchers.IO) {
        val body = get("/api/activity?profileId=${enc(profileId)}")
        val values = body.optJSONArray("items") ?: JSONArray()
        List(values.length()) {
            val item = values.getJSONObject(it)
            ActivityEntry(
                mediaType = item.optString("mediaType", "movie"),
                tmdbId = item.optInt("tmdbId"),
                title = item.optString("title", "Untitled"),
                poster = item.optString("poster", null),
                percent = item.optDouble("percent", 0.0),
                season = if (item.has("season") && !item.isNull("season")) item.optInt("season") else null,
                episode = if (item.has("episode") && !item.isNull("episode")) item.optInt("episode") else null,
                updatedAt = if (item.has("updatedAt") && !item.isNull("updatedAt")) item.optLong("updatedAt") else null,
            )
        }
    }

    suspend fun liveChannels(profileId: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        val body = get("/api/livetv/channels?profileId=${enc(profileId)}")
        val values = body.optJSONArray("channels") ?: JSONArray()
        List(values.length()) {
            val item = values.getJSONObject(it)
            LiveChannel(
                id = item.optString("id"),
                channelId = item.optString("channelId", item.optString("id").substringAfterLast(':')),
                name = item.optString("name", "Channel"),
                genre = item.optString("genre", "Entertainment"),
                country = item.optString("country", "International"),
            )
        }
    }

    suspend fun liveStreams(channelId: String, profileId: String? = null): List<StreamSource> = withContext(Dispatchers.IO) {
        val suffix = profileId?.let { "?profileId=${enc(it)}" } ?: ""
        val body = get("/api/livetv/stream/${enc(channelId)}$suffix")
        val values = body.optJSONArray("streams") ?: JSONArray()
        List(values.length()) {
            val item = values.getJSONObject(it)
            StreamSource(item.optString("name", "Live"), item.optString("quality", "Live"), item.optString("url"))
        }
    }

    private fun parseRows(rows: JSONArray): List<HomeRow> = List(rows.length()) { index ->
        val row = rows.getJSONObject(index)
        val values = row.optJSONArray("items") ?: JSONArray()
        HomeRow(
            id = row.optString("id"),
            title = row.optString("title"),
            items = List(values.length()) { values.getJSONObject(it).toMediaCard() },
        )
    }

    private suspend fun get(path: String): JSONObject = request("GET", path, null)

    private suspend fun post(path: String, body: JSONObject): JSONObject = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val endpoint = baseUrl.trimEnd('/') + path
        val url = URL(endpoint)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            doInput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Streammore-TV/0.1.0")
            cookie?.let { setRequestProperty("Cookie", it) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.toString()?.toByteArray(Charsets.UTF_8)?.let { connection.outputStream.use { out -> out.write(it) } }
            val status = connection.responseCode
            Log.d(TAG, "$method $path -> HTTP $status")
            connection.headerFields.entries
                .firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                ?.value
                ?.firstOrNull()
                ?.let { setCookie ->
                    cookie = setCookie.substringBefore(';')
                    sessionPrefs.edit().putString("cookie", cookie).apply()
                }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { input -> BufferedReader(InputStreamReader(input)).readText() } ?: "{}"
            if (status !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error", text) }.getOrDefault(text)
                throw ApiException(status, message)
            }
            runCatching { JSONObject(text) }.getOrElse {
                throw ApiException(status, "Backend returned invalid JSON from $path")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (api: ApiException) {
            throw api
        } catch (failure: IOException) {
            Log.e(TAG, "$method $endpoint failed", failure)
            throw ApiException(-1, "Cannot reach Streammore backend at ${baseUrl.trimEnd('/')}: ${failure.message ?: failure.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TAG = "StreammoreApi"
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    /**
     * The backend hands back same-origin paths such as `/api/subtitles/file?subId=...`.
     * A browser resolves those against the page; ExoPlayer cannot, and fails with
     * `FileNotFoundException: /api/subtitles/file: open failed: ENOENT`.
     */
    private fun absoluteUrl(url: String): String = when {
        url.isBlank() -> url
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> baseUrl.trimEnd('/') + url
        else -> baseUrl.trimEnd('/') + "/" + url
    }
}
