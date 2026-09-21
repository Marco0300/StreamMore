package com.marco.streammore.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val GITHUB_RELEASES_URL =
    "https://api.github.com/repos/Marco0300/StreamMore/releases/latest"
private const val UPDATE_USER_AGENT = "Streammore-TV/${BuildConfig.VERSION_NAME}"

internal data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String?,
    val releaseNotes: String,
)

internal fun isNewerVersion(latest: String, current: String): Boolean {
    fun parts(value: String): List<Int> = value
        .removePrefix("v")
        .split('.', '-', '+')
        .take(3)
        .map { it.toIntOrNull() ?: 0 }
        .let { it + List(3 - it.size) { 0 } }
    return parts(latest).zip(parts(current)).firstOrNull { it.first != it.second }?.let {
        it.first > it.second
    } ?: false
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
        val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val version = release.optString("tag_name").removePrefix("v")
        if (version.isBlank() || !isNewerVersion(version, BuildConfig.VERSION_NAME)) return@withContext null
        val assets = release.optJSONArray("assets") ?: return@withContext null
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null
        AppUpdate(
            versionName = version,
            downloadUrl = apk.getString("browser_download_url"),
            sha256 = apk.optString("digest").removePrefix("sha256:").ifBlank { null },
            releaseNotes = release.optString("body").trim(),
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
