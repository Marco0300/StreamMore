package com.marco.streammore.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Wall clock and finish times ─────────────────────────────────────────────
//
// A viewer sitting three metres from the screen should not have to do arithmetic:
// the detail page answers "if I press Play now, what time is it over?" and the
// player answers the same question above the seek bar. Everything here reads the
// device's wall clock, so the reading always matches the clock in the room.

/** The zone the on-screen clock uses. Re-read rather than cached, so a device that
 *  changes timezone (or comes back from standby with a new one) is not stuck. */
internal fun clockZone(): ZoneId = ZoneId.systemDefault()

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "21:45" — the same shape the Live TV programme times use. */
internal fun formatWallClock(epochMs: Long, zone: ZoneId = clockZone()): String =
    CLOCK_FORMAT.withZone(zone).format(Instant.ofEpochMilli(epochMs))

/** A TMDB runtime is in minutes; anything that is not a positive length is unknown. */
internal fun runtimeMs(runtimeMinutes: Int?): Long? =
    runtimeMinutes?.takeIf { it > 0 }?.let { it * 60_000L }

/**
 * The instant a title finishes, or null when its runtime is unknown.
 *
 * A partially watched title only has its remaining part left, so the position is
 * subtracted: resuming a 167 minute film 62 minutes in finishes 105 minutes from
 * now, not 167.
 */
internal fun finishEpochMs(nowMs: Long, runtimeMinutes: Int?, positionMs: Long = 0L): Long? {
    val total = runtimeMs(runtimeMinutes) ?: return null
    val remaining = (total - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    return nowMs + remaining
}

/**
 * "Ends at 21:45 · 1h 32m left" for a title of a known runtime, or null when the
 * runtime is unknown so the caller can leave the line out entirely rather than
 * print a placeholder. The remaining half is dropped inside the last minute.
 */
internal fun finishCaption(
    finishMs: Long?,
    remainingFromMs: Long,
    remainingToMs: Long,
    zone: ZoneId = clockZone(),
): String? {
    if (finishMs == null || remainingToMs <= 0L) return null
    val endsAt = "Ends at ${formatWallClock(finishMs, zone)}"
    val left = remainingLabel((remainingFromMs / 1000L).toDouble(), (remainingToMs / 1000L).toDouble())
    return listOfNotNull(endsAt, left).joinToString(" · ")
}

/**
 * The caption for a title on a detail page, where only a runtime in minutes is
 * known.
 */
internal fun runtimeFinishCaption(
    nowMs: Long,
    runtimeMinutes: Int?,
    positionMs: Long = 0L,
    zone: ZoneId = clockZone(),
): String? {
    val total = runtimeMs(runtimeMinutes) ?: return null
    return finishCaption(
        finishMs = finishEpochMs(nowMs, runtimeMinutes, positionMs),
        remainingFromMs = positionMs,
        remainingToMs = total,
        zone = zone,
    )
}

/**
 * The caption for the player, where Media3 knows the real duration of the stream
 * — for an Xtream remux that can differ from the catalogue runtime, and it is the
 * player's figure that the seek bar is drawn from.
 */
internal fun playbackFinishCaption(
    nowMs: Long,
    positionMs: Long,
    durationMs: Long,
    zone: ZoneId = clockZone(),
): String? {
    if (durationMs <= 0L) return null
    return finishCaption(
        finishMs = nowMs + (durationMs - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L),
        remainingFromMs = positionMs,
        remainingToMs = durationMs,
        zone = zone,
    )
}

/**
 * The episode the detail page's Play action would start, which is what its finish
 * time must describe.
 *
 * This deliberately mirrors the precedence in the Play handler: the saved resume
 * point when it belongs to the season on screen, otherwise that season's first
 * episode; and an already finished episode advances to the next one, because that
 * is what Play does. When the next episode lives in another season its runtime is
 * not known here, so the finished episode is described instead of guessing.
 */
internal fun playTargetEpisode(
    detail: TitleDetail,
    episodes: List<Episode>,
    selectedSeason: Int,
): Episode? {
    if (episodes.isEmpty()) return null
    val resumeEpisode = detail.resumeEpisode
    if (resumeEpisode == null || detail.resumeSeason != selectedSeason) return episodes.firstOrNull()
    val resumeTarget = episodes.firstOrNull { it.number == resumeEpisode } ?: return episodes.firstOrNull()
    if (isWatchedProgress(detail.progress) && !detail.resumeNext) {
        return episodes.firstOrNull { it.number == resumeTarget.number + 1 } ?: resumeTarget
    }
    return resumeTarget
}

/**
 * "Ends at 21:45 · 41m left" for whatever the detail page would play now: a movie
 * at its saved position, or the episode Resume/Play would open. Null when no
 * runtime is known, and for anything that is not a title (a trailer has no
 * runtime and no finish time worth promising).
 */
internal fun detailFinishCaption(
    detail: TitleDetail,
    episodes: List<Episode>,
    selectedSeason: Int,
    nowMs: Long,
    zone: ZoneId = clockZone(),
): String? {
    if (detail.mediaType != "tv") {
        return runtimeFinishCaption(nowMs, detail.runtime, detail.resumePositionMs ?: 0L, zone)
    }
    val target = playTargetEpisode(detail, episodes, selectedSeason)
    // Only the episode that is actually half watched contributes a position; the
    // next episode starts from its beginning.
    val resumingTarget = target != null &&
        target.number == detail.resumeEpisode &&
        detail.resumeSeason == selectedSeason
    val positionMs = if (resumingTarget) target!!.positionMs else 0L
    // A series advertises an average episode length, which is the honest fallback
    // when TMDB has no runtime for the individual episode.
    return runtimeFinishCaption(nowMs, target?.runtime ?: detail.runtime, positionMs, zone)
}

/**
 * The current time as epoch milliseconds, refreshed on the minute boundary.
 *
 * Every label above is a function of "now", so they have to be handed a reading
 * that moves: the clock in the corner alone would be enough for a per-second
 * ticker, but a finish time only changes on the minute and a viewer reading
 * "21:45" does not want the frame redrawn sixty times a minute for it.
 */
@Composable
internal fun rememberWallClockMs(): Long {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            // Align to the next minute so the displayed minute flips promptly.
            delay(60_000L - (nowMs % 60_000L) + 200L)
        }
    }
    return nowMs
}

/** The current wall-clock time, for the corner of the screen. */
@Composable
internal fun rememberWallClock(): String = formatWallClock(rememberWallClockMs())
