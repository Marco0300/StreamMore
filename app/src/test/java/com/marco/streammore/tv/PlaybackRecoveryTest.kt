package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An Xtream episode that reaches its end must advance even when the provider
 * closes the file a few seconds early, and a resolve that fails because every
 * Xtream account is mid-stream must be retried rather than silently dropped.
 */
class PlaybackRecoveryTest {
    private val duration = 3_267_000L

    @Test
    fun anEarlyCloseAtTheEndOfTheEpisodeCountsAsFinished() {
        assertTrue(isEndOfMediaFailure(ERROR_CODE_IO_UNSPECIFIED, duration - 4_000, duration))
        assertTrue(isEndOfMediaFailure(ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, duration - 40_000, duration))
        assertTrue(isEndOfMediaFailure(ERROR_CODE_PARSING_CONTAINER_MALFORMED, duration - 200, duration))
    }

    @Test
    fun aNetworkFailureInTheMiddleOfAnEpisodeStaysAnError() {
        assertFalse(isEndOfMediaFailure(ERROR_CODE_IO_UNSPECIFIED, duration / 2, duration))
        assertFalse(isEndOfMediaFailure(ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, 60_000, duration))
    }

    @Test
    fun decoderFailuresAreNeverMistakenForTheEndOfAnEpisode() {
        assertFalse(isEndOfMediaFailure(ERROR_CODE_DECODER_INIT_FAILED, duration - 500, duration))
        assertFalse(isEndOfMediaFailure(ERROR_CODE_DECODING_FAILED, duration - 500, duration))
    }

    @Test
    fun anUnknownDurationCannotBeTreatedAsAFinishedEpisode() {
        assertFalse(isEndOfMediaFailure(ERROR_CODE_IO_UNSPECIFIED, 0, 0))
        assertFalse(isEndOfMediaFailure(ERROR_CODE_IO_UNSPECIFIED, 5_000, -1))
    }

    @Test
    fun theErrorWindowIsConfigurable() {
        assertTrue(isEndOfMediaFailure(ERROR_CODE_IO_UNSPECIFIED, duration - 80_000, duration, windowMs = 90_000))
        assertFalse(isEndOfMediaFailure(ERROR_CODE_IO_UNSPECIFIED, duration - 80_000, duration, windowMs = 30_000))
    }

    @Test
    fun exhaustedXtreamCapacityIsRetryable() {
        assertTrue(isRetryableResolveReason("all-accounts-at-capacity"))
        assertTrue(isRetryableResolveReason("no-eligible-account"))
        assertTrue(isRetryableResolveReason("xtream-failed: socket hang up"))
        assertTrue(isRetryableResolveReason("flyx-failed: timeout; all-accounts-at-capacity"))
    }

    @Test
    fun contentThatIsNotInTheCatalogueIsNotRetried() {
        assertFalse(isRetryableResolveReason("not-indexed"))
        assertFalse(isRetryableResolveReason("unsupported-content-kind"))
        assertFalse(isRetryableResolveReason("title-required"))
        assertFalse(isRetryableResolveReason(null))
    }

    @Test
    fun aFinishedStreamExitsOnlyWhenNothingFollowsIt() {
        // A movie, and a series' final episode, go back to the page they started from.
        assertTrue(shouldExitWhenFinished("movie", hasNextEpisode = false))
        assertTrue(shouldExitWhenFinished("tv", hasNextEpisode = false))
        // An episode with one after it is advanced instead of exited.
        assertFalse(shouldExitWhenFinished("tv", hasNextEpisode = true))
        assertFalse(shouldExitWhenFinished("movie", hasNextEpisode = true))
        // A trailer and a Live TV channel carry no media type and keep the player.
        assertFalse(shouldExitWhenFinished(null, hasNextEpisode = false))
    }

    @Test
    fun failureMessagesTellTheViewerWhatToDo() {
        val capacity = resolveFailureMessage("all-accounts-at-capacity")
        assertTrue(capacity.contains("busy", ignoreCase = true))
        assertTrue(resolveFailureMessage("not-indexed").contains("catalogue", ignoreCase = true))
        assertTrue(resolveFailureMessage("something-new").isNotBlank())
    }

