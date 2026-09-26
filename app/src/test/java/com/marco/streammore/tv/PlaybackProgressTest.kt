package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {
    @Test
    fun halfwayEpisodeHasHalfProgress() {
        assertEquals(0.5, progressFraction(300.0, 600.0), 0.001)
        assertFalse(isWatchedProgress(progressFraction(300.0, 600.0)))
    }

    @Test
    fun ninetyFivePercentIsWatched() {
        assertTrue(isWatchedProgress(progressFraction(570.0, 600.0)))
    }

    @Test
    fun missingDurationHasNoProgress() {
        assertEquals(0.0, progressFraction(30.0, 0.0), 0.001)
    }

    @Test
    fun onlyPartiallyWatchedTitlesHaveAResumePosition() {
        assertEquals(300_000L, resumablePositionMs(300.0, 600.0, 0.5))
        assertEquals(null, resumablePositionMs(600.0, 600.0, 1.0))
    }

    @Test
    fun remainingTimeIsShownInMinutesAndHours() {
        assertEquals("25m left", remainingLabel(300.0, 1800.0))
        assertEquals("1h 42m left", remainingLabel(0.0, 6120.0))
        assertEquals("2h left", remainingLabel(0.0, 7200.0))
    }

    @Test
    fun remainingTimeFallsBackToTheWatchedFraction() {
        assertEquals("30m left", remainingLabel(null, 3600.0, 0.5))
    }

    @Test
    fun unknownOrFinishedRuntimesHaveNoRemainingLabel() {
        assertEquals(null, remainingLabel(300.0, 0.0))
        assertEquals(null, remainingLabel(300.0, null))
        assertEquals(null, remainingLabel(599.0, 600.0))
    }

    @Test
    fun watchCaptionCombinesTheEpisodeNumberAndWhatIsLeft() {
        val resumable = MediaCard(
            mediaType = "tv", tmdbId = 1, title = "The Bear",
            season = 2, episode = 4, positionSeconds = 600.0, durationSeconds = 1800.0,
        )
        assertEquals("S2E4 · 20m left", watchCaption(resumable))

        val unknownRuntime = MediaCard(mediaType = "movie", tmdbId = 2, title = "Dune", percent = 0.4)
        assertEquals(null, watchCaption(unknownRuntime))
    }

    @Test
    fun liveProgrammeProgressFollowsTheGuideWindow() {
        assertEquals(0.5, programmeProgressFraction(1_000L, 3_000L, 2_000L), 0.001)
        assertEquals(1.0, programmeProgressFraction(1_000L, 3_000L, 9_000L), 0.001)
        assertEquals(0.0, programmeProgressFraction(0L, 3_000L, 2_000L), 0.001)
        assertEquals(0.0, programmeProgressFraction(3_000L, 3_000L, 2_000L), 0.001)
    }
}
