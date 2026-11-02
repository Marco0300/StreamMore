package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a "Play Something" pick starts: the resume rule, kept out of the composable. */
class PlayPickLogicTest {

    private fun card(position: Double?) = MediaCard(
        mediaType = "movie",
        tmdbId = 438631,
        title = "Dune",
        positionSeconds = position,
    )

    @Test
    fun aPartlyWatchedPickResumesWhereItStopped() {
        assertEquals(1_234_500L, playPickPositionMs(card(1234.5)))
    }

    @Test
    fun anUnstartedPickPlaysFromTheTop() {
        assertNull(playPickPositionMs(card(null)))
        assertNull(playPickPositionMs(card(0.0)))
    }
}
