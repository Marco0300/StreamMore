package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test

/**
 * The finish-time labels are pure functions of "now", the runtime and the saved
 * position, so they are pinned here rather than judged by eye on a television.
 */
class EndTimesTest {

    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-09-27T19:00:00Z").toEpochMilli()

    private fun movie(runtime: Int?, resumePositionMs: Long? = null) = TitleDetail(
        mediaType = "movie",
        tmdbId = 1,
        title = "Dune: Part Two",
        overview = "",
        runtime = runtime,
        resumePositionMs = resumePositionMs,
    )

    @Test
    fun `the clock is formatted as a 24 hour wall clock in the given zone`() {
        assertEquals("21:00", formatWallClock(now, ZoneOffset.ofHours(2)))
        assertEquals("19:00", formatWallClock(now, zone))
    }

    @Test
    fun `a movie with no position ends now plus its full runtime`() {
        // 19:00 + 167 minutes.
        assertEquals("Ends at 21:47 · 2h 47m left", detailFinishCaption(movie(167), emptyList(), 1, now, zone))
    }

    @Test
    fun `a resumed movie only has its remaining part left`() {
        // 62 of 167 minutes watched, so 105 remain: 19:00 + 1h45m.
        val caption = detailFinishCaption(movie(167, resumePositionMs = 62 * 60_000L), emptyList(), 1, now, zone)
        assertEquals("Ends at 20:45 · 1h 45m left", caption)
    }

    @Test
    fun `an unknown runtime produces no caption at all`() {
        assertNull(detailFinishCaption(movie(null), emptyList(), 1, now, zone))
        assertNull(detailFinishCaption(movie(0), emptyList(), 1, now, zone))
    }

    @Test
    fun `the final seconds drop the redundant remaining half`() {
        // 20 seconds of a 167 minute film left: it is over inside this minute, and a
        // "0m left" would be noise.
        val caption = detailFinishCaption(movie(167, resumePositionMs = 167 * 60_000L - 20_000L), emptyList(), 1, now, zone)
        assertEquals("Ends at 19:00", caption)
    }

    @Test
    fun `a position past the end never reports a finish in the past`() {
        val caption = detailFinishCaption(movie(100, resumePositionMs = 200 * 60_000L), emptyList(), 1, now, zone)
        assertEquals("Ends at 19:00", caption)
    }

    @Test
    fun `an episode's own runtime is used rather than the series average`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 4,
            progress = 0.3,
        )
        val episodes = listOf(Episode(3, "E3", runtime = 25), Episode(4, "E4", runtime = 41))
        // 19:00 + 41 minutes.
        assertEquals("Ends at 19:41 · 41m left", detailFinishCaption(detail, episodes, 2, now, zone))
    }

    @Test
    fun `a half watched resume episode finishes sooner`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 4,
            progress = 0.3,
        )
        val episodes = listOf(
            Episode(4, "E4", progress = 0.5, positionMs = 20 * 60_000L, runtime = 41),
        )
        // 21 of 41 minutes left: 19:00 + 21m.
        assertEquals("Ends at 19:21 · 21m left", detailFinishCaption(detail, episodes, 2, now, zone))
    }

    @Test
    fun `a series falls back to its average episode length when the episode runtime is missing`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 1,
            resumeEpisode = 1,
        )
        val episodes = listOf(Episode(1, "E1", runtime = null))
        assertEquals("Ends at 19:30 · 30m left", detailFinishCaption(detail, episodes, 1, now, zone))
    }

    @Test
    fun `a title with no runtime anywhere gets no caption`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = null,
            resumeSeason = 1,
            resumeEpisode = 1,
        )
        assertNull(detailFinishCaption(detail, listOf(Episode(1, "E1")), 1, now, zone))
    }

    @Test
    fun `another season describes its own first episode, not the saved point`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 4,
            progress = 0.3,
        )
        val seasonOne = listOf(Episode(1, "S1E1", positionMs = 10 * 60_000L, runtime = 22))
        // Season 1 is on screen, so Play would start its first episode from the top.
        assertEquals("Ends at 19:22 · 22m left", detailFinishCaption(detail, seasonOne, 1, now, zone))
    }

    @Test
    fun `the target episode is the saved resume point of the season on screen`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 4,
            progress = 0.3,
        )
        val episodes = listOf(Episode(4, "E4", runtime = 41), Episode(5, "E5", runtime = 42))
        assertEquals(4, playTargetEpisode(detail, episodes, 2)?.number)
    }

    @Test
    fun `a finished episode advances to the next one, exactly as Play does`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 4,
            progress = 0.97,
        )
        val episodes = listOf(Episode(4, "E4", progress = 1.0, watched = true), Episode(5, "E5", runtime = 42))
        assertEquals(5, playTargetEpisode(detail, episodes, 2)?.number)
        // And the caption describes that next episode from its beginning.
        assertEquals("Ends at 19:42 · 42m left", detailFinishCaption(detail, episodes, 2, now, zone))
    }

    @Test
    fun `a finished season finale describes itself when the next season is not loaded`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 4,
            progress = 0.97,
        )
        val episodes = listOf(Episode(4, "E4", progress = 1.0, watched = true, runtime = 41))
        assertEquals(4, playTargetEpisode(detail, episodes, 2)?.number)
    }

    @Test
    fun `a completed episode that the server already advanced describes the next one`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 5,
            progress = 0.97,
            resumeNext = true,
        )
        val episodes = listOf(Episode(5, "E5", runtime = 42), Episode(6, "E6", runtime = 43))
        assertEquals(5, playTargetEpisode(detail, episodes, 2)?.number)
        assertEquals("Ends at 19:42 · 42m left", detailFinishCaption(detail, episodes, 2, now, zone))
    }

    @Test
    fun `an unloaded season list still falls back to the series average`() {
        val detail = TitleDetail(
            mediaType = "tv",
            tmdbId = 2,
            title = "The Bear",
            overview = "",
            runtime = 30,
            resumeSeason = 2,
            resumeEpisode = 4,
        )
        // No episode is known, so there is no target to start; the caption still
        // uses the series' average episode length rather than showing nothing.
        assertNull(playTargetEpisode(detail, emptyList(), 2))
        assertEquals("Ends at 19:30 · 30m left", detailFinishCaption(detail, emptyList(), 2, now, zone))
    }

    @Test
    fun `the player caption follows the stream duration Media3 reports`() {
        // 30 of 60 minutes played: 19:00 + 30m.
        val caption = playbackFinishCaption(now, 30 * 60_000L, 60 * 60_000L, zone)
        assertEquals("Ends at 19:30 · 30m left", caption)
    }

    @Test
    fun `the player caption stays honest while paused and reports no duration`() {
        // A slide of the seek bar re-reads the same clock and lands on the new time.
        assertEquals("Ends at 20:00 · 1h left", playbackFinishCaption(now, 0L, 60 * 60_000L, zone))
        // Before the duration is known there is nothing to promise.
        assertNull(playbackFinishCaption(now, 0L, 0L, zone))
    }
}
