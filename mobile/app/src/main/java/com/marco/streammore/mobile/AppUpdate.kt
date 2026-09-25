package com.marco.streammore.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val GITHUB_RELEASES_URL =
    "https://api.github.com/repos/Marco0300/StreamMore/releases?per_page=100"
private const val UPDATE_USER_AGENT = "Streammore-Mobile/${BuildConfig.VERSION_NAME}"

internal data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String?,
    val releaseNotes: String,
)

internal data class MobileReleaseCandidate(
    val versionName: String,
    val assetName: String,
    val downloadUrl: String,
    val sha256: String?,
    val releaseNotes: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

internal fun isMobileApkAsset(name: String): Boolean =
    name.endsWith(".apk", ignoreCase = true) && name.contains("mobile", ignoreCase = true)

internal fun normalizeMobileReleaseVersion(tag: String): String =
    tag.removePrefix("mobile-").removePrefix("v").removeSuffix("-mobile")

private fun versionParts(value: String): List<Int> = value
    .removePrefix("v")
    .split('.', '-', '+')
    .take(3)
    .map { it.toIntOrNull() ?: 0 }
    .let { it + List(3 - it.size) { 0 } }

private fun compareVersionNames(left: String, right: String): Int {
    val leftParts = versionParts(left)
    val rightParts = versionParts(right)
    for (index in leftParts.indices) {
        val comparison = leftParts[index].compareTo(rightParts[index])
        if (comparison != 0) return comparison
    }
    return 0
}

internal fun selectLatestMobileRelease(releases: List<MobileReleaseCandidate>): MobileReleaseCandidate? =
    releases
        .filter { !it.draft && !it.prerelease && isMobileApkAsset(it.assetName) }
        .maxWithOrNull(Comparator { left, right -> compareVersionNames(left.versionName, right.versionName) })

internal fun isNewerVersion(latest: String, current: String): Boolean =
    versionParts(latest).zip(versionParts(current)).firstOrNull { it.first != it.second }?.let {
        it.first > it.second
    } ?: false

internal fun parseMobileReleaseCandidates(payload: String): List<MobileReleaseCandidate> {
    val releases = JSONArray(payload)
    return buildList {
        for (index in 0 until releases.length()) {
            val release = releases.optJSONObject(index) ?: continue
            val assets = release.optJSONArray("assets") ?: continue
            val apk = (0 until assets.length())
                .mapNotNull { assets.optJSONObject(it) }
                .firstOrNull { isMobileApkAsset(it.optString("name")) }
                ?: continue
            val version = normalizeMobileReleaseVersion(release.optString("tag_name"))
            if (version.isBlank()) continue
            add(
                MobileReleaseCandidate(
                    versionName = version,
                    assetName = apk.optString("name"),
                    downloadUrl = apk.optString("browser_download_url"),
                    sha256 = apk.optString("digest").removePrefix("sha256:").ifBlank { null },
                    releaseNotes = release.optString("body").trim(),
                    draft = release.optBoolean("draft"),
                    prerelease = release.optBoolean("prerelease"),
                ),
            )
        }
    }
}

internal suspend fun checkForAppUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
    val connection = (URL(GITHUB_RELEASES_URL).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 15_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", UPDATE_USER_AGENT)
    }
    try {
        if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("GitHub update check failed (${connection.responseCode})")
        }
        val payload = connection.inputStream.bufferedReader().use { it.readText() }
        val candidates = parseMobileReleaseCandidates(payload)
        val release = selectLatestMobileRelease(candidates) ?: return@withContext null
        if (!isNewerVersion(release.versionName, BuildConfig.VERSION_NAME)) return@withContext null
        if (release.downloadUrl.isBlank()) return@withContext null
        AppUpdate(
            versionName = release.versionName,
            downloadUrl = release.downloadUrl,
            sha256 = release.sha256,
            releaseNotes = release.releaseNotes,
        )
    } finally {
        connection.disconnect()
    }
}

internal suspend fun downloadAndInstallUpdate(context: Context, update: AppUpdate) = withContext(Dispatchers.IO) {
    val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
    val apk = File(updatesDir, "streammore-${update.versionName}.apk")
    val connection = (URL(update.downloadUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("Accept", "application/octet-stream")
        setRequestProperty("User-Agent", UPDATE_USER_AGENT)
    }
    try {
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Update download failed (${connection.responseCode})")
        }
        connection.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
    } finally {
        connection.disconnect()
    }

    update.sha256?.let { expected ->
        val actual = MessageDigest.getInstance("SHA-256").digest(apk.readBytes())
            .joinToString("") { byte -> "%02x".format(byte) }
        if (!actual.equals(expected, ignoreCase = true)) {
            apk.delete()
            throw SecurityException("Downloaded update checksum did not match GitHub")
        }
    }

    withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            throw IllegalStateException("Allow Streammore to install updates, then try again")
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }
}
