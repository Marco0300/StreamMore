package com.marco.streammore.tv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardeningTest {

    @Test
    fun jsonNullAndTheTextNullBothReadAsMissing() {
        val json = JSONObject("""{"a": null, "b": "null", "c": "x", "d": ""}""")
        assertNull(json.stringOrNull("a"))
        assertNull(json.stringOrNull("b"))
        assertNull(json.stringOrNull("missing"))
        assertEquals("x", json.stringOrNull("c"))
        assertEquals("", json.stringOrNull("d"))
    }

    @Test
    fun mediaCardTreatsNullPosterAsAbsent() {
        val card = JSONObject("""{"mediaType":"movie","tmdbId":1,"title":"T","poster":null,"sub":null}""").toMediaCard()
        assertNull(card.poster)
        assertNull(card.sub)
    }

    @Test
    fun cookieOnlyGoesToTheStreammoreHost() {
        val base = "https://play.streammore.xyz"
        assertTrue(shouldSendSessionCookie("https://play.streammore.xyz/api/subtitles/file?x=1", base))
        assertTrue(shouldSendSessionCookie("https://PLAY.streammore.xyz:443/a.m3u8", base))
        assertFalse(shouldSendSessionCookie("https://cdn.example.com/a.m3u8", base))
        assertFalse(shouldSendSessionCookie("https://play.streammore.xyz.evil.com/a", base))
        assertFalse(shouldSendSessionCookie("http://play.streammore.xyz/a", base))
        assertFalse(shouldSendSessionCookie("https://play.streammore.xyz:8443/a", base))
        assertFalse(shouldSendSessionCookie("not a url", base))
    }

    @Test
    fun installNeedsTheLowStorageThresholdOnTop() {
        val mb = 1024L * 1024
        // 4 GB partition: 10% (~410 MB) is below the 500 MB cap.
        assertEquals(13 * mb + 4096 * mb / 10, requiredFreeBytes(13 * mb, 4096 * mb))
        // Large partition: capped at 500 MB.
        assertEquals(13 * mb + 500 * mb, requiredFreeBytes(13 * mb, 64 * 1024 * mb))
    }

    @Test
    fun storageMessageNamesTheShortfall() {
        val mb = 1024L * 1024
        val message = insufficientStorageMessage(430 * mb, 200 * mb)
        assertTrue(message, message.contains("230 MB"))
        assertTrue(insufficientStorageMessage(10, 9).contains("1 MB"))
    }
}
