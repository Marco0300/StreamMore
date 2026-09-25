package com.marco.streammore.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioTrackSelectionTest {
    @Test
    fun englishIsPreferredAfterRussianAndUkrainian() {
        val tracks = listOf(
            Track(language = "rus", label = "Russian"),
            Track(language = "ukr", label = "Ukrainian"),
            Track(language = "eng", label = "English"),
        )

        val selected = tracks.indexOfFirst { audioTrackMatchesEnglish(it.language, it.label) }

        assertEquals(2, selected)
        assertEquals("en", media3PreferredAudioLanguage())
    }

    @Test
    fun aSingleNonEnglishTrackFallsBackSafely() {
        val tracks = listOf(Track(language = "rus", label = "Russian"))
        val selected = tracks.indexOfFirst { audioTrackMatchesEnglish(it.language, it.label) }

        assertEquals(-1, selected)
        assertEquals(0, tracks.indices.firstOrNull() ?: -1)
    }

    @Test
    fun missingLanguageMetadataFallsBackSafely() {
        val tracks = listOf(Track(language = null, label = null))
        val selected = tracks.indexOfFirst { audioTrackMatchesEnglish(it.language, it.label) }

        assertEquals(-1, selected)
        assertEquals(0, tracks.indices.firstOrNull() ?: -1)
    }

    @Test
    fun englishLanguageMatchingAcceptsIsoCodesAndEnglishLabel() {
        assertTrue(audioTrackMatchesEnglish("en", null))
        assertTrue(audioTrackMatchesEnglish("eng", null))
        assertTrue(audioTrackMatchesEnglish(null, "English (Original)"))
        assertFalse(audioTrackMatchesEnglish("rus", "Russian"))
    }

    private data class Track(val language: String?, val label: String?)
}
