using System.Globalization;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using Microsoft.Data.Sqlite;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class InventoryStoreSyncTests : IDisposable
{
    private readonly string _testRoot = Path.Combine(
        Path.GetTempPath(),
        $"component-vault-tests-{Guid.NewGuid():N}"
    );

    [Fact]
    public void ApplySyncResult_PreservesQueueEntryEditedAfterSnapshot()
    {
        var store = CreateStore();
        var component = store.SaveComponent(CreateDraft("first"));
        var snapshot = store.CreateSyncEnvelope();

        store.SaveComponent(CreateDraft("second", component.Id));
        store.ApplySyncResult(CreateSuccess(syncCursor: 7), snapshot.QueuedEntities, string.Empty);

        var remaining = store.CreateSyncEnvelope();
        var queued = Assert.Single(remaining.QueuedEntities);
        Assert.NotEqual(snapshot.QueuedEntities[0].EntityUpdatedAt, queued.EntityUpdatedAt);
        Assert.Equal("second", Assert.Single(remaining.PushRequest.Components).Description);
        Assert.Equal(7, remaining.Cursor);
    }

    [Fact]
    public void ApplySyncResult_AcknowledgesEquivalentTimestampFormats()
    {
        var store = CreateStore();
        store.SaveComponent(CreateDraft("first"));
        var snapshot = Assert.Single(store.CreateSyncEnvelope().QueuedEntities);
        var instant = DateTimeOffset.Parse(snapshot.EntityUpdatedAt, CultureInfo.InvariantCulture);
        var equivalentSnapshot = new SyncEntityReference
        {
            EntityType = snapshot.EntityType,
            EntityId = snapshot.EntityId,
            EntityUpdatedAt = instant.ToOffset(TimeSpan.FromHours(8)).ToString("O"),
        };

        store.ApplySyncResult(CreateSuccess(syncCursor: 1), [equivalentSnapshot], string.Empty);

        Assert.Empty(store.CreateSyncEnvelope().QueuedEntities);
    }

    [Fact]
    public void SyncCursor_IsIndependentFromDisplayTimeAndClearedForNewServer()
    {
        var store = CreateStore();
        Assert.Null(store.CreateSyncEnvelope().Cursor);

        store.ApplySyncResult(CreateSuccess(syncCursor: 12), [], string.Empty);
        Assert.Equal(12, store.CreateSyncEnvelope().Cursor);

        store.ApplySyncResult(CreateSuccess(syncCursor: null), [], string.Empty);
        Assert.Null(store.CreateSyncEnvelope().Cursor);

        store.ApplySyncResult(CreateSuccess(syncCursor: 13), [], string.Empty);

        store.SaveSyncConfiguration("https://new-server.example/", "token", true);
        Assert.Null(store.CreateSyncEnvelope().Cursor);
    }

    [Fact]
    public void ApplySyncResult_RejectsResponseAfterServerChanges()
    {
        var store = CreateStore();
        store.SaveSyncConfiguration("https://old.example/", "token", true);
        store.SaveComponent(CreateDraft("queued"));
        var snapshot = store.CreateSyncEnvelope();

        store.SaveSyncConfiguration("https://new.example/", "token", true);
        var applied = store.ApplySyncResult(
            CreateSuccess(syncCursor: 99),
            snapshot.QueuedEntities,
            snapshot.Settings.ServerBaseUrl
        );

        Assert.False(applied);
        var current = store.CreateSyncEnvelope();
        Assert.Null(current.Cursor);
        Assert.Single(current.QueuedEntities);
    }

    public void Dispose()
    {
        SqliteConnection.ClearAllPools();
        if (Directory.Exists(_testRoot))
        {
            Directory.Delete(_testRoot, recursive: true);
        }
    }

    private InventoryStore CreateStore()
    {
        var store = new InventoryStore(Path.Combine(_testRoot, "inventory.db"));
        store.Initialize();
        return store;
    }

    private static ComponentDraft CreateDraft(string description, string? id = null) =>
        new()
        {
            Id = id,
            Sku = "TEST-1",
            Name = "Test component",
            Category = "Test",
            PackageName = "DIP",
            Location = "A1",
            Description = description,
            Quantity = 1,
            MinStock = 0,
        };

    private static SyncRunResult CreateSuccess(long? syncCursor) =>
        new()
        {
            IsSuccess = true,
            Message = "ok",
            PullResponse = new SyncPullResponse
            {
                ServerTime = "2026-09-15T00:00:00Z",
                SyncCursor = syncCursor,
            },
        };
}
