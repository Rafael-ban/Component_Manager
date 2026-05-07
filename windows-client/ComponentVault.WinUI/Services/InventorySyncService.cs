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

    public async Task<SyncRunResult> RunSyncAsync(CancellationToken cancellationToken = default)
    {
        var envelope = _store.CreateSyncEnvelope();
        var result = await _apiClient.RunSyncAsync(
            envelope.Settings,
            envelope.PushRequest,
            envelope.Since,
            cancellationToken
        );

        if (result.IsSuccess)
        {
            _store.ApplySyncResult(result, envelope.QueuedEntities);
        }
        else
        {
            _store.UpdateSyncStatus(result.Message);
        }

        return result;
    }
}
