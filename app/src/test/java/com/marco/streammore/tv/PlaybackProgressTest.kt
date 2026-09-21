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
}
