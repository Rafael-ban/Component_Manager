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
    public void ParseRelease_RejectsForeignAssetButKeepsRelease()
    {
        var release = GitHubReleaseParser.ParseRelease(ReleaseJson(
            msixUrl: "https://github.com/Rafael-ban/Other/releases/download/v0.3.11/component-vault-windows-x64.msix"
        ));

        Assert.NotNull(release);
        Assert.Null(release.MsixDownload);
        Assert.NotNull(release.PortableDownload);
    }

    [Fact]
    public void ParseRelease_RepresentsMissingPlatformAsset()
    {
        var release = GitHubReleaseParser.ParseRelease(ReleaseJson(includePortable: false));

        Assert.NotNull(release);
        Assert.NotNull(release.MsixDownload);
        Assert.Null(release.PortableDownload);
    }

    [Fact]
    public void ValidateAssetUrl_RejectsUserInfoAndQuery()
    {
        Assert.Null(GitHubReleaseParser.ValidateAssetUrl(
            "https://user@github.com/Rafael-ban/Component_Manager/releases/download/v0.3.11/component-vault-windows-x64.msix",
            "v0.3.11",
            GitHubReleaseParser.MsixAssetName
        ));
        Assert.Null(GitHubReleaseParser.ValidateAssetUrl(
            "https://github.com/Rafael-ban/Component_Manager/releases/download/v0.3.11/component-vault-windows-x64.msix?redirect=evil",
            "v0.3.11",
            GitHubReleaseParser.MsixAssetName
        ));
    }

    private static string ReleaseJson(
        bool prerelease = false,
        string htmlUrl = "https://github.com/Rafael-ban/Component_Manager/releases/tag/v0.3.11",
        string msixUrl = "https://github.com/Rafael-ban/Component_Manager/releases/download/v0.3.11/component-vault-windows-x64.msix",
        bool includePortable = true
    )
    {
        var portable = includePortable
            ? ", {\"name\":\"component-vault-windows-portable-x64.zip\",\"browser_download_url\":\"https://github.com/Rafael-ban/Component_Manager/releases/download/v0.3.11/component-vault-windows-portable-x64.zip\"}"
            : string.Empty;
        return $$"""
            {
              "tag_name": "v0.3.11",
              "html_url": "{{htmlUrl}}",
              "draft": false,
              "prerelease": {{prerelease.ToString().ToLowerInvariant()}},
              "published_at": "2026-09-15T08:00:00Z",
              "body": "Fixes and improvements.",
              "assets": [
                {"name":"component-vault-windows-x64.msix","browser_download_url":"{{msixUrl}}"}{{portable}}
              ]
            }
            """;
    }
}
