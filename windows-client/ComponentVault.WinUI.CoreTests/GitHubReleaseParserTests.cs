using ComponentVault.WinUI.Services.Updates;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class GitHubReleaseParserTests
{
    [Theory]
    [InlineData("0.3.9", "v0.3.10", UpdateComparison.UpdateAvailable)]
    [InlineData("0.3.10.0", "v0.3.10", UpdateComparison.UpToDate)]
    [InlineData("0.4.0.0", "v0.3.10", UpdateComparison.LocalNewer)]
    [InlineData("0.3.10", "v0.3.10-beta", UpdateComparison.UnknownVersion)]
    [InlineData("0.7.4-dev.1", "v0.7.4-dev.2", UpdateComparison.UpdateAvailable)]
    [InlineData("0.7.4-dev.2", "v0.7.4", UpdateComparison.UpdateAvailable)]
    [InlineData("unknown", "v0.3.10", UpdateComparison.UnknownVersion)]
    public void Compare_UsesNumericVersions(string local, string remote, UpdateComparison expected)
    {
        Assert.Equal(expected, GitHubReleaseParser.Compare(local, remote));
    }

    [Fact]
    public void ParseRelease_IgnoresPrerelease()
    {
        Assert.Null(GitHubReleaseParser.ParseRelease(ReleaseJson(prerelease: true)));
    }

    [Fact]
    public void StableChannelRejectsDevTagEvenWhenPrereleaseFlagIsWrong()
    {
        Assert.Null(GitHubReleaseParser.ParseRelease(ReleaseJson(tag: "v0.7.4-dev.1")));
        Assert.Null(GitHubReleaseParser.ParseNumericVersion("999999999999999999999.0.0"));
    }

    [Fact]
    public void ParseLatest_DevChannelIncludesPrereleaseAndPrefersStableAtSameBase()
    {
        var json = $"[{ReleaseJson(prerelease: true, tag: "v0.7.4-dev.2")},{ReleaseJson(tag: "v0.7.4")}]";
        var release = GitHubReleaseParser.ParseLatest(json, UpdateChannel.Dev);
        Assert.NotNull(release);
        Assert.Equal("v0.7.4", release.Tag);
    }

    [Fact]
    public void ParseRelease_RejectsForeignReleasePage()
    {
        Assert.Null(GitHubReleaseParser.ParseRelease(ReleaseJson(
            htmlUrl: "https://github.com/attacker/repo/releases/tag/v0.3.11"
        )));
    }

    [Fact]
    public void ParseRelease_RejectsReleasePageForDifferentTag()
    {
        Assert.Null(GitHubReleaseParser.ParseRelease(ReleaseJson(
            htmlUrl: "https://github.com/Rafael-ban/Component_Manager/releases/tag/v0.3.10"
        )));
    }

    [Fact]
    public void ParseRelease_RejectsForeignPortableAssetButKeepsRelease()
    {
        var release = GitHubReleaseParser.ParseRelease(ReleaseJson(
            portableUrl: "https://github.com/Rafael-ban/Other/releases/download/v0.3.11/component-vault-windows-portable-x64.zip"
        ));

        Assert.NotNull(release);
        Assert.Null(release.PortableDownload);
    }

    [Fact]
    public void ParseRelease_RepresentsMissingPlatformAsset()
    {
        var release = GitHubReleaseParser.ParseRelease(ReleaseJson(includePortable: false));

        Assert.NotNull(release);
        Assert.Null(release.PortableDownload);
    }

    [Fact]
    public void ValidateAssetUrl_RejectsUserInfoAndQuery()
    {
        Assert.Null(GitHubReleaseParser.ValidateAssetUrl(
            "https://user@github.com/Rafael-ban/Component_Manager/releases/download/v0.3.11/component-vault-windows-portable-x64.zip",
            "v0.3.11",
            GitHubReleaseParser.PortableAssetName
        ));
        Assert.Null(GitHubReleaseParser.ValidateAssetUrl(
            "https://github.com/Rafael-ban/Component_Manager/releases/download/v0.3.11/component-vault-windows-portable-x64.zip?redirect=evil",
            "v0.3.11",
            GitHubReleaseParser.PortableAssetName
        ));
    }

    private static string ReleaseJson(
        bool prerelease = false,
        string tag = "v0.3.11",
        string? htmlUrl = null,
        string? portableUrl = null,
        bool includePortable = true
    )
    {
        htmlUrl ??= $"https://github.com/Rafael-ban/Component_Manager/releases/tag/{tag}";
        portableUrl ??= $"https://github.com/Rafael-ban/Component_Manager/releases/download/{tag}/component-vault-windows-portable-x64.zip";
        var portable = includePortable
            ? $", {{\"name\":\"component-vault-windows-portable-x64.zip\",\"browser_download_url\":\"{portableUrl}\"}}"
            : string.Empty;
        return $$"""
            {
              "tag_name": "{{tag}}",
              "html_url": "{{htmlUrl}}",
              "draft": false,
              "prerelease": {{prerelease.ToString().ToLowerInvariant()}},
              "published_at": "2026-09-15T08:00:00Z",
              "body": "Fixes and improvements.",
              "assets": [
                {{portable.TrimStart(',', ' ')}}
              ]
            }
            """;
    }
}
