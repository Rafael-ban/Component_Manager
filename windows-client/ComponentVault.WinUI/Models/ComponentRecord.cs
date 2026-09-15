using ComponentVault.WinUI.Localization;
using ComponentVault.WinUI.Services.Catalog;

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
    public long CumulativeOutboundQuantity { get; init; }

    public bool IsLowStock => !Deleted && Quantity <= MinStock;

    public long StatisticalTotal => checked((long)Quantity + CumulativeOutboundQuantity);

    public double OutboundRatio => StatisticalTotal <= 0
        ? 0
        : Math.Clamp((double)CumulativeOutboundQuantity / StatisticalTotal, 0, 1);

    public string Status => IsLowStock ? "低库存" : "库存正常";

    public string? ProductImageUrl => Description
        .Split(['；', ';', '\r', '\n'], StringSplitOptions.RemoveEmptyEntries)
        .Select(note => note.Trim())
        .FirstOrDefault(note => note.StartsWith("商品图片：", StringComparison.Ordinal))
        ?.Substring("商品图片：".Length)
        .Trim() is { } value
            ? LcscPublicCatalog.TrustedImageUrl(value) ?? LcscPublicCatalog.CachedImageUrl(Sku)
            : LcscPublicCatalog.CachedImageUrl(Sku);

    public string DisplayCategory => CategoryDisplay.Localize(Category);
}
