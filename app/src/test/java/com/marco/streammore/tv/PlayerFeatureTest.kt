package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerFeatureTest {
    @Test
    fun nextEpisodeIsTheNextNumberInTheCurrentSeason() {
        val episodes = listOf(
            Episode(1, "Pilot"),
            Episode(2, "The Second"),
            Episode(3, "The Third"),
        )

        val next = nextEpisodeAfter(episodes, season = 1, episode = 1)

        assertEquals(NextEpisodeInfo("tv", 1, "The Second", 2), next)
    }

    @Test
    fun nextEpisodeMovesToTheFirstEpisodeOfTheNextSeason() {
        val episodes = listOf(Episode(10, "Season finale"))
        val seasons = listOf(Season(1, "Season 1", 10), Season(2, "Season 2", 8))

        val next = nextEpisodeAfter(episodes, season = 1, episode = 10, seasons = seasons)

        assertEquals(NextEpisodeInfo("tv", 2, "Episode 1", 1), next)
    }
    @Test
    fun noNextEpisodeIsReturnedAfterTheLastEpisode() {
        val episodes = listOf(Episode(1, "Pilot"), Episode(2, "Finale"))

        assertNull(nextEpisodeAfter(episodes, season = 1, episode = 2))
    }

    @Test
    fun finishedProgressUsesTheStoredNextSeasonResumePoint() {
        assertEquals(ResumePoint(2, 1), resumePointForProgress(1, 10, percent = 0.98, nextSeason = 2, nextEpisode = 1))
    }
    @Test
    fun qualityLabelsExposeAutoAndCommonCaps() {
        assertEquals("Auto", VideoQuality.Auto.label)
        assertEquals("1080p", VideoQuality.P1080.label)
        assertEquals("720p", VideoQuality.P720.label)
        assertEquals("480p", VideoQuality.P480.label)
        assertEquals("Data Saver", VideoQuality.DataSaver.label)
    }

    @Test
    fun playerTimeMatchesTheWebPlayerFormat() {
        assertEquals("0:00", formatPlayerTime(0))
        assertEquals("1:05", formatPlayerTime(65_000))
        assertEquals("1:02:03", formatPlayerTime(3_723_000))
    }
}
