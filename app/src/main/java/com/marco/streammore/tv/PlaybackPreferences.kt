package com.marco.streammore.tv

import android.content.Context

/**
 * Audio and subtitle language a profile picked last time, so the next title starts
 * the same way. A null subtitle language means subtitles were off (or never chosen).
 */
internal data class PlaybackPrefs(val audioLanguage: String? = null, val subtitleLanguage: String? = null)

/** Lower-cases a language code and drops the "undetermined" placeholder. */
internal fun normalizedLanguage(code: String?): String? =
    code?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "und" }

/**
 * The subtitle language to start with: the saved one, but only when this title
 * actually offers it. Returns the track's own spelling so ExoPlayer can match it.
 */
internal fun resolveSubtitlePreference(preferred: String?, available: List<SubtitleTrack>): String? {
    val wanted = normalizedLanguage(preferred) ?: return null
    return available.firstOrNull { normalizedLanguage(it.language) == wanted }?.language
}

internal class PlaybackPrefsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("streammore_playback_prefs", Context.MODE_PRIVATE)

    fun get(profileId: String): PlaybackPrefs = PlaybackPrefs(
        audioLanguage = prefs.getString(key(profileId, "audio"), null),
        subtitleLanguage = prefs.getString(key(profileId, "subtitle"), null),
    )

    fun setAudioLanguage(profileId: String, language: String?) = put(key(profileId, "audio"), normalizedLanguage(language))

    fun setSubtitleLanguage(profileId: String, language: String?) = put(key(profileId, "subtitle"), normalizedLanguage(language))

    private fun put(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    private fun key(profileId: String, field: String) = "$profileId:$field"
}
