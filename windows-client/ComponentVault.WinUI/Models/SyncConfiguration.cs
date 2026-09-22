namespace ComponentVault.WinUI.Models;

public sealed class SyncConfiguration
{
    public required string DeviceId { get; init; }
    public required string ServerBaseUrl { get; init; }
    public required string FallbackServerBaseUrl { get; init; }
    public required string ApiToken { get; init; }
    public required bool AutoSyncEnabled { get; init; }
    public required string LastSyncedAt { get; init; }
    public required string LastSyncMessage { get; init; }

    public SyncConfiguration WithDraftConnection(
        string serverBaseUrl,
        string fallbackServerBaseUrl,
        string apiToken,
        bool autoSyncEnabled
    ) => new()
    {
        DeviceId = DeviceId,
        ServerBaseUrl = NormalizeServerBaseUrl(serverBaseUrl),
        FallbackServerBaseUrl = NormalizeServerBaseUrl(fallbackServerBaseUrl),
        ApiToken = apiToken.Trim(),
        AutoSyncEnabled = autoSyncEnabled,
        LastSyncedAt = LastSyncedAt,
        LastSyncMessage = LastSyncMessage,
    };

    public bool MatchesConnectionDraft(
        string serverBaseUrl,
        string fallbackServerBaseUrl,
        string apiToken,
        bool autoSyncEnabled
    ) => string.Equals(ServerBaseUrl, NormalizeServerBaseUrl(serverBaseUrl), StringComparison.Ordinal)
        && string.Equals(FallbackServerBaseUrl, NormalizeServerBaseUrl(fallbackServerBaseUrl), StringComparison.Ordinal)
        && string.Equals(ApiToken, apiToken.Trim(), StringComparison.Ordinal)
        && AutoSyncEnabled == autoSyncEnabled;

    private static string NormalizeServerBaseUrl(string serverBaseUrl) =>
        serverBaseUrl.Trim().TrimEnd('/');

    public string ApiTokenMasked
    {
        get
        {
            if (string.IsNullOrWhiteSpace(ApiToken))
            {
                return string.Empty;
            }

            if (ApiToken.Length <= 4)
            {
                return new string('*', ApiToken.Length);
            }

            return $"{new string('*', ApiToken.Length - 4)}{ApiToken[^4..]}";
        }
    }
}
