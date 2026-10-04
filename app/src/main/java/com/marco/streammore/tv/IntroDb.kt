package com.marco.streammore.tv

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val INTRODB_MEDIA_URL = "https://api.theintrodb.org/v3/media"

internal data class IntroDbWindow(val startMs: Long, val endMs: Long)

internal data class IntroDbTimings(
    val intro: List<IntroDbWindow> = emptyList(),
    val recap: List<IntroDbWindow> = emptyList(),
)

internal data class SkipTarget(val label: String, val targetMs: Long)

/** Build the public, unauthenticated IntroDB lookup URL. */
internal fun introDbRequestUrl(
    tmdbId: Int,
    season: Int?,
    episode: Int?,
    durationMs: Long?,
): String? {
    if (tmdbId <= 0 || (season == null) != (episode == null)) return null
    if (season != null && (season <= 0 || episode == null || episode <= 0)) return null

    val params = mutableListOf("tmdb_id=${encode(tmdbId.toString())}")
    if (season != null && episode != null) {
        params += "season=${encode(season.toString())}"
        params += "episode=${encode(episode.toString())}"
    }
    durationMs?.takeIf { it > 0 }?.let { params += "duration_ms=${encode(it.toString())}" }
    return "$INTRODB_MEDIA_URL?${params.joinToString("&")}"
}

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

/** Parse milliseconds at the provider boundary; invalid or missing data is ignored. */
internal fun parseIntroDbTimings(body: String): IntroDbTimings = runCatching {
    val json = JSONObject(body)
    IntroDbTimings(
        intro = json.optJSONArray("intro").toWindows(),
        recap = json.optJSONArray("recap").toWindows(),
    )
}.getOrDefault(IntroDbTimings())

private fun JSONArray?.toWindows(): List<IntroDbWindow> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val segment = optJSONObject(index) ?: continue
            val startMs = if (!segment.has("start_ms") || segment.isNull("start_ms")) {
                0L
            } else {
                segment.optLong("start_ms", -1L)
            }
            val endMs = if (!segment.has("end_ms") || segment.isNull("end_ms")) {
                -1L
            } else {
                segment.optLong("end_ms", -1L)
            }
            if (startMs >= 0L && endMs > startMs) add(IntroDbWindow(startMs, endMs))
        }
    }
}

/** Best-effort public lookup; an unavailable provider never interrupts playback. */
internal suspend fun fetchIntroDbTimings(
    tmdbId: Int?,
    season: Int?,
    episode: Int?,
    durationMs: Long? = null,
): IntroDbTimings = withContext(Dispatchers.IO) {
    val endpoint = tmdbId?.let { introDbRequestUrl(it, season, episode, durationMs) }
        ?: return@withContext IntroDbTimings()
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4_000
            readTimeout = 4_000
            instanceFollowRedirects = true
            doInput = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Streammore-TV")
        }
        if (connection.responseCode !in 200..299) return@withContext IntroDbTimings()
        val body = connection.inputStream.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
        }
        parseIntroDbTimings(body)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        IntroDbTimings()
    } finally {
        connection?.disconnect()
    }
}

/** Choose the active timed segment, preserving the server's custom-marker override. */
internal fun chooseSkipTarget(
    positionMs: Long,
    timings: IntroDbTimings,
    customIntroEndSeconds: Long? = null,
    customRecapEndSeconds: Long? = null,
): SkipTarget? {
    if (positionMs < 0L) return null
    val positionSeconds = positionMs / 1_000.0
    var candidate = activeDatabaseTarget(timings.recap, positionMs, "Skip recap")
        ?: activeDatabaseTarget(timings.intro, positionMs, "Skip intro")

    val recapEnd = customRecapEndSeconds?.takeIf { it > 0L } ?: 0L
    val introEnd = customIntroEndSeconds?.takeIf { it > 0L } ?: 0L
    if (recapEnd > 0L) {
        if (positionSeconds > 1.0 && positionSeconds < recapEnd - 2.0) {
            return SkipTarget("Skip recap", recapEnd * 1_000L)
        }
        if (candidate?.label == "Skip recap") candidate = null
    }
    if (introEnd > 0L) {
        if (positionSeconds > maxOf(recapEnd, 1L) && positionSeconds < introEnd - 2.0) {
            return SkipTarget("Skip intro", introEnd * 1_000L)
        }
        if (candidate?.label == "Skip intro") candidate = null
    }
    return candidate
}

private fun activeDatabaseTarget(
    windows: List<IntroDbWindow>,
    positionMs: Long,
    label: String,
): SkipTarget? {
    val window = windows.firstOrNull { segment ->
        positionMs >= (segment.startMs - 2_000L).coerceAtLeast(0L) &&
            positionMs < segment.endMs - 2_000L
    } ?: return null
    return SkipTarget(label, window.endMs)
}

internal fun shouldShowSkipPrompt(
    activeTarget: SkipTarget?,
    dismissedTarget: SkipTarget?,
    nextPromptVisible: Boolean,
): Boolean = activeTarget != null && activeTarget != dismissedTarget && !nextPromptVisible
