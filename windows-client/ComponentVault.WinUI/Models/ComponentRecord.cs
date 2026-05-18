using ComponentVault.WinUI.Localization;

namespace ComponentVault.WinUI.Models;

public sealed class ComponentRecord
{
    public required string Id { get; init; }
    public required string Sku { get; init; }
    public required string Name { get; init; }
    public required string Category { get; init; }
    public required string PackageName { get; init; }
    public required string Location { get; init; }
    public required string Description { get; init; }
    public required int Quantity { get; init; }
    public required int MinStock { get; init; }
    public required string UpdatedAt { get; init; }
    public required bool Deleted { get; init; }

    public bool IsLowStock => !Deleted && Quantity <= MinStock;

    public string Status => IsLowStock ? "低库存" : "库存正常";

    public string DisplayCategory => CategoryDisplay.Localize(Category);
}
