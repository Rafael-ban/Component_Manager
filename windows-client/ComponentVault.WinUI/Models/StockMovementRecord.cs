namespace ComponentVault.WinUI.Models;

public sealed class StockMovementRecord
{
    public required string Id { get; init; }
    public required string ComponentId { get; init; }
    public required string ComponentSku { get; init; }
    public required string ComponentName { get; init; }
    public required string MovementType { get; init; }
    public required int Quantity { get; init; }
    public required string Reason { get; init; }
    public required string Note { get; init; }
    public required string HappenedAt { get; init; }
    public required string UpdatedAt { get; init; }
    public required bool Deleted { get; init; }

    public long QuantityChange => MovementType.Trim().ToLowerInvariant() switch
    {
        "outbound" => -Math.Abs((long)Quantity),
        "inbound" => Math.Abs((long)Quantity),
        _ => Quantity,
    };

    public string QuantityLabel => QuantityChange.ToString("+#,0;-#,0;0");

    public string MovementTypeLabel => MovementType.Trim().ToLowerInvariant() switch
    {
        "inbound" => "入库",
        "outbound" => "出库",
        "adjustment" => "调整",
        _ => MovementType,
    };
}
