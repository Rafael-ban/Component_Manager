using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class ComponentUsageStatisticsTests
{
    [Fact]
    public void Calculate_CountsOnlyValidOutboundMovements()
    {
        var result = ComponentUsageStatisticsCalculator.Calculate(
            [Component("c1", 15)],
            [
                new("c1", "inbound", 7, false),
                new("c1", "outbound", 3, false),
                new("c1", "outbound", -2, false),
                new("c1", "adjustment", -4, false),
                new("c1", "outbound", 99, true),
            ]
        )["c1"];

        Assert.Equal(15, result.CurrentQuantity);
        Assert.Equal(5, result.CumulativeOutboundQuantity);
        Assert.Equal(20, result.StatisticalTotal);
        Assert.Equal(0.25, result.OutboundRatio);
    }

    [Fact]
    public void Calculate_UsesEmptyStateWhenTotalIsZero()
    {
        var result = ComponentUsageStatisticsCalculator.Calculate([Component("c1", 0)], [])["c1"];

        Assert.Equal(0, result.StatisticalTotal);
        Assert.Equal(0, result.OutboundRatio);
    }

    [Fact]
    public void Calculate_UsesLongForLargeOutboundTotals()
    {
        var result = ComponentUsageStatisticsCalculator.Calculate(
            [Component("c1", 1)],
            [new("c1", "outbound", int.MaxValue, false), new("c1", "outbound", int.MaxValue, false)]
        )["c1"];

        Assert.Equal(4_294_967_294L, result.CumulativeOutboundQuantity);
        Assert.Equal(4_294_967_295L, result.StatisticalTotal);
    }

    private static ComponentRecord Component(string id, int quantity) => new()
    {
        Id = id, Sku = id, Name = id, Category = "Test", PackageName = "0603",
        Location = "A", Description = "", Quantity = quantity, MinStock = 0,
        UpdatedAt = "2026-09-15T00:00:00Z", Deleted = false,
    };
}
