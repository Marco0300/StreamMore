package com.marco.streammore.tv

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class Profile(
    val id: String,
    val name: String,
    val avatar: String? = null,
    val kids: Boolean = false,
    val hasPin: Boolean = false,
    /** The backend this profile would rather stream from; "auto" lets the server choose. */
    val preferredSource: String = "auto",
)

/** A streaming backend this deployment offers, as `/api/profiles` advertises it. */
data class SourceOption(
    val key: String,
    val label: String,
    /** The deployment has this backend switched off, so the profile cannot pick it. */
    val off: Boolean = false,
)

/** `/api/profiles` answers both the profile list and the source options for the screen. */
data class ProfilesPayload(
    val profiles: List<Profile> = emptyList(),
    val sources: List<SourceOption> = emptyList(),
)

data class TrailerPlayback(
    val videoUrl: String,
    val audioUrl: String? = null,
)

data class MediaCard(
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val poster: String? = null,
    val backdrop: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val sub: String? = null,
    val percent: Double = 0.0,
    val ribbon: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** Episode name, sent by the backend for Continue Watching rows. */
    val episodeTitle: String? = null,
    /** Playback position in seconds, as the backend stores it. */
    val positionSeconds: Double? = null,
    /** Total runtime in seconds, as the backend stores it. */
    val durationSeconds: Double? = null,
)

data class HomeRow(
    val id: String,
    val title: String,
    val items: List<MediaCard>,
)

/**
 * The home-page hero. Mirrors the browser's `billboard` object: the backend picks a
 * title from the trending row and attaches its YouTube `trailerKey`.
 */
data class Billboard(
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val backdrop: String? = null,
    val poster: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val trailerKey: String? = null,
) {
    fun toCard(): MediaCard = MediaCard(mediaType, tmdbId, title, poster, backdrop, year, rating)
}

data class HomeData(
    val rows: List<HomeRow> = emptyList(),
    val billboard: Billboard? = null,
    /** Per-profile preference; the hero trailer honours it, on by default. */
    val autoplayPreviews: Boolean = true,
)

data class Season(
    val number: Int,
    val name: String,
    val episodes: Int,
)

data class Episode(
    val number: Int,
    val name: String,
    val overview: String? = null,
    val still: String? = null,
    val airDate: String? = null,
    val progress: Double = 0.0,
    val watched: Boolean = false,
    val positionMs: Long = 0L,
    /** TMDB's runtime for this episode in minutes; null when it is not published. */
    val runtime: Int? = null,
)

data class NextEpisodeInfo(
    val mediaType: String,
    val season: Int,
    val title: String,
    val episode: Int,
    val tmdbId: Int = 0,
)

fun nextEpisodeAfter(episodes: List<Episode>, season: Int, episode: Int, seasons: List<Season> = emptyList()): NextEpisodeInfo? {
    episodes.firstOrNull { it.number == episode + 1 }?.let {
        return NextEpisodeInfo("tv", season, it.name, it.number)
    }
    val nextSeason = seasons
        .filter { it.number > season && it.episodes > 0 }
        .minByOrNull { it.number }
    return nextSeason?.let { NextEpisodeInfo("tv", it.number, "Episode 1", 1) }
}

enum class VideoQuality(val label: String, val width: Int, val height: Int) {
    Auto("Auto", Int.MAX_VALUE, Int.MAX_VALUE),
    P1080("1080p", 1920, 1080),
    P720("720p", 1280, 720),
    P480("480p", 854, 480),
    DataSaver("Data Saver", 640, 360),
}

/**
 * A cast member. The backend sends `{ id, name, character, profile }` — it is a
 * person, not a title, so it must not be parsed as a MediaCard.
 */
data class Person(
    val id: Int,
    val name: String,
    val character: String = "",
    val profile: String? = null,
)

data class GenreOption(val id: Int, val name: String)

data class BrowsePage(
    val items: List<MediaCard> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
)

