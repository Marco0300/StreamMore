package com.marco.streammore.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test for the GitHub auto-updater.
 *
 * The fixture `github-releases.json` is a real response captured from
 * `GET /repos/Marco0300/StreamMore/releases?per_page=100`, trimmed to the
 * release that matters and the newest TV release, plus two clearly synthetic
 * entries (`mobile-v2.0.0` prerelease and `mobile-v3.0.0` draft) that prove
 * those releases are never offered. Real TV releases share this repository and
 * carry higher version numbers, so they are the main hazard this test guards.
 */
class MobileUpdaterContractTest {
    private fun livePayload(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("github-releases.json")) {
            "github-releases.json fixture is missing"
        }.bufferedReader().use { it.readText() }

    @Test
    fun picksTheNewestMobileAssetFromTheRealReleasePayload() {
        val selected = selectLatestMobileRelease(parseMobileReleaseCandidates(livePayload()))

        assertNotNull(selected)
        assertEquals("1.0.5", selected!!.versionName)
        assertEquals("Streammore-Mobile-1.0.5-release.apk", selected.assetName)
        assertEquals(
            "https://github.com/Marco0300/StreamMore/releases/download/mobile-v1.0.5/" +
                "Streammore-Mobile-1.0.5-release.apk",
            selected.downloadUrl,
        )
        assertEquals(
            "8b13f0ca3e230d867631cd058d634a708aaab9b91890e94324284aa3accdc93d",
            selected.sha256,
        )
    }

    @Test
    fun neverOffersTelevisionReleasesEvenThoughTheirVersionNumbersAreHigher() {
        val selected = selectLatestMobileRelease(parseMobileReleaseCandidates(livePayload()))!!

        assertTrue(isMobileApkAsset(selected.assetName))
        assertFalse(selected.downloadUrl.contains("Streammore-TV", ignoreCase = true))
        // The TV app in this repository is published as 1.17.0 and would win a
        // naive "newest version" comparison, so the asset-name filter is what
        // keeps the mobile client from installing the TV build.
        assertTrue(isNewerVersion("1.17.0", "1.0.5"))
    }

    @Test
    fun ignoresPrereleaseAndDraftMobileReleasesThatWouldOtherwiseWin() {
        val candidates = parseMobileReleaseCandidates(livePayload())

        assertTrue(candidates.any { it.versionName == "2.0.0" && it.prerelease })
        assertTrue(candidates.any { it.versionName == "3.0.0" && it.draft })
        assertEquals("1.0.5", selectLatestMobileRelease(candidates)!!.versionName)
    }

    @Test
    fun offersThePublishedBuildAsAnUpgradeForOlderInstallsOnly() {
        val release = selectLatestMobileRelease(parseMobileReleaseCandidates(livePayload()))!!

        assertTrue(isNewerVersion(release.versionName, "1.0.4"))
        assertFalse(isNewerVersion(release.versionName, "1.0.5"))
        assertFalse(isNewerVersion(release.versionName, "1.1.0"))
    }

    @Test
    fun toleratesAnEmptyOrUnrelatedPayload() {
        assertEquals(emptyList<MobileReleaseCandidate>(), parseMobileReleaseCandidates("[]"))
        assertEquals(
            emptyList<MobileReleaseCandidate>(),
            parseMobileReleaseCandidates("""[{"tag_name":"v1.0.0","assets":[{"name":"Streammore-TV-v1.0.0-release.apk"}]}]"""),
        )
    }
}
