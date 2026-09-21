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
    fun noNextEpisodeIsReturnedAfterTheLastEpisode() {
        val episodes = listOf(Episode(1, "Pilot"), Episode(2, "Finale"))

        assertNull(nextEpisodeAfter(episodes, season = 1, episode = 2))
    }

    @Test
    fun qualityLabelsExposeAutoAndCommonCaps() {
        assertEquals("Auto", VideoQuality.Auto.label)
        assertEquals("1080p", VideoQuality.P1080.label)
        assertEquals("720p", VideoQuality.P720.label)
        assertEquals("480p", VideoQuality.P480.label)
        assertEquals("Data Saver", VideoQuality.DataSaver.label)
    }
}
