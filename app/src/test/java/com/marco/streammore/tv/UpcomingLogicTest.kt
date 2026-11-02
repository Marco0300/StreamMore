package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Upcoming page's day arithmetic and grouping. Kept pure so the labels the
 * browser renders can be asserted without a network round trip.
 */
class UpcomingLogicTest {

    private fun entry(id: Int, dayIndex: Int, title: String = "Show $id") = CalendarEntry(
        tmdbId = id,
        title = title,
        poster = "https://image.tmdb.org/t/p/w500/poster$id.jpg",
        year = "2020",
        rating = 8.0,
        status = "Returning Series",
        episode = CalendarEpisode(
            season = 1,
            episode = 2,
            airDate = "2026-11-04",
            name = "Episode Two",
            dayIndex = dayIndex,
            aired = dayIndex < 0,
        ),
    )

    @Test
    fun dayLabelsMatchTheWebCopy() {
        assertEquals("Today", calendarDayLabel("2026-11-04", 0))
        assertEquals("Tomorrow", calendarDayLabel("2026-11-05", 1))
        assertEquals("Yesterday", calendarDayLabel("2026-11-03", -1))
        assertEquals("Wednesday 4 Nov", calendarDayLabel("2026-11-04", 2))
        assertEquals("Saturday 14 Nov", calendarDayLabel("2026-11-14", 10))
    }

    @Test
    fun malformedDatesFallBackToTheRawValue() {
        assertEquals("not-a-date", calendarDayLabel("not-a-date", 5))
        assertEquals("", calendarDayLabel(null, 5))
    }

    @Test
    fun sectionsKeepServerDayOrderAndAppendJustAired() {
        val data = CalendarData(
            days = listOf(
                CalendarDay("2026-11-06", 2, listOf(entry(3, 2))),
                CalendarDay("2026-11-04", 0, listOf(entry(1, 0), entry(2, 0))),
            ),
            recent = listOf(entry(9, -2)),
            followed = 3,
        )

        val sections = upcomingSections(data)

        assertEquals(listOf("Today", "Friday 6 Nov", "Just aired"), sections.map { it.title })
        assertEquals(listOf(2, 1, 1), sections.map { it.count })
        assertEquals(listOf(false, false, true), sections.map { it.aired })
        assertEquals(1, sections.first().entries.first().tmdbId)
    }

    @Test
    fun emptyDaysAreDroppedRatherThanRenderedAsBlankSections() {
        val data = CalendarData(days = listOf(CalendarDay("2026-11-04", 0, emptyList())))
        assertTrue(upcomingSections(data).isEmpty())
    }

    @Test
    fun justAiredIsOmittedWhenThereIsNothingRecent() {
        val data = CalendarData(days = listOf(CalendarDay("2026-11-04", 0, listOf(entry(1, 0)))))
        assertEquals(listOf("Today"), upcomingSections(data).map { it.title })
    }

    @Test
    fun emptyMessageDependsOnWhetherAnythingIsFollowed() {
        assertEquals(
            "Watch a few episodes of a show and its next air dates will show up here.",
            upcomingEmptyMessage(0),
        )
        assertEquals("Nothing scheduled in the next two weeks.", upcomingEmptyMessage(2))
    }

    @Test
    fun episodeCodePadsNumbersAndSurvivesMissingData() {
        assertEquals("S05E16", episodeCode(CalendarEpisode(season = 5, episode = 16)))
        assertEquals("", episodeCode(CalendarEpisode(season = 5, episode = null)))
        assertEquals("", episodeCode(CalendarEpisode(season = null, episode = 4)))
        assertEquals("", episodeCode(null))
    }

    @Test
    fun aCalendarEntryOpensTheTvDetail() {
        val card = calendarCard(entry(1396, 0, "Breaking Bad"))
        assertEquals("tv", card.mediaType)
        assertEquals(1396, card.tmdbId)
        assertEquals("Breaking Bad", card.title)
        assertEquals("https://image.tmdb.org/t/p/w500/poster1396.jpg", card.poster)
    }

    @Test
    fun totalLabelUsesTheSingularForOneEpisode() {
        assertEquals("1 episode", calendarTotalLabel(1))
        assertEquals("3 episodes", calendarTotalLabel(3))
        assertEquals("", calendarTotalLabel(0))
    }
}