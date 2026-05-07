namespace ComponentVault.WinUI.Models;

public sealed class DashboardSnapshot
{
    public required int ComponentCount { get; init; }
    public required int TotalUnits { get; init; }
    public required int LowStockCount { get; init; }
    public required int MovementCount { get; init; }
}

