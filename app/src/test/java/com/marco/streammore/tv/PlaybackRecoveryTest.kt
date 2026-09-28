package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
