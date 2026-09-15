namespace ComponentVault.WinUI.Models;

public sealed record ComponentMovementStatisticInput(
    string ComponentId,
    string MovementType,
    int Quantity,
    bool Deleted
);

public sealed record ComponentUsageStatistics(
    long CurrentQuantity,
    long CumulativeOutboundQuantity
)
{
    public long StatisticalTotal => checked(CurrentQuantity + CumulativeOutboundQuantity);
    public double OutboundRatio => StatisticalTotal <= 0
        ? 0
        : Math.Clamp((double)CumulativeOutboundQuantity / StatisticalTotal, 0, 1);
}
