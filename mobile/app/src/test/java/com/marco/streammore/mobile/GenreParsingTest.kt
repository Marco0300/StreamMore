package com.marco.streammore.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreParsingTest {
    @Test
    fun titleGenresAreReadAndEmptyNamesAreIgnored() {
        assertEquals(
            listOf("Action", "Science Fiction"),
            genreNamesFrom(listOf("Action", null, "Science Fiction", "")),
        )
    }
}
