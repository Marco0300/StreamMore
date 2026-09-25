package com.marco.streammore.mobile

import org.junit.Assert.assertEquals
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

    @Test
    fun mobileReleaseTagsNormalizeWithoutChangingAppVersionSemantics() {
        assertEquals("1.0.5", normalizeMobileReleaseVersion("mobile-v1.0.5"))
        assertEquals("1.0.5", normalizeMobileReleaseVersion("v1.0.5"))
    }

    @Test
    fun updaterChoosesTheNewestMobileAssetInsteadOfTheNewestTvRelease() {
        val selected = selectLatestMobileRelease(
            listOf(
                MobileReleaseCandidate(
                    versionName = "1.18.0",
                    assetName = "Streammore-TV-v1.18.0-release.apk",
                    downloadUrl = "https://example.invalid/tv.apk",
                    sha256 = null,
                    releaseNotes = "TV",
                ),
                MobileReleaseCandidate(
                    versionName = "1.0.5",
                    assetName = "Streammore-Mobile-v1.0.5-release.apk",
                    downloadUrl = "https://example.invalid/mobile.apk",
                    sha256 = "abc",
                    releaseNotes = "Mobile",
                ),
                MobileReleaseCandidate(
                    versionName = "1.0.4",
                    assetName = "Streammore-Mobile-v1.0.4-release.apk",
                    downloadUrl = "https://example.invalid/older.apk",
                    sha256 = null,
                    releaseNotes = "Older mobile",
                ),
            ),
        )

        assertEquals("1.0.5", selected?.versionName)
        assertEquals("Streammore-Mobile-v1.0.5-release.apk", selected?.assetName)
    }
}