data class TitleDetail(
    val mediaType: String,
    val tmdbId: Int,
    val title: String,
    val overview: String,
    val backdrop: String? = null,
    val poster: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val runtime: Int? = null,
    val tagline: String? = null,
    val inMyList: Boolean = false,
    val myRating: String? = null,
    val progress: Double = 0.0,
    val resumePositionMs: Long? = null,
    val resumeSeason: Int? = null,
    val resumeEpisode: Int? = null,
    val resumeNext: Boolean = false,
    val releaseDate: String? = null,
    val introEndSeconds: Long? = null,
    val recapEndSeconds: Long? = null,
    val genres: List<String> = emptyList(),
        val seasons: List<Season> = emptyList(),
        val cast: List<Person> = emptyList(),
        /** TMDB franchise when the server knows one; absent for standalone titles. */
        val collection: TitleCollection? = null,
    )

internal fun formatReleaseDate(value: String?): String? {
    val parts = value?.trim()?.split('-') ?: return null
    if (parts.size != 3) return value?.takeIf { it.isNotBlank() }
    val month = parts[1].toIntOrNull()?.let {
        listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec").getOrNull(it)
    } ?: return value
    val day = parts[2].toIntOrNull() ?: return value
    return "$month $day, ${parts[0]}"
}

data class StreamSource(
    val name: String,
    val quality: String,
    val url: String,
    val backend: String? = null,
    val nativeUrl: String? = null,
    val sourceExtension: String? = null,
) {
    fun tvUrl(): String = nativeUrl?.takeIf { it.isNotBlank() } ?: url
}

/**
 * A source list plus the server's explanation when nothing could be resolved.
 * The Streammore API answers 200 with an empty list for an unplayable title, so
 * the reason is the only way to distinguish "every Xtream account is busy" from
 * "this episode is not in the catalogue".
 */
data class StreamResolution(
    val sources: List<StreamSource>,
    val reason: String? = null,
)


data class SubtitleTrack(val label: String, val language: String, val url: String)

data class LiveChannel(
    val id: String,
    val channelId: String,
    val name: String,
    val genre: String,
    val country: String,
    val nowPlaying: LiveProgram? = null,
    val nextPlaying: LiveProgram? = null,
)

data class LiveProgram(
    val id: String,
    val title: String,
    val description: String? = null,
    val startMs: Long,
    val endMs: Long,
    val isLive: Boolean = false,
)

internal fun JSONObject.toLiveProgram(): LiveProgram? {
    val start = optLong("startMs", 0L)
    val end = optLong("endMs", 0L)
    if (start <= 0L || end <= start) return null
    return LiveProgram(
        id = optString("id"),
        title = optString("title", "Untitled programme"),
        description = optString("description").takeIf { it.isNotBlank() },
        startMs = start,
        endMs = end,
        isLive = optBoolean("isLive", false),
    )
}

data class LiveSchedule(
    val available: Boolean = false,
    val reason: String? = null,
    val categories: List<LiveEventCategory> = emptyList(),
)

data class LiveEventCategory(
    val name: String,
    val icon: String = "📺",
    val events: List<LiveEvent> = emptyList(),
)

data class LiveEvent(
    val id: String,
    val time: String? = null,
    val localTime: String? = null,
    val title: String,
    val isLive: Boolean = false,
    val channelIds: List<String> = emptyList(),
)

internal fun genreNamesFrom(values: List<String?>): List<String> =
    values.filterNotNull().filter { it.isNotBlank() }

fun JSONObject.toGenreNames(): List<String> {
    val values = runCatching { getJSONArray("genres") }.getOrNull() ?: JSONArray()
    return genreNamesFrom((0 until values.length()).mapNotNull { index ->
        runCatching { values.getJSONObject(index).getString("name") }.getOrNull()
    })
}

fun JSONObject.intOrNull(name: String): Int? =
    if (has(name)) optInt(name, 0).takeIf { it > 0 } else null

/**
 * Android's org.json returns the text "null" from optString(name, null) when the
 * server sent a JSON null (the reference org.json used by the unit tests returns
 * the fallback instead), so a missing poster would load an image named "null".
 */
fun JSONObject.stringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeUnless { it == "null" } else null

fun JSONObject.doubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name).takeUnless { it.isNaN() } else null

internal fun progressFraction(position: Double?, duration: Double?): Double =
    if (position != null && duration != null && duration > 0.0) (position / duration).coerceIn(0.0, 1.0) else 0.0

