package com.marco.streammore.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract test for the GitHub auto-updater.
 *
 * The fixture `github-releases.json` is a real response captured from
 * `GET /repos/Marco0300/StreamMore/releases?per_page=100`, trimmed to the releases
 * that matter (the newest TV builds and the newest phone build), plus two clearly
 * synthetic entries (`v9.9.9` draft and `v9.9.8` prerelease) that prove those are
 * never offered. The phone app publishes into the same repository and its releases
 * are interleaved by publish order, which is the hazard this guards: polling
 * `/releases/latest` made a phone release the newest one, its tag parsed as version
 * 0.0.0, and the TV client silently reported "no update" for every TV build after
 * it.
 */
class AppUpdateTest {
    private fun livePayload(): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("github-releases.json")) {
            "github-releases.json fixture is missing"
        }.bufferedReader().use { it.readText() }

    @Test
    fun semanticReleaseComparisonOnlyAcceptsNewerVersions() {
        assertTrue(isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun picksTheNewestTvAssetFromTheRealReleasePayload() {
        val selected = selectLatestTvRelease(parseTvReleaseCandidates(livePayload()))

        assertNotNull(selected)
        assertEquals("1.18.2", selected!!.versionName)
        assertEquals("Streammore-TV-v1.18.2-release.apk", selected.assetName)
        assertEquals(
            "https://github.com/Marco0300/StreamMore/releases/download/v1.18.2/" +
                "Streammore-TV-v1.18.2-release.apk",
            selected.downloadUrl,
        )
        assertEquals(
            "c3824adcf03628179abc3b0ce2f6716d06e33c09d39954a242bee8b29d9b55de",
            selected.sha256,
        )
    }

    @Test
    fun neverOffersPhoneReleasesThatShareTheRepository() {
        val selected = selectLatestTvRelease(parseTvReleaseCandidates(livePayload()))!!

        assertTrue(isTvApkAsset(selected.assetName))
        assertFalse(selected.downloadUrl.contains("Streammore-Mobile", ignoreCase = true))
        // The fixture really does carry a published, non-draft phone release, so the
        // asset filter is what kept it out rather than its absence from the payload.
        val payload = livePayload()
        assertTrue(payload.contains("mobile-v1.0.7"))
        assertTrue(payload.contains("Streammore-Mobile-1.0.7-release.apk"))
    }

    @Test
    fun aNewerPhoneReleaseCannotShadowTheTvUpdate() {
        // The real failure mode, spelled out: `/releases/latest` was the phone
        // release, its tag parses as version 0.0.0, and 0 is never newer than the
        // installed version - so the update prompt went quiet.
        assertEquals("mobile-v1.0.7", normalizeTvReleaseVersion("mobile-v1.0.7"))
        assertFalse(isNewerVersion(normalizeTvReleaseVersion("mobile-v1.0.7"), "1.18.1"))

        // The payload lists the phone release first, as the newest published release.
        val payload = """
            [
              {"tag_name":"mobile-v1.0.7","draft":false,"prerelease":false,
               "assets":[{"name":"Streammore-Mobile-1.0.7-release.apk","digest":"sha256:11",
                          "browser_download_url":"https://example.invalid/mobile.apk"}]},
              {"tag_name":"v1.18.2","draft":false,"prerelease":false,
               "assets":[{"name":"Streammore-TV-v1.18.2-release.apk","digest":"sha256:22",
                          "browser_download_url":"https://example.invalid/tv.apk"}]}
            ]
        """.trimIndent()

        val selected = selectLatestTvRelease(parseTvReleaseCandidates(payload))!!
        assertEquals("1.18.2", selected.versionName)
        assertTrue(selected.downloadUrl.endsWith("tv.apk"))
    }

    @Test
    fun ignoresDraftAndPrereleaseTvReleasesThatWouldOtherwiseWin() {
        val candidates = parseTvReleaseCandidates(livePayload())

        assertTrue(candidates.any { it.versionName == "9.9.9" && it.draft })
        assertTrue(candidates.any { it.versionName == "9.9.8" && it.prerelease })
        assertEquals("1.18.2", selectLatestTvRelease(candidates)!!.versionName)
    }

    @Test
    fun offersThePublishedBuildAsAnUpgradeForOlderInstallsOnly() {
        val release = selectLatestTvRelease(parseTvReleaseCandidates(livePayload()))!!

        assertTrue(isNewerVersion(release.versionName, "1.18.1"))
        assertTrue(isNewerVersion(release.versionName, "1.17.1"))
        assertFalse(isNewerVersion(release.versionName, "1.18.2"))
        assertFalse(isNewerVersion(release.versionName, "1.19.0"))
    }

    @Test
    fun toleratesAnEmptyOrUnrelatedPayload() {
        assertEquals(emptyList<TvReleaseCandidate>(), parseTvReleaseCandidates("[]"))
        // A payload carrying only the phone client's asset yields no TV candidate.
        assertEquals(
            emptyList<TvReleaseCandidate>(),
            parseTvReleaseCandidates(
                """[{"tag_name":"mobile-v1.0.5","assets":[{"name":"Streammore-Mobile-1.0.5-release.apk"}]}]""",
            ),
        )
    }
}
