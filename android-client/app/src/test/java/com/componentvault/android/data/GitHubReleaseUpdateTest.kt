package com.componentvault.android.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GitHubReleaseUpdateTest {
    @Test
    fun numericSemanticVersionsCompareByNumber() {
        val newer = NumericSemanticVersion.parse("v0.3.10")
        val older = NumericSemanticVersion.parse("0.3.9")
        requireNotNull(newer)
        requireNotNull(older)
        assertEquals(1, newer.compareTo(older))
        assertEquals(newer, NumericSemanticVersion.parse("0.3.10"))
    }

    @Test
    fun malformedOrNonNumericVersionsAreRejected() {
        assertEquals(null, NumericSemanticVersion.parse("v0.3"))
        assertEquals(null, NumericSemanticVersion.parse("0.3.10-beta"))
        assertEquals(null, NumericSemanticVersion.parse("01.3.10"))
        assertEquals(null, NumericSemanticVersion.parse("0.3.10-dev.999999999999999999999999"))
        assertIs<ReleaseCheckResult.CannotDetermineVersion>(
            GitHubReleaseParser.compareWithInstalled(releaseJson(tag = "latest"), "0.3.9"),
        )
    }

    @Test
    fun stableChannelRejectsDevTagEvenWhenReleaseFlagIsWrong() {
        assertIs<ReleaseCheckResult.NoRelease>(
            GitHubReleaseParser.parse(releaseJson(tag = "v0.7.4-dev.1", prerelease = false)),
        )
    }

    @Test
    fun devChannelSelectsHighestEligibleReleaseAndStableWinsSameBase() {
        val releases = JSONArray()
            .put(JSONObject(releaseJson(tag = "v0.7.4-dev.2", prerelease = true)))
            .put(JSONObject(releaseJson(tag = "v0.7.4-dev.10", prerelease = true)))
            .put(JSONObject(releaseJson(tag = "v0.7.4", prerelease = false)))
        val result = assertIs<ReleaseCheckResult.UpdateAvailable>(
            GitHubReleaseParser.compareWithInstalled(releases.toString(), "0.7.3", UpdateChannel.Dev),
        )
        assertEquals("v0.7.4", result.release.tagName)
        assertEquals(1, NumericSemanticVersion.parse("0.7.4")!!.compareTo(NumericSemanticVersion.parse("0.7.4-dev.10")!!))
    }

    @Test
    fun equalVersionDoesNotOfferAnUpdate() {
        assertIs<ReleaseCheckResult.UpToDate>(
            GitHubReleaseParser.compareWithInstalled(releaseJson(), "0.3.10"),
        )
    }

    @Test
    fun missingExpectedApkKeepsReleaseMetadata() {
        val result = assertIs<ReleaseCheckResult.UpdateAvailable>(
            GitHubReleaseParser.compareWithInstalled(releaseJson(assetName = "other.apk"), "0.3.9"),
        )
        assertEquals(null, result.release.apkUrl)
        assertEquals("Plain release notes", result.release.releaseNotes)
    }

    @Test
    fun foreignAndUnsafeAssetUrlsAreRejected() {
        listOf(
            "https://example.com/Rafael-ban/Component_Manager/releases/download/v0.3.10/component-vault-android-release.apk",
            "https://attacker@github.com/Rafael-ban/Component_Manager/releases/download/v0.3.10/component-vault-android-release.apk",
            "javascript:alert(1)",
        ).forEach { url ->
            assertIs<ReleaseCheckResult.InvalidReleaseLinks>(
                GitHubReleaseParser.parse(releaseJson(assetUrl = url)),
            )
        }
        assertIs<ReleaseCheckResult.InvalidReleaseLinks>(
            GitHubReleaseParser.parse(
                releaseJson(pageUrl = "https://example.com/Rafael-ban/Component_Manager/releases/tag/v0.3.10"),
            ),
        )
    }

    @Test
    fun draftAndPrereleaseResponsesAreNotEligible() {
        assertIs<ReleaseCheckResult.NoRelease>(GitHubReleaseParser.parse(releaseJson(draft = true)))
        assertIs<ReleaseCheckResult.NoRelease>(GitHubReleaseParser.parse(releaseJson(prerelease = true)))
    }

    private fun releaseJson(
        tag: String = "v0.3.10",
        assetName: String = "component-vault-android-release.apk",
        assetUrl: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false,
        pageUrl: String = "https://github.com/Rafael-ban/Component_Manager/releases/tag/$tag",
    ): String = JSONObject().apply {
        put("tag_name", tag)
        put("draft", draft)
        put("prerelease", prerelease)
        put("published_at", "2026-09-15T08:00:00Z")
        put("body", "Plain release notes")
        put("html_url", pageUrl)
        put("assets", JSONArray().put(JSONObject().apply {
            put("name", assetName)
            put("browser_download_url", assetUrl ?: "https://github.com/Rafael-ban/Component_Manager/releases/download/$tag/component-vault-android-release.apk")
        }))
    }.toString()
}