    private fun source(url: String, name: String = "Flyx", native: String? = null) =
        StreamSource(name = name, quality = "1080p", url = url, nativeUrl = native)

    @Test
    fun aSourceGetsTenSecondsToStartBeforeItIsGivenUpOn() {
        assertEquals(10_000L, PLAYBACK_START_TIMEOUT_MS)
        assertTrue(shouldTryNextSource(PLAYBACK_START_TIMEOUT_MS, false, false, true))
        // Still inside the window: the player keeps waiting rather than thrashing.
        assertFalse(shouldTryNextSource(PLAYBACK_START_TIMEOUT_MS - 1, false, false, true))
    }

    @Test
    fun aSourceThatNeverStartsHandsOverToTheNextOne() {
        assertTrue(shouldTryNextSource(12_000, hasStartedPlayback = false, userPaused = false, hasNextSource = true))
    }

    @Test
    fun aSourceThatIsPlayingIsNeverSwitchedAwayFrom() {
        // The rule is about never starting, so a slow start that then plays is left alone.
        assertFalse(shouldTryNextSource(30_000, hasStartedPlayback = true, userPaused = false, hasNextSource = true))
    }

    @Test
    fun aPausedPlayerIsTheViewerDecidingRatherThanABrokenSource() {
        assertFalse(shouldTryNextSource(30_000, hasStartedPlayback = false, userPaused = true, hasNextSource = true))
    }

    @Test
    fun theLastSourceKeepsTheScreenInsteadOfLooping() {
        assertFalse(shouldTryNextSource(30_000, hasStartedPlayback = false, userPaused = false, hasNextSource = false))
    }

    @Test
    fun theFallbackIsTheNextSourceTheServerRanked() {
        // The server puts a profile's preferred backend first, so the next entry is
        // the next-best link rather than the same one again.
        val sources = listOf(
            source("http://a/1.m3u8", name = "Streamline"),
            source("http://a/2.m3u8", name = "Flyx"),
            source("http://a/3.m3u8", name = "Xtream"),
        )
        assertEquals("Flyx", nextSourceAfter(sources, "http://a/1.m3u8")?.name)
        assertEquals("Xtream", nextSourceAfter(sources, "http://a/2.m3u8")?.name)
        // Nothing after the last one, so the chain ends there.
        assertNull(nextSourceAfter(sources, "http://a/3.m3u8"))
    }

    @Test
    fun theFallbackFollowsTheUrlThePlayerActuallyUses() {
        // A source can carry both an HLS and a direct link; the player uses the
        // native one, so that is what identifies the entry it is sitting on.
        val sources = listOf(
            source("http://a/1.m3u8", name = "Flyx", native = "http://a/1.mkv"),
            source("http://a/2.m3u8", name = "Xtream"),
        )
        assertEquals("Xtream", nextSourceAfter(sources, "http://a/1.mkv")?.name)
    }

    @Test
    fun aStreamWithNothingToListKeepsPlayingInsteadOfSwitching() {
        // Live TV and trailers hand the player a single URL that was never a resolved
        // title source, so there is no position to advance from.
        assertNull(nextSourceAfter(emptyList(), "http://live/5.ts"))
        assertNull(nextSourceAfter(listOf(source("http://a/1.m3u8")), "http://live/5.ts"))
    }

    @Test
    fun aFailedSourceHandsOverWhereItStopped() {
        // A film that dies twenty minutes in continues from there on the next source.
        assertEquals(20 * 60_000L, resumePositionForFallback(20 * 60_000L, null))
        // The stored resume point stands when the failed player never got past it: the
        // viewer picked the film up at 30 minutes, so the fallback starts there.
        assertEquals(30 * 60_000L, resumePositionForFallback(0L, 30 * 60_000L))
        // Whichever is further along wins, so neither one rewinds the viewer.
        assertEquals(35 * 60_000L, resumePositionForFallback(35 * 60_000L, 30 * 60_000L))
        // Nothing known is simply the top of the film, never a negative seek.
        assertEquals(0L, resumePositionForFallback(0L, null))
        assertEquals(0L, resumePositionForFallback(-5_000L, -1L))
    }
}
