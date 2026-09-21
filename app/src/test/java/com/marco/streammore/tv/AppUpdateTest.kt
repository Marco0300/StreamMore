package com.marco.streammore.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun semanticReleaseComparisonOnlyAcceptsNewerVersions() {
        assertTrue(isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("0.9.9", "1.0.0"))
    }
}