internal fun resumablePositionMs(position: Double?, duration: Double?, percent: Double?): Long? {
    val fraction = percent ?: progressFraction(position, duration)
    return position?.takeIf { fraction > 0.02 && fraction < 0.95 }?.let { (it * 1000.0).toLong() }
}

internal fun isWatchedProgress(progress: Double): Boolean = progress >= 0.95

/** How far a live programme has aired, for the progress bar on a channel tile. */
internal fun programmeProgressFraction(startMs: Long, endMs: Long, nowMs: Long): Double {
    if (startMs <= 0L || endMs <= startMs) return 0.0
    return ((nowMs - startMs).toDouble() / (endMs - startMs).toDouble()).coerceIn(0.0, 1.0)
}

/**
 * "12m left" / "1h 42m left" for a title whose runtime is known, or null when it
 * is not. The position falls back to the watched fraction, because a row that
 * only carries a percentage can still say how much is left.
 */
internal fun remainingLabel(positionSeconds: Double?, durationSeconds: Double?, percent: Double = 0.0): String? {
    val duration = durationSeconds?.takeIf { it > 0.0 } ?: return null
    val position = positionSeconds ?: (percent.coerceIn(0.0, 1.0) * duration)
    val leftSeconds = duration - position
    // Under half a minute left is not worth a label.
    if (leftSeconds < 30.0) return null
    val minutes = (leftSeconds / 60.0).roundToInt().coerceAtLeast(1)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours <= 0 -> "${minutes}m left"
        rest == 0 -> "${hours}h left"
        else -> "${hours}h ${rest}m left"
    }
}

