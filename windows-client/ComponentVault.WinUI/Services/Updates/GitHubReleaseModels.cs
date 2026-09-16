using System.Text.Json;

namespace ComponentVault.WinUI.Services.Updates;

public enum UpdateComparison { UpdateAvailable, UpToDate, LocalNewer, UnknownVersion }

public sealed record GitHubReleaseInfo(
    string Tag,
    Version Version,
    DateTimeOffset? PublishedAt,
    string Notes,
    Uri ReleasePage,
    Uri? PortableDownload
);

public sealed record UpdateCheckResult(
    bool IsSuccess,
    string Message,
    UpdateComparison Comparison,
    GitHubReleaseInfo? Release
);

public static class GitHubReleaseParser
{
    public const string Owner = "Rafael-ban";
    public const string Repository = "Component_Manager";
    public const string PortableAssetName = "component-vault-windows-portable-x64.zip";

    public static Version? ParseNumericVersion(string? value)
    {
        var normalized = value?.Trim();
        if (string.IsNullOrEmpty(normalized)) return null;
        if (normalized.StartsWith("v", StringComparison.OrdinalIgnoreCase)) normalized = normalized[1..];
        var parts = normalized.Split('.');
        if (parts.Length is < 3 or > 4 || parts.Any(part => part.Length == 0 || !part.All(char.IsAsciiDigit))) return null;
        var numbers = new int[4];
        for (var index = 0; index < parts.Length; index++)
            if (!int.TryParse(parts[index], out numbers[index])) return null;
        return new Version(numbers[0], numbers[1], numbers[2], numbers[3]);
    }

    public static UpdateComparison Compare(string? localVersion, string? remoteTag)
    {
        var local = ParseNumericVersion(localVersion);
        var remote = ParseNumericVersion(remoteTag);
        if (local is null || remote is null) return UpdateComparison.UnknownVersion;
        var comparison = local.CompareTo(remote);
        return comparison < 0 ? UpdateComparison.UpdateAvailable
            : comparison > 0 ? UpdateComparison.LocalNewer
            : UpdateComparison.UpToDate;
    }

    public static GitHubReleaseInfo? ParseRelease(string json)
    {
        using var document = JsonDocument.Parse(json, new JsonDocumentOptions { MaxDepth = 32 });
        var root = document.RootElement;
        if (root.ValueKind != JsonValueKind.Object
            || root.TryGetProperty("draft", out var draft) && draft.GetBoolean()
            || root.TryGetProperty("prerelease", out var prerelease) && prerelease.GetBoolean()) return null;
        var tag = Text(root, "tag_name");
        var version = ParseNumericVersion(tag);
        var releasePage = ValidateReleasePage(Text(root, "html_url"), tag);
        if (tag is null || version is null || releasePage is null) return null;
        var assets = root.TryGetProperty("assets", out var assetArray) && assetArray.ValueKind == JsonValueKind.Array
            ? assetArray.EnumerateArray().ToArray() : [];
        return new(
            tag,
            version,
            DateTimeOffset.TryParse(Text(root, "published_at"), out var published) ? published : null,
            Text(root, "body") ?? string.Empty,
            releasePage,
            FindAsset(assets, tag, PortableAssetName)
        );
    }

    public static Uri? ValidateReleasePage(string? value, string? expectedTag = null)
    {
        if (!TryTrustedGitHubUri(value, out var uri)) return null;
        var prefix = $"/{Owner}/{Repository}/releases";
        var expectedPath = expectedTag is null
            ? prefix
            : $"{prefix}/tag/{Uri.EscapeDataString(expectedTag)}";
        return string.IsNullOrEmpty(uri.Query) && string.IsNullOrEmpty(uri.Fragment)
            && uri.AbsolutePath.Equals(expectedPath, StringComparison.Ordinal) ? uri : null;
    }

    public static Uri? ValidateAssetUrl(string? value, string tag, string expectedName)
    {
        if (!TryTrustedGitHubUri(value, out var uri)) return null;
        var expectedPath = $"/{Owner}/{Repository}/releases/download/{tag}/{expectedName}";
        return uri.AbsolutePath.Equals(expectedPath, StringComparison.Ordinal) && string.IsNullOrEmpty(uri.Query) && string.IsNullOrEmpty(uri.Fragment) ? uri : null;
    }

    private static Uri? FindAsset(IEnumerable<JsonElement> assets, string tag, string expectedName)
    {
        foreach (var asset in assets)
            if (Text(asset, "name") == expectedName && ValidateAssetUrl(Text(asset, "browser_download_url"), tag, expectedName) is { } uri) return uri;
        return null;
    }

    private static bool TryTrustedGitHubUri(string? value, out Uri uri)
    {
        if (Uri.TryCreate(value, UriKind.Absolute, out uri!)
            && uri.Scheme == Uri.UriSchemeHttps && uri.IsDefaultPort
            && string.IsNullOrEmpty(uri.UserInfo)
            && uri.Host.Equals("github.com", StringComparison.OrdinalIgnoreCase)) return true;
        uri = null!;
        return false;
    }

    private static string? Text(JsonElement root, string name) => root.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString()?.Trim() : null;
}
