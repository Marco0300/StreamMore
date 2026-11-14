package com.marco.streammore.tv

// Media3 PlaybackException codes, repeated as plain constants so the recovery
// rules stay unit-testable on the JVM without an Android runtime.
internal const val ERROR_CODE_IO_UNSPECIFIED = 2000
internal const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 2001
internal const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 2002
internal const val ERROR_CODE_IO_BAD_HTTP_STATUS = 2004
internal const val ERROR_CODE_PARSING_CONTAINER_MALFORMED = 3001
internal const val ERROR_CODE_DECODER_INIT_FAILED = 4001
internal const val ERROR_CODE_DECODING_FAILED = 4003

// Xtream hosts routinely close a finished MKV a few seconds early, and Media3
// reports that as a network or container error rather than STATE_ENDED.
private val END_OF_MEDIA_ERROR_CODES = setOf(
    ERROR_CODE_IO_UNSPECIFIED,
    ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    ERROR_CODE_IO_BAD_HTTP_STATUS,
    ERROR_CODE_PARSING_CONTAINER_MALFORMED,
)

internal const val END_OF_MEDIA_WINDOW_MS = 90_000L

/**
 * True when a playback failure is really the end of the episode: a connection or
 * container error raised inside the closing window of a title whose duration is
 * known. Without this an early provider close leaves the viewer on a stuck
 * countdown with an error banner, because neither STATE_ENDED nor the countdown
 * ever fires.
 */
internal fun isEndOfMediaFailure(
    errorCode: Int,
    positionMs: Long,
    durationMs: Long,
    windowMs: Long = END_OF_MEDIA_WINDOW_MS,
): Boolean {
    if (durationMs <= 0L) return false
    if (errorCode !in END_OF_MEDIA_ERROR_CODES) return false
    val position = positionMs.coerceIn(0L, durationMs)
    return durationMs - position <= windowMs
}

/**
 * True when nothing follows this stream, so the player should hand the viewer back
 * to the page that started playback instead of sitting on a finished frame. A movie
 * and a series' final episode qualify. A trailer and a Live TV channel carry no media
 * type and keep the player, and an episode with one after it is advanced instead.
 */
internal fun shouldExitWhenFinished(mediaType: String?, hasNextEpisode: Boolean): Boolean =
    mediaType != null && !hasNextEpisode

private val PERMANENT_REASON_MARKERS = listOf(
    "not-indexed",
    "unsupported-content-kind",
    "title-required",
)

private val RETRYABLE_REASON_MARKERS = listOf(
    "all-accounts-at-capacity",
    "no-eligible-account",
    "no-sources",
    "lookup-failed",
    "xtream-failed",
    "connection",
    "timeout",
    "socket",
)

/**
 * A resolve can fail because every Xtream account that carries the title is
 * currently streaming, which clears within seconds once the previous episode's
 * connection is released. Retrying that is worthwhile; re-requesting content
 * that is simply not in the catalogue is not.
 */
internal fun isRetryableResolveReason(reason: String?): Boolean {
    val value = reason?.lowercase()?.trim().orEmpty()
    if (value.isEmpty()) return false
    if (PERMANENT_REASON_MARKERS.any { value.contains(it) }) return false
    return RETRYABLE_REASON_MARKERS.any { value.contains(it) }
}

internal fun resolveFailureMessage(reason: String?): String {
    val value = reason?.lowercase().orEmpty()
    return when {
        value.contains("all-accounts-at-capacity") ->
            "Every Xtream account is busy streaming — press OK to try again"
        value.contains("no-eligible-account") ->
            "No Xtream account could serve this episode right now — press OK to try again"
        value.contains("not-indexed") ->
            "This episode is not in the Xtream catalogue"
        value.contains("unsupported-content-kind") || value.contains("title-required") ->
            "This title cannot be resolved"
        else ->
            "Could not start this episode — press OK to try again"
    }
}

// How long a source gets to start playing before the player moves on. Ten seconds
// covers an Xtream handshake and the first buffered segment, and is short enough
// that a viewer does not sit on a black screen wondering whether to press Back.
internal const val PLAYBACK_START_TIMEOUT_MS = 10_000L

/**
 * True when the source the viewer is on has had its chance: the player was handed
 * it, the start window has passed, and it is still not playing. Then the next
 * source the server ranked takes over — which is how a profile's preferred backend
 * failing still ends in playback instead of an error.
 *
 * A player the viewer paused is their decision, not a broken source, and a stream
 * with nothing after it has nowhere to fall back to, so neither one switches.
 */
internal fun shouldTryNextSource(
    elapsedMs: Long,
    hasStartedPlayback: Boolean,
    userPaused: Boolean,
    hasNextSource: Boolean,
    timeoutMs: Long = PLAYBACK_START_TIMEOUT_MS,
): Boolean = hasNextSource && !hasStartedPlayback && !userPaused && elapsedMs >= timeoutMs

/**
 * Where the fallback source resumes after the one in use fails outright. The failed
 * player's own position wins once it got further than the stored resume point, so a
 * film that died twenty minutes in continues from there; before that, the resume
 * point stands, so a film picked up at 30 minutes does not restart from the top on
 * the next source.
 */
internal fun resumePositionForFallback(playerPositionMs: Long, initialPositionMs: Long?): Long =
    maxOf(playerPositionMs.coerceAtLeast(0L), initialPositionMs?.coerceAtLeast(0L) ?: 0L)

/**
 * The source after the one playing, in the order the server ranked them — a
 * profile's preferred backend leads that list, so this is the next-best link.
 * Null on the last entry, which ends the chain rather than looping back onto a
 * source that has already failed, and null when the playing URL is not in the
 * list at all (a Live TV or trailer source), because the position is then unknown
 * and switching could restart something the viewer is already watching.
 */
internal fun nextSourceAfter(sources: List<StreamSource>, currentUrl: String): StreamSource? {
    val index = sources.indexOfFirst { it.tvUrl() == currentUrl }
    if (index < 0) return null
    return sources.getOrNull(index + 1)
}
