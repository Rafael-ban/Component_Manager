using ComponentVault.WinUI.Services;

namespace ComponentVault.WinUI.Models;

public sealed class SyncRunResult
{
    public required bool IsSuccess { get; init; }
    public required string Message { get; init; }
    public int AcceptedComponents { get; init; }
    public int AcceptedStockMovements { get; init; }
    public string UsedServerBaseUrl { get; init; } = string.Empty;
    public SyncPullResponse? PullResponse { get; init; }

    public static SyncRunResult Failure(string message) =>
        new()
        {
            IsSuccess = false,
            Message = message,
        };
}
