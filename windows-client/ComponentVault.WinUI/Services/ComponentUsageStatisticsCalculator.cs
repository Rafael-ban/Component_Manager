using ComponentVault.WinUI.Models;

namespace ComponentVault.WinUI.Services;

public static class ComponentUsageStatisticsCalculator
{
    public static IReadOnlyDictionary<string, ComponentUsageStatistics> Calculate(
        IEnumerable<ComponentRecord> components,
        IEnumerable<ComponentMovementStatisticInput> movements
    )
    {
        var outboundByComponent = new Dictionary<string, long>(StringComparer.Ordinal);
        foreach (var movement in movements)
        {
            if (movement.Deleted || !movement.MovementType.Equals("outbound", StringComparison.OrdinalIgnoreCase))
                continue;
            var amount = Math.Abs((long)movement.Quantity);
            outboundByComponent[movement.ComponentId] = checked(
                outboundByComponent.GetValueOrDefault(movement.ComponentId) + amount
            );
        }

        return components.ToDictionary(
            component => component.Id,
            component => new ComponentUsageStatistics(
                component.Quantity,
                outboundByComponent.GetValueOrDefault(component.Id)
            ),
            StringComparer.Ordinal
        );
    }
}
