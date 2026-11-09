package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Updates screen renders whatever these rows say, and the labels come from the
 * server, so the marking and the "off for this server" rule are pinned here.
 */
class PreferredSourceLogicTest {

    private val options = listOf(
        SourceOption("auto", "Automatic"),
        SourceOption("flyx", "Flyx add-on"),
        SourceOption("cinepro", "CinePro"),
        SourceOption("xtream", "Xtream panel", off = true),
    )

    @Test
    fun theAutoDefaultIsTheOnlySelectedRow() {
        val rows = preferredSourceRows(options, "auto")

        assertEquals(listOf("auto", "flyx", "cinepro", "xtream"), rows.map { it.key })
        assertEquals(listOf(true, false, false, false), rows.map { it.selected })
        assertEquals("Automatic", rows[0].label)
    }

    @Test
    fun anExplicitlyChosenBackendIsTheOnlySelectedRow() {
        val rows = preferredSourceRows(options, "cinepro")

        assertEquals(listOf(false, false, true, false), rows.map { it.selected })
        // The row echoes the server's own label rather than a hard-coded name.
        assertEquals("CinePro", rows.first { it.selected }.label)
    }

    @Test
    fun anOptionThatIsOffForTheServerIsDisabledAndSaysWhy() {
        val rows = preferredSourceRows(options, "auto")

        val off = rows.first { it.key == "xtream" }
        assertTrue(off.disabled)
        assertEquals("Off for this server", off.note)

        val on = rows.first { it.key == "auto" }
        assertFalse(on.disabled)
        assertNull(on.note)
    }

    @Test
    fun anEmptyOptionListRendersNoRows() {
        assertEquals(emptyList<PreferredSourceRow>(), preferredSourceRows(emptyList(), "auto"))
    }
}