/** Caption under a Continue Watching tile, for example "S2E4 · 12m left". */
internal fun watchCaption(card: MediaCard): String? {
    val parts = buildList {
        if (card.season != null && card.episode != null) add("S${card.season}E${card.episode}")
        remainingLabel(card.positionSeconds, card.durationSeconds, card.percent)?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

data class ResumePoint(val season: Int, val episode: Int)

internal fun resumePointFrom(season: Int?, episode: Int?): ResumePoint? =
    if (season != null && season > 0 && episode != null && episode > 0) ResumePoint(season, episode) else null

internal fun resumePointForProgress(
    season: Int?,
    episode: Int?,
    percent: Double,
    nextSeason: Int? = null,
    nextEpisode: Int? = null,
): ResumePoint? =
    if (percent >= 0.95) {
        resumePointFrom(nextSeason, nextEpisode) ?: resumePointFrom(season, episode)
    } else {
        resumePointFrom(season, episode)
    }

fun JSONObject.toResumePoint(): ResumePoint? {
    val percent = doubleOrNull("percent") ?: progressFraction(doubleOrNull("position"), doubleOrNull("duration"))
    val next = optJSONObject("upNext")
    return resumePointForProgress(
        season = intOrNull("season"),
        episode = intOrNull("episode"),
        percent = percent,
        nextSeason = next?.intOrNull("season"),
        nextEpisode = next?.intOrNull("episode"),
    )
}

fun JSONObject.toMediaCard(): MediaCard = MediaCard(
    mediaType = optString("mediaType", "movie"),
    tmdbId = optInt("tmdbId"),
    title = optString("title", "Untitled"),
    poster = stringOrNull("poster"),
    backdrop = stringOrNull("backdrop"),
    year = stringOrNull("year"),
    rating = if (has("rating") && !isNull("rating")) optDouble("rating") else null,
    sub = stringOrNull("sub"),
    percent = optDouble("percent", 0.0),
    ribbon = stringOrNull("ribbon"),
    season = if (has("season") && !isNull("season")) optInt("season") else null,
    episode = if (has("episode") && !isNull("episode")) optInt("episode") else null,
    episodeTitle = stringOrNull("episodeTitle"),
    positionSeconds = if (has("position") && !isNull("position")) optDouble("position") else null,
    durationSeconds = if (has("duration") && !isNull("duration")) optDouble("duration") else null,
)

fun JSONObject.toBrowsePage(): BrowsePage {
    val values = optJSONArray("items") ?: JSONArray()
    return BrowsePage(
        items = List(values.length()) { values.getJSONObject(it).toMediaCard() },
        page = optInt("page", 1),
        totalPages = optInt("totalPages", 1),
        totalResults = optInt("totalResults", 0),
    )
}

fun JSONObject.toGenreOptions(mediaType: String): List<GenreOption> {
    val values = optJSONArray(mediaType) ?: JSONArray()
    return List(values.length()) { index ->
        val item = values.getJSONObject(index)
        GenreOption(item.optInt("id"), item.optString("name", "Genre"))
    }
}

fun JSONObject.toBillboard(): Billboard = Billboard(
    mediaType = optString("mediaType", "movie"),
    tmdbId = optInt("tmdbId"),
    title = optString("title", "Untitled"),
    overview = optString("overview", ""),
    backdrop = stringOrNull("backdrop"),
    poster = stringOrNull("poster"),
    year = stringOrNull("year"),
    rating = if (has("rating") && !isNull("rating")) optDouble("rating") else null,
    trailerKey = stringOrNull("trailerKey"),
)

fun JSONObject.toPerson(): Person = Person(
    id = optInt("id"),
    name = optString("name", "Unknown"),
    character = optString("character", ""),
    profile = stringOrNull("profile"),
)

fun JSONObject.toProfile(): Profile = Profile(
    id = optString("id"),
    name = optString("name", "Profile"),
    avatar = stringOrNull("avatar"),
    kids = optBoolean("kids", false),
    hasPin = optBoolean("hasPin", false),
    // Older servers omit prefs entirely; the default has always been automatic.
    preferredSource = toPreferredSource(),
)

/** The per-profile `prefs.source` value, from a profile object or a PATCH response. */
fun JSONObject.toPreferredSource(): String =
    optJSONObject("prefs")?.optString("source")?.takeIf { it.isNotBlank() } ?: "auto"

fun JSONObject.toSourceOptions(): List<SourceOption> {
    val values = optJSONArray("sources") ?: JSONArray()
    return List(values.length()) { index ->
        val item = values.optJSONObject(index) ?: JSONObject()
        SourceOption(
            key = item.optString("key"),
            label = item.optString("label", item.optString("key")),
            off = item.optBoolean("off", false),
        )
    }.filter { it.key.isNotBlank() }
}

fun JSONObject.toProfilesPayload(): ProfilesPayload {
    val values = optJSONArray("profiles") ?: JSONArray()
    return ProfilesPayload(
        profiles = List(values.length()) { values.getJSONObject(it).toProfile() },
        sources = toSourceOptions(),
    )
}

// ── Upcoming / air dates ─────────────────────────────────────────────────────

/** One airing, as `/api/calendar` reports it. `dayIndex` 0 is today, negative has aired. */
data class CalendarEpisode(
    val season: Int? = null,
    val episode: Int? = null,
    val airDate: String? = null,
    val name: String? = null,
    val dayIndex: Int = 0,
    val aired: Boolean = false,
)

data class CalendarEntry(
    val tmdbId: Int = 0,
    val title: String = "Untitled",
    val poster: String? = null,
    val year: String? = null,
    val rating: Double? = null,
    val status: String? = null,
    val episode: CalendarEpisode? = null,
)

data class CalendarDay(
    val date: String = "",
    val dayIndex: Int = 0,
    val items: List<CalendarEntry> = emptyList(),
)

data class CalendarCounts(
    val checked: Int = 0,
    val upcoming: Int = 0,
    val today: Int = 0,
    val recent: Int = 0,
)

data class CalendarData(
    val days: List<CalendarDay> = emptyList(),
    val recent: List<CalendarEntry> = emptyList(),
    val counts: CalendarCounts = CalendarCounts(),
    val followed: Int = 0,
    val cached: Boolean = false,
)

/** A TMDB franchise attached to a movie's detail payload. Parts are oldest first. */
data class TitleCollection(
    val id: Int = 0,
    val name: String = "",
    val overview: String? = null,
    val poster: String? = null,
    val parts: List<MediaCard> = emptyList(),
)

/** A person page: biography plus the two credit lists the browser renders. */
data class PersonDetail(
    val personId: Int = 0,
    val name: String = "Unknown",
    val biography: String = "",
    val photo: String? = null,
    val knownFor: String? = null,
    val birthday: String? = null,
    val placeOfBirth: String? = null,
    val movies: List<MediaCard> = emptyList(),
    val shows: List<MediaCard> = emptyList(),
)

fun JSONObject.toCalendarEpisode(): CalendarEpisode = CalendarEpisode(
    season = if (has("season") && !isNull("season")) optInt("season") else null,
    episode = if (has("episode") && !isNull("episode")) optInt("episode") else null,
    airDate = stringOrNull("airDate"),
    name = stringOrNull("name"),
    dayIndex = optInt("dayIndex", 0),
    aired = optBoolean("aired", false),
)

fun JSONObject.toCalendarEntry(): CalendarEntry = CalendarEntry(
    tmdbId = optInt("tmdbId"),
    title = optString("title", "Untitled"),
    poster = stringOrNull("poster"),
    year = stringOrNull("year"),
    rating = doubleOrNull("rating"),
    status = stringOrNull("status"),
    episode = optJSONObject("episode")?.toCalendarEpisode(),
)

fun JSONObject.toCalendarData(): CalendarData {
    val dayValues = optJSONArray("days") ?: JSONArray()
    val recentValues = optJSONArray("recent") ?: JSONArray()
    val counts = optJSONObject("counts")
    return CalendarData(
        days = List(dayValues.length()) { index ->
            val day = dayValues.optJSONObject(index) ?: JSONObject()
            val items = day.optJSONArray("items") ?: JSONArray()
            CalendarDay(
                date = day.optString("date", ""),
                dayIndex = day.optInt("dayIndex", 0),
                items = List(items.length()) { itemIndex ->
                    items.optJSONObject(itemIndex)?.toCalendarEntry() ?: CalendarEntry()
                },
            )
        },
        recent = List(recentValues.length()) { index ->
            recentValues.optJSONObject(index)?.toCalendarEntry() ?: CalendarEntry()
        },
        counts = CalendarCounts(
            checked = counts?.optInt("checked", 0) ?: 0,
            upcoming = counts?.optInt("upcoming", 0) ?: 0,
            today = counts?.optInt("today", 0) ?: 0,
            recent = counts?.optInt("recent", 0) ?: 0,
        ),
        followed = optInt("followed", 0),
        cached = optBoolean("cached", false),
    )
}

fun JSONObject.toCollection(): TitleCollection {
    val values = optJSONArray("parts") ?: JSONArray()
    return TitleCollection(
        id = optInt("id"),
        name = optString("name", ""),
        overview = stringOrNull("overview"),
        poster = stringOrNull("poster"),
        parts = List(values.length()) { index ->
            values.optJSONObject(index)?.toMediaCard() ?: MediaCard("movie", 0, "Untitled")
        },
    )
}

fun JSONObject.toPersonDetail(): PersonDetail {
    val credits = optJSONObject("credits")
    val movieValues = credits?.optJSONArray("movie") ?: JSONArray()
    val showValues = credits?.optJSONArray("tv") ?: JSONArray()
    return PersonDetail(
        personId = optInt("personId"),
        name = optString("name", "Unknown"),
        biography = optString("biography", ""),
        photo = stringOrNull("profile"),
        knownFor = stringOrNull("knownFor"),
        birthday = stringOrNull("birthday"),
        placeOfBirth = stringOrNull("placeOfBirth"),
        movies = List(movieValues.length()) { index ->
            movieValues.optJSONObject(index)?.toMediaCard() ?: MediaCard("movie", 0, "Untitled")
        },
        shows = List(showValues.length()) { index ->
            showValues.optJSONObject(index)?.toMediaCard() ?: MediaCard("tv", 0, "Untitled")
        },
    )
}

/** `channelIds` from the Live TV favourites routes. */
fun JSONObject.toChannelIds(): List<String> {
    val values = optJSONArray("channelIds") ?: JSONArray()
    return List(values.length()) { index -> values.optString(index) }.filter { it.isNotBlank() }
}
