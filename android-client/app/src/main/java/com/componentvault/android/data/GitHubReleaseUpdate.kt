package com.componentvault.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal data class NumericSemanticVersion(
    val major: Long,
    val minor: Long,
    val patch: Long,
    val devNumber: Long? = null,
) : Comparable<NumericSemanticVersion> {
    override fun compareTo(other: NumericSemanticVersion): Int {
        val base = compareValuesBy(this, other, NumericSemanticVersion::major, NumericSemanticVersion::minor, NumericSemanticVersion::patch)
        if (base != 0) return base
        if (devNumber == null && other.devNumber != null) return 1
        if (devNumber != null && other.devNumber == null) return -1
        return compareValues(devNumber ?: 0, other.devNumber ?: 0)
    }

    companion object {
        private val pattern = Regex("^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-dev\\.(0|[1-9]\\d*))?$")

        fun parse(value: String): NumericSemanticVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            val numbers = (1..3).map { match.groupValues[it].toLongOrNull() ?: return null }
            val devText = match.groupValues[4]
            val devNumber = if (devText.isEmpty()) null else devText.toLongOrNull() ?: return null
            return NumericSemanticVersion(numbers[0], numbers[1], numbers[2], devNumber)
        }
    }
}

internal enum class UpdateChannel { Stable, Dev }

internal data class GitHubReleaseInfo(
    val tagName: String,
    val version: NumericSemanticVersion,
    val publishedAt: String,
    val releaseNotes: String,
    val pageUrl: String,
    val apkUrl: String?,
)

internal sealed interface ReleaseCheckResult {
    data class UpdateAvailable(val release: GitHubReleaseInfo) : ReleaseCheckResult
    data class UpToDate(val release: GitHubReleaseInfo) : ReleaseCheckResult
    data class LocalNewer(val release: GitHubReleaseInfo) : ReleaseCheckResult
    data object NoRelease : ReleaseCheckResult
    data object RateLimited : ReleaseCheckResult
    data class CannotDetermineVersion(val tagName: String) : ReleaseCheckResult
    data object InvalidReleaseLinks : ReleaseCheckResult
    data object NetworkFailure : ReleaseCheckResult
}

internal object GitHubReleaseParser {
    private const val owner = "Rafael-ban"
    private const val repository = "Component_Manager"
    private const val apkName = "component-vault-android-release.apk"

    fun parse(body: String, channel: UpdateChannel = UpdateChannel.Stable): ReleaseCheckResult {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return ReleaseCheckResult.NetworkFailure
        if (json.optBoolean("draft") || channel == UpdateChannel.Stable && json.optBoolean("prerelease")) return ReleaseCheckResult.NoRelease

        val tag = json.optString("tag_name").trim()
        val version = NumericSemanticVersion.parse(tag)
            ?: return ReleaseCheckResult.CannotDetermineVersion(tag)
        if (channel == UpdateChannel.Stable && version.devNumber != null) return ReleaseCheckResult.NoRelease
        val pageUrl = json.optString("html_url").trim()
        val apkAsset = json.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .asSequence()
                    .mapNotNull(assets::optJSONObject)
                    .firstOrNull { it.optString("name") == apkName }
            }
        val apkUrl = apkAsset?.optString("browser_download_url")?.trim()?.takeIf { it.isNotBlank() }
        if (!isTrustedReleasePage(pageUrl, tag) || (apkUrl != null && !isTrustedApk(apkUrl, tag))) {
            return ReleaseCheckResult.InvalidReleaseLinks
        }
        return ReleaseCheckResult.UpdateAvailable(
            GitHubReleaseInfo(
                tagName = tag,
                version = version,
                publishedAt = json.optString("published_at").trim(),
                releaseNotes = json.optString("body"),
                pageUrl = pageUrl,
                apkUrl = apkUrl,
            ),
        )
    }

    fun parseLatest(body: String, channel: UpdateChannel): ReleaseCheckResult {
        if (channel == UpdateChannel.Stable) return parse(body, channel)
        val releases = runCatching { JSONArray(body) }.getOrNull() ?: return ReleaseCheckResult.NetworkFailure
        return (0 until releases.length()).asSequence()
            .mapNotNull(releases::optJSONObject)
            .map { parse(it.toString(), channel) }
            .filterIsInstance<ReleaseCheckResult.UpdateAvailable>()
            .maxByOrNull { it.release.version }
            ?: ReleaseCheckResult.NoRelease
    }

    fun compareWithInstalled(body: String, installedVersion: String, channel: UpdateChannel = UpdateChannel.Stable): ReleaseCheckResult {
        val parsed = parseLatest(body, channel)
        if (parsed !is ReleaseCheckResult.UpdateAvailable) return parsed
        val installed = NumericSemanticVersion.parse(installedVersion)
            ?: return ReleaseCheckResult.CannotDetermineVersion(installedVersion)
        val comparison = installed.compareTo(parsed.release.version)
        return when {
            comparison < 0 -> parsed
            comparison == 0 -> ReleaseCheckResult.UpToDate(parsed.release)
            else -> ReleaseCheckResult.LocalNewer(parsed.release)
        }
    }

    private fun isTrustedReleasePage(value: String, tag: String): Boolean = trustedUri(value)?.let { uri ->
        uri.path == "/$owner/$repository/releases/tag/$tag" &&
            uri.query == null &&
            uri.fragment == null
    } == true

    private fun isTrustedApk(value: String, tag: String): Boolean = trustedUri(value)?.let { uri ->
        uri.path == "/$owner/$repository/releases/download/$tag/$apkName" && uri.query == null && uri.fragment == null
    } == true

    private fun trustedUri(value: String): URI? = runCatching { URI(value) }.getOrNull()?.takeIf { uri ->
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.port == -1
    }
}

internal class GitHubReleaseUpdateChecker {
    private val mutex = Mutex()

    suspend fun check(installedVersion: String, channel: UpdateChannel = UpdateChannel.Stable): ReleaseCheckResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val connection = runCatching { URL(if (channel == UpdateChannel.Stable) latestReleaseUrl else releasesUrl).openConnection() as HttpURLConnection }
                .getOrElse { return@withContext ReleaseCheckResult.NetworkFailure }
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "ComponentVault-Android/${installedVersion.take(40)}")
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (output.size() + count > maxResponseBytes) return@withContext ReleaseCheckResult.NetworkFailure
                                output.write(buffer, 0, count)
                            }
                            output.toString(Charsets.UTF_8.name())
                        }
                        GitHubReleaseParser.compareWithInstalled(body, installedVersion, channel)
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> ReleaseCheckResult.NoRelease
                    HttpURLConnection.HTTP_FORBIDDEN -> ReleaseCheckResult.RateLimited
                    else -> ReleaseCheckResult.NetworkFailure
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ReleaseCheckResult.NetworkFailure
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val latestReleaseUrl = "https://api.github.com/repos/Rafael-ban/Component_Manager/releases/latest"
        const val releasesUrl = "https://api.github.com/repos/Rafael-ban/Component_Manager/releases?per_page=30"
        const val maxResponseBytes = 512 * 1024
    }
}
