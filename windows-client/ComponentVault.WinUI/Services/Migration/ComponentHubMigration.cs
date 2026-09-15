using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using ComponentVault.WinUI.Models;

namespace ComponentVault.WinUI.Services.Migration;

public enum DuplicateSkuPolicy { Block, Skip }

public sealed record ComponentHubImportItem(
    int Index, string Sku, string Name, string Category, string PackageName,
    string Location, string Description, int Quantity, int MinStock
);

public sealed record ComponentHubPreview(
    string Fingerprint,
    IReadOnlyList<ComponentHubImportItem> Items,
    IReadOnlyList<string> Issues,
    int SkippedDuplicates
)
{
    public bool CanConfirm => Issues.Count == 0 && Items.Count > 0;
}

public sealed record ComponentHubImportRequest(
    string Fingerprint,
    DuplicateSkuPolicy DuplicatePolicy,
    IReadOnlyList<ComponentHubImportItem> Items
);

public sealed class ComponentHubMigrationReader
{
    public ComponentHubPreview Read(
        string filePath,
        IReadOnlyList<ComponentRecord> inventory,
        DuplicateSkuPolicy duplicatePolicy
    )
    {
        var info = new FileInfo(filePath);
        if (!info.Exists) throw new FileNotFoundException("迁移文件不存在。", filePath);
        if (info.Length > Bom.BomFileReader.MaxFileBytes) throw new InvalidDataException("迁移文件超过 10 MiB 限制。");
        var bytes = File.ReadAllBytes(filePath);
        var fingerprint = Convert.ToHexString(SHA256.HashData(bytes));
        using var document = JsonDocument.Parse(bytes, new JsonDocumentOptions { MaxDepth = 32 });
        if (document.RootElement.ValueKind != JsonValueKind.Object || !document.RootElement.TryGetProperty("components", out var components) || components.ValueKind != JsonValueKind.Array)
            throw new InvalidDataException("component-hub 备份根对象必须包含 components 数组。");
        if (components.GetArrayLength() > Bom.BomFileReader.MaxRows) throw new InvalidDataException("迁移记录超过 5000 条限制。");

        var issues = new List<string>();
        var items = new List<ComponentHubImportItem>();
        var known = inventory.Where(item => !item.Deleted).Select(item => item.Sku).ToHashSet(StringComparer.OrdinalIgnoreCase);
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var skipped = 0;
        var index = 0;
        foreach (var element in components.EnumerateArray())
        {
            index++;
            if (element.ValueKind != JsonValueKind.Object) { issues.Add($"第 {index} 条不是对象。"); continue; }
            var name = Text(element, "name");
            var model = Text(element, "model");
            var productCode = Text(element, "productCode");
            var sku = string.IsNullOrWhiteSpace(productCode) ? FallbackSku(element) : productCode.Trim();
            var quantity = Integer(element, "stock");
            var threshold = Integer(element, "threshold");
            if (quantity is null or < 0 || threshold is null or < 0) { issues.Add($"第 {index} 条库存或阈值不是非负整数。"); continue; }
            if (string.IsNullOrWhiteSpace(name)) { issues.Add($"第 {index} 条缺少名称。"); continue; }
            var duplicate = known.Contains(sku) || !seen.Add(sku);
            if (duplicate)
            {
                if (duplicatePolicy == DuplicateSkuPolicy.Skip) { skipped++; continue; }
                issues.Add($"第 {index} 条 SKU {sku} 重复；请选择明确跳过策略后再导入。");
                continue;
            }

            var image = TrustedImage(Text(element, "image"));
            var details = new[]
            {
                Pair("原料号", productCode), Pair("型号", model), Pair("品牌", Text(element, "brand")),
                Pair("原ID", RawField(element, "id")), Pair("原分类", Text(element, "category")),
                Pair("子分类", Text(element, "subCategory")), Pair("参数", RawField(element, "params")),
                Pair("数值", RawField(element, "value")), Pair("原价格", RawField(element, "price")),
                Pair("数据手册", Text(element, "datasheet")), Pair("原图片", Text(element, "image")),
                Pair("原备注", Text(element, "notes")), Pair("商品图片", image),
            }.Where(value => value is not null);
            items.Add(new(
                index, sku, name.Trim(), ChineseCategory(FirstOptional(
                    Text(element, "parentCatalogName"), Text(element, "catalogName"),
                    Text(element, "categoryName"), Text(element, "category")
                )),
                First(Text(element, "encapStandard"), "未知封装"),
                First(Text(element, "location"), "待整理"), string.Join("\n", details!),
                quantity.Value, threshold.Value
            ));
        }
        return new(fingerprint, items, issues, skipped);
    }

    private static string ChineseCategory(string value) => value.Trim().ToLowerInvariant() switch
    {
        "resistor" or "resistors" => "电阻",
        "capacitor" or "capacitors" => "电容",
        "inductor" or "inductors" => "电感",
        "diode" or "diodes" => "二极管",
        "transistor" or "transistors" => "晶体管",
        "ic" or "integrated-circuit" or "integrated-circuits" => "集成电路",
        "connector" or "connectors" => "连接器",
        "sensor" or "sensors" => "传感器",
        var other when other.Length > 0 => value.Trim(),
        _ => "未分类",
    };

    private static string FallbackSku(JsonElement element)
    {
        var sourceId = element.TryGetProperty("id", out var id) && id.ValueKind is JsonValueKind.String or JsonValueKind.Number
            ? RawField(element, "id").Trim() : "";
        var stableSource = sourceId.Length > 0
            ? $"id:{sourceId}"
            : string.Join("|", element.EnumerateObject()
                .Where(property => property.Name is not "createdAt" and not "updatedAt")
                .OrderBy(property => property.Name, StringComparer.Ordinal)
                .Select(property => $"{property.Name}:{property.Value.GetRawText()}"));
        var hash = SHA256.HashData(Encoding.UTF8.GetBytes(stableSource));
        return $"CH-{Convert.ToHexString(hash)[..12]}";
    }
    private static string Text(JsonElement item, string name) => item.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString() ?? "" : "";
    private static int? Integer(JsonElement item, string name) => item.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.Number && value.TryGetInt32(out var number) ? number : null;
    private static string RawField(JsonElement item, string name) => item.TryGetProperty(name, out var value)
        ? value.ValueKind == JsonValueKind.String ? value.GetString() ?? "" : value.GetRawText() : "";
    private static string First(params string[] values) => values.First(value => !string.IsNullOrWhiteSpace(value)).Trim();
    private static string FirstOptional(params string[] values) => values.FirstOrDefault(value => !string.IsNullOrWhiteSpace(value))?.Trim() ?? "";
    private static string? Pair(string label, string value) => string.IsNullOrWhiteSpace(value) ? null : $"{label}：{value.Trim()}";
    private static string TrustedImage(string value) =>
        Catalog.LcscPublicCatalog.TrustedImageUrl(value) ?? "";
}
