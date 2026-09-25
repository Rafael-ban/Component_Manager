using ComponentVault.WinUI.Models;

namespace ComponentVault.WinUI.Services;

public sealed class InventorySyncService
{
    private readonly InventoryStore _store;
    private readonly SyncApiClient _apiClient;

    public InventorySyncService(InventoryStore store, SyncApiClient apiClient)
    {
        _store = store;
        _apiClient = apiClient;
    }

    public Task<OperationResult> TestConnectionAsync(CancellationToken cancellationToken = default)
    {
        return _apiClient.TestConnectionAsync(_store.GetSyncConfiguration(), cancellationToken);
    }

    public Task<OperationResult> TestConnectionAsync(
        SyncConfiguration draft,
        CancellationToken cancellationToken = default
    )
    {
        return _apiClient.TestConnectionAsync(draft, cancellationToken);
    }

    public async Task<SyncRunResult> RunSyncAsync(CancellationToken cancellationToken = default)
    {
        var envelope = _store.CreateSyncEnvelope();
        var result = await _apiClient.RunSyncAsync(
            envelope.Settings,
            envelope.PushRequest,
            envelope.Cursor,
            cancellationToken,
            identity => _store.ValidateAndBindSyncIdentity(identity, envelope.Settings)
        );

        if (result.IsSuccess)
        {
            if (
                !_store.ApplySyncResult(
                    result,
                    envelope.QueuedEntities,
                    envelope.Settings.ServerBaseUrl,
                    envelope.Settings
                )
            )
            {
                result = SyncRunResult.Failure(
                    "同步期间服务器配置已变化，本次旧服务器结果未应用。"
                );
                _store.UpdateSyncStatus(result.Message);
            }
        }
        else
        {
            _store.UpdateSyncStatus(result.Message);
        }

        return result;
    }
}
