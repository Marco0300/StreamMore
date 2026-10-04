package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroDbTest {
    @Test
    fun episodeLookupUrlIncludesCoordinatesAndOptionalRuntimeWithoutCredentials() {
        assertEquals(
            "https://api.theintrodb.org/v3/media?tmdb_id=1396&season=1&episode=1&duration_ms=3600000",
            introDbRequestUrl(tmdbId = 1396, season = 1, episode = 1, durationMs = 3_600_000),
        )
        assertEquals(
            "https://api.theintrodb.org/v3/media?tmdb_id=603",
            introDbRequestUrl(tmdbId = 603, season = null, episode = null, durationMs = null),
        )
        assertNull(introDbRequestUrl(tmdbId = 0, season = null, episode = null, durationMs = null))
        assertNull(introDbRequestUrl(tmdbId = 1396, season = 1, episode = null, durationMs = null))
    }

    @Test
    fun responseConvertsIntroAndRecapMillisecondsAndDropsInvalidRanges() {
        val parsed = parseIntroDbTimings(
            """{"intro":[{"start_ms":228664,"end_ms":246143},{"start_ms":90,"end_ms":50}],"recap":[{"start_ms":null,"end_ms":20000},{"start_ms":5,"end_ms":null}]}""",
        )

        assertEquals(
            IntroDbTimings(
                intro = listOf(IntroDbWindow(228_664L, 246_143L)),
                recap = listOf(IntroDbWindow(0L, 20_000L)),
            ),
            parsed,
        )
    }

    @Test
    fun skipTargetIsAvailableOnlyAroundItsTimedSegmentAndRecapWinsOverlap() {
        val timings = IntroDbTimings(
            intro = listOf(IntroDbWindow(20_000L, 60_000L)),
            recap = listOf(IntroDbWindow(0L, 30_000L)),
        )

        assertEquals(SkipTarget("Skip recap", 30_000L), chooseSkipTarget(10_000L, timings))
        assertEquals(SkipTarget("Skip intro", 60_000L), chooseSkipTarget(43_000L, timings.copy(recap = emptyList())))
        assertNull(chooseSkipTarget(17_999L, timings.copy(recap = emptyList())))
        assertNull(chooseSkipTarget(58_000L, timings.copy(recap = emptyList())))
    }

    @Test
    fun customPerShowMarkersOverrideOnlyTheirMatchingIntroDbSegment() {
        val timings = IntroDbTimings(
            intro = listOf(IntroDbWindow(25_000L, 62_000L)),
            recap = listOf(IntroDbWindow(0L, 24_000L)),
        )

        assertEquals(
            SkipTarget("Skip recap", 18_000L),
            chooseSkipTarget(8_000L, timings, customRecapEndSeconds = 18L),
        )
        assertNull(chooseSkipTarget(40_000L, timings, customIntroEndSeconds = 22L))
        assertEquals(
            SkipTarget("Skip intro", 80_000L),
            chooseSkipTarget(70_000L, timings, customIntroEndSeconds = 80L),
        )
    }

    @Test
    fun skipPromptIsSuppressedAfterDismissalAndWhileUpNextOwnsFocus() {
        val target = SkipTarget("Skip intro", 246_143L)

        assertTrue(shouldShowSkipPrompt(target, dismissedTarget = null, nextPromptVisible = false))
        assertFalse(shouldShowSkipPrompt(target, dismissedTarget = target, nextPromptVisible = false))
        assertFalse(shouldShowSkipPrompt(target, dismissedTarget = null, nextPromptVisible = true))
        assertFalse(shouldShowSkipPrompt(null, dismissedTarget = null, nextPromptVisible = false))
    }
}
