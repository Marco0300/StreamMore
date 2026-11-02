package com.marco.streammore.tv

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure rules behind the Upcoming page, the Live TV favourites filter, the movie
 * franchise row and the person page. They live outside the composables so the
 * same rules the browser implements can be asserted directly.
 */

/** The browser clamps a biography to roughly this many characters before "Read more". */
internal const val BiographyClamp = 420

internal const val LiveFavouritesCategory = "Favourites"

internal data class UpcomingSection(
    val title: String,
    val count: Int,
    val entries: List<CalendarEntry>,
    val aired: Boolean,
)

/**
 * "Today" / "Tomorrow" / "Yesterday" / "Wednesday 4 Nov", matching the browser's
 * `calendarDayLabel`. An unparseable date is shown verbatim rather than dropped.
 */
internal fun calendarDayLabel(date: String?, dayIndex: Int): String = when (dayIndex) {
    0 -> "Today"
    1 -> "Tomorrow"
    -1 -> "Yesterday"
    else -> {
        val raw = date?.trim().orEmpty()
        val parsed = if (raw.length >= 10) {
            runCatching { LocalDate.parse(raw.substring(0, 10)) }.getOrNull()
        } else {
            null
        }
        parsed?.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.getDefault())) ?: raw
    }
}

/** Non-empty days in air-date order, then the "Just aired" section from `recent`. */
internal fun upcomingSections(data: CalendarData?): List<UpcomingSection> {
    if (data == null) return emptyList()
    val sections = data.days
        .filter { it.items.isNotEmpty() }
        .sortedBy { it.dayIndex }
        .map { day ->
            UpcomingSection(
                title = calendarDayLabel(day.date, day.dayIndex),
                count = day.items.size,
                entries = day.items,
                aired = day.dayIndex < 0,
            )
        }
        .toMutableList()
    if (data.recent.isNotEmpty()) {
        sections += UpcomingSection("Just aired", data.recent.size, data.recent, aired = true)
    }
    return sections
}

internal fun upcomingEmptyMessage(followed: Int): String =
    if (followed > 0) {
        "Nothing scheduled in the next two weeks."
    } else {
        "Watch a few episodes of a show and its next air dates will show up here."
    }

internal fun calendarTotalLabel(count: Int): String = when {
    count <= 0 -> ""
    count == 1 -> "1 episode"
    else -> "$count episodes"
}

internal fun episodeCode(episode: CalendarEpisode?): String {
    val season = episode?.season ?: return ""
    val number = episode.episode ?: return ""
    return "S%02dE%02d".format(season, number)
}

internal fun calendarCard(entry: CalendarEntry): MediaCard =
    MediaCard("tv", entry.tmdbId, entry.title, entry.poster, null, entry.year, entry.rating)

// ── Live TV favourites ───────────────────────────────────────────────────────

internal fun withFavourite(ids: List<String>, channelId: String, favourite: Boolean): List<String> =
    if (favourite) (ids + channelId).distinct() else ids.filterNot { it == channelId }

/** A pin may be stored either as the bare id or as the catalogue's full channel id. */
internal fun isFavourite(ids: List<String>, channel: LiveChannel): Boolean =
    ids.contains(channel.channelId) || ids.contains(channel.id)

internal fun liveCategories(channels: List<LiveChannel>): List<String> = buildList {
    add("All")
    add(LiveFavouritesCategory)
    channels.map { it.genre }.filter { it.isNotBlank() }.distinct().sorted().forEach { add(it) }
}

internal fun liveFilteredChannels(
    channels: List<LiveChannel>,
    category: String,
    search: String,
    favouriteIds: List<String>,
): List<LiveChannel> {
    val needle = search.trim()
    return channels.filter { channel ->
        val matchesCategory = when (category) {
            LiveFavouritesCategory -> isFavourite(favouriteIds, channel)
            "All" -> true
            else -> channel.genre == category
        }
        val matchesSearch = needle.isBlank() ||
            channel.name.contains(needle, ignoreCase = true) ||
            channel.genre.contains(needle, ignoreCase = true) ||
            channel.country.contains(needle, ignoreCase = true)
        matchesCategory && matchesSearch
    }
}

// ── Person page ──────────────────────────────────────────────────────────────

/** "Acting · 1963-12-18 · Shawnee, Oklahoma, USA", skipping whatever is missing. */
internal fun personFacts(person: PersonDetail?): String {
    if (person == null) return ""
    val values = mutableListOf<String>()
    person.knownFor?.trim()?.takeIf { it.isNotEmpty() }?.let { values += it }
    person.birthday?.trim()?.takeIf { it.isNotEmpty() }?.let { values += it }
    person.placeOfBirth?.trim()?.takeIf { it.isNotEmpty() }?.let { values += it }
    return values.joinToString(" · ")
}

internal fun clampBiography(text: String?, expanded: Boolean, limit: Int = BiographyClamp): String {
    val value = text?.trim().orEmpty()
    return if (expanded || value.length <= limit) value else value.take(limit) + "…"
}

internal fun personCreditCount(person: PersonDetail?): Int =
    (person?.movies?.size ?: 0) + (person?.shows?.size ?: 0)

// ── Movie franchise row ──────────────────────────────────────────────────────

/** The browser hides a "collection" that only contains the title itself. */
internal fun collectionParts(collection: TitleCollection?): List<MediaCard> {
    val parts = collection?.parts.orEmpty()
    return if (parts.size < 2) emptyList() else parts
}

internal fun collectionMeta(card: MediaCard): String {
    val values = mutableListOf<String>()
    card.year?.takeIf { it.isNotBlank() }?.let { values += it }
    card.rating?.let { values += "★ %.1f".format(it) }
    return values.joinToString(" · ")
}