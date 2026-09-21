package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePointTest {
    @Test
    fun showProgressSelectsTheSavedSeasonAndEpisode() {
        val point = resumePointFrom(7, 20)

        assertEquals(7, point?.season)
        assertEquals(20, point?.episode)
    }

    @Test
    fun incompleteProgressDoesNotPretendThereIsAResumePoint() {
        assertNull(resumePointFrom(7, null))
        assertNull(resumePointFrom(0, 20))
    }
}
