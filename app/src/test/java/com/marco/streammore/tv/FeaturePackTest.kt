package com.marco.streammore.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeaturePackTest {

    private fun track(language: String) = SubtitleTrack("label", language, "https://x/$language.vtt")

    @Test
    fun languageCodesAreNormalised() {
        assertEquals("eng", normalizedLanguage(" ENG "))
        assertNull(normalizedLanguage("und"))
        assertNull(normalizedLanguage(""))
        assertNull(normalizedLanguage(null))
    }

    @Test
    fun savedSubtitleIsOnlyUsedWhenTheTitleOffersIt() {
        val offered = listOf(track("eng"), track("spa"))
        assertEquals("spa", resolveSubtitlePreference("SPA", offered))
        assertNull(resolveSubtitlePreference("fra", offered))
        assertNull(resolveSubtitlePreference(null, offered))
        assertNull(resolveSubtitlePreference("eng", emptyList()))
    }

    @Test
    fun preferencesAreKeptPerProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("streammore_playback_prefs", 0).edit().clear().commit()
        val store = PlaybackPrefsStore(context)
        store.setAudioLanguage("kid", "spa")
        store.setSubtitleLanguage("kid", "eng")
        store.setAudioLanguage("dad", "fra")
        assertEquals(PlaybackPrefs("spa", "eng"), store.get("kid"))
        assertEquals(PlaybackPrefs("fra", null), store.get("dad"))
        assertEquals(PlaybackPrefs(), store.get("nobody"))

        store.setSubtitleLanguage("kid", null)
        assertNull(store.get("kid").subtitleLanguage)
        assertEquals("spa", store.get("kid").audioLanguage)
    }

    @Test
    fun lowStorageIsDetectedAgainstTheInstallThreshold() {
        val mb = 1024L * 1024
        // The TV that failed this update: 217 MB free of a 4 GB partition.
        assertTrue(isStorageLow(217 * mb, 4096 * mb))
        // After the clean-up: 574 MB free.
        assertFalse(isStorageLow(574 * mb, 4096 * mb))
        assertEquals("217 MB", formatStorageSize(217 * mb))
        assertEquals("4.0 GB", formatStorageSize(4096 * mb))
    }

    @Test
    fun markWatchedWritesAFinishedPositionThenClearsTheLiveEntry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requests = CopyOnWriteArrayList<Pair<String, String>>()
        val server = ServerSocket(0)
        val worker = thread(isDaemon = true) {
            repeat(2) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val requestLine = reader.readLine()
                    var length = 0
                    while (true) {
                        val header = reader.readLine()
                        if (header.isNullOrEmpty()) break
                        if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(':').trim().toInt()
                    }
                    val body = CharArray(length).also { reader.read(it, 0, length) }.concatToString()
                    requests += requestLine to body
                    val reply = """{"ok":true}"""
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: ${reply.length}\r\nConnection: close\r\n\r\n$reply".toByteArray())
                        flush()
                    }
                }
            }
        }
        try {
            val api = StreammoreApi(context, "http://127.0.0.1:${server.localPort}")
            api.markWatched("p1", MediaCard("movie", 603, "The Matrix", "poster.jpg", null, "1999", null))
            worker.join(5_000)
        } finally {
            server.close()
        }

        assertEquals(2, requests.size)
        val (progressLine, progressBody) = requests[0]
        assertTrue(progressLine, progressLine.startsWith("POST /api/progress"))
        val json = JSONObject(progressBody)
        assertEquals(603, json.getInt("tmdbId"))
        assertEquals("movie", json.getString("mediaType"))
        assertEquals(json.getDouble("duration"), json.getDouble("position"), 0.0)
        assertTrue(json.getDouble("duration") > 0)
        val (clearLine, _) = requests[1]
        assertTrue(clearLine, clearLine.startsWith("DELETE /api/watching"))
        assertTrue(clearLine, clearLine.contains("playbackId=" + json.getString("playbackId")))
    }
}
