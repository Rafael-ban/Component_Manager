namespace ComponentVault.WinUI.Services;

public sealed class SyncComponentDto
{
    public required string Id { get; init; }
    public required string Sku { get; init; }
    public required string Name { get; init; }
    public required string Category { get; init; }
    public required string PackageName { get; init; }
    public required string Location { get; init; }
    public string? Description { get; init; }
    public required int Quantity { get; init; }
    public required int MinStock { get; init; }
    public required string UpdatedAt { get; init; }
    public required bool Deleted { get; init; }
}

public sealed class SyncStockMovementDto
{
    public required string Id { get; init; }
    public required string ComponentId { get; init; }
    public required string MovementType { get; init; }
    public required int Quantity { get; init; }
    public required string Reason { get; init; }
    public string? Note { get; init; }
    public required string HappenedAt { get; init; }
    public required string UpdatedAt { get; init; }
    public required bool Deleted { get; init; }
}

public sealed class SyncPushRequest
{
    public required string DeviceId { get; init; }
    public IReadOnlyList<SyncComponentDto> Components { get; init; } = [];
    public IReadOnlyList<SyncStockMovementDto> StockMovements { get; init; } = [];
}

public sealed class SyncPushResponse
{
    public required int AcceptedComponents { get; init; }
    public required int AcceptedStockMovements { get; init; }
    public required string ServerTime { get; init; }
}

public sealed class SyncPullResponse
{
    public required string ServerTime { get; init; }
    public long? SyncCursor { get; init; }
    public IReadOnlyList<SyncComponentDto> Components { get; init; } = [];
    public IReadOnlyList<SyncStockMovementDto> StockMovements { get; init; } = [];
}

public sealed class SyncTokenStatusResponse
{
    public required string Status { get; init; }
    public required string ServerTime { get; init; }
}

public sealed class ApiErrorResponse
{
    public string? Detail { get; init; }
}
