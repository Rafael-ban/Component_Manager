namespace ComponentVault.WinUI.Models;

public sealed class SyncConfiguration
{
    public required string DeviceId { get; init; }
    public required string ServerBaseUrl { get; init; }
    public required string ApiToken { get; init; }
    public required bool AutoSyncEnabled { get; init; }
    public required string LastSyncedAt { get; init; }
    public required string LastSyncMessage { get; init; }

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
