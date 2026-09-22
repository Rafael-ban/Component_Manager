using System.Globalization;
using System.Net;
using System.Text.Encodings.Web;
using System.Text.Json;
using ComponentVault.WinUI.Models;

namespace ComponentVault.WinUI.Services;

public enum LabelWorkbookColumn
{
    Name,
    Sku,
    Model,
    PackageName,
    Category,
    Location,
    Quantity,
    LongQrText,
    ShortQrText,
}

public static class LabelWorkbookExporter
{
    public static IReadOnlySet<LabelWorkbookColumn> DefaultColumns { get; } = new HashSet<LabelWorkbookColumn>
    {
        LabelWorkbookColumn.Name,
        LabelWorkbookColumn.Sku,
        LabelWorkbookColumn.LongQrText,
        LabelWorkbookColumn.ShortQrText,
    };

    public static void Export(string path, IEnumerable<ComponentRecord> components, IReadOnlySet<LabelWorkbookColumn> selectedColumns)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(path);
        if (selectedColumns.Count == 0) throw new InvalidOperationException("请至少选择一个导出字段。");
        var columns = Enum.GetValues<LabelWorkbookColumn>().Where(selectedColumns.Contains).ToArray();
        var rows = new List<object?[]> { columns.Select(Header).Cast<object?>().ToArray() };
        foreach (var component in components.Where(component => !component.Deleted))
        {
            var metadata = ParseDescription(component.Description);
            rows.Add(columns.Select(column => Value(column, component, metadata)).ToArray());
        }
        SimpleXlsx.Write(path, new Dictionary<string, IReadOnlyList<object?[]>> { ["标签打印数据"] = rows });
    }

    internal static string LongQrText(ComponentRecord component)
    {
        var metadata = ParseDescription(component.Description);
        if (metadata.SourceLabel?.Contains("JLC", StringComparison.OrdinalIgnoreCase) == true ||
            metadata.RawPayload?.TrimStart().StartsWith("{on:", StringComparison.OrdinalIgnoreCase) == true)
        {
            return BuildJlcCompatiblePayload(component, metadata);
        }
        var payload = new Dictionary<string, object?>
        {
            ["fmt"] = "component-vault-label", ["v"] = 1, ["sku"] = component.Sku,
            ["name"] = component.Name, ["cat"] = component.Category, ["pkg"] = component.PackageName,
            ["loc"] = component.Location, ["qty"] = component.Quantity, ["min"] = component.MinStock,
            ["model"] = metadata.Model, ["brand"] = metadata.Brand,
        };
        return JsonSerializer.Serialize(payload, new JsonSerializerOptions { Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping });
    }

    internal static string ShortQrText(ComponentRecord component) =>
        string.Join('|', "cvl3", EncodeCompact(component.Sku), component.Quantity.ToString(CultureInfo.InvariantCulture));

    private static object Value(LabelWorkbookColumn column, ComponentRecord component, LabelMetadata metadata) => column switch
    {
        LabelWorkbookColumn.Name => component.Name,
        LabelWorkbookColumn.Sku => component.Sku,
        LabelWorkbookColumn.Model => metadata.Model ?? string.Empty,
        LabelWorkbookColumn.PackageName => component.PackageName,
        LabelWorkbookColumn.Category => component.Category,
        LabelWorkbookColumn.Location => component.Location,
        LabelWorkbookColumn.Quantity => component.Quantity,
        LabelWorkbookColumn.LongQrText => LongQrText(component),
        LabelWorkbookColumn.ShortQrText => ShortQrText(component),
        _ => throw new ArgumentOutOfRangeException(nameof(column)),
    };

    private static string Header(LabelWorkbookColumn column) => column switch
    {
        LabelWorkbookColumn.Name => "名称", LabelWorkbookColumn.Sku => "SKU", LabelWorkbookColumn.Model => "型号",
        LabelWorkbookColumn.PackageName => "封装", LabelWorkbookColumn.Category => "分类", LabelWorkbookColumn.Location => "库位",
        LabelWorkbookColumn.Quantity => "当前数量", LabelWorkbookColumn.LongQrText => "长二维码文本",
        LabelWorkbookColumn.ShortQrText => "短二维码文本", _ => throw new ArgumentOutOfRangeException(nameof(column)),
    };

    private static LabelMetadata ParseDescription(string description)
    {
        string? model = null, brand = null, sourceLabel = null, rawPayload = null;
        foreach (var rawLine in description.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries))
        {
            var line = rawLine.Trim();
            if (line.StartsWith("型号：", StringComparison.Ordinal)) model = BlankToNull(line[3..].Trim());
            else if (line.StartsWith("Model: ", StringComparison.Ordinal)) model = BlankToNull(line[7..].Trim());
            else if (line.StartsWith("品牌：", StringComparison.Ordinal)) brand = BlankToNull(line[3..].Trim());
            else if (line.StartsWith("Brand: ", StringComparison.Ordinal)) brand = BlankToNull(line[7..].Trim());
            else if (line.StartsWith("导入来源：", StringComparison.Ordinal)) sourceLabel = BlankToNull(line[5..].Trim());
            else if (line.StartsWith("Import source: ", StringComparison.Ordinal)) sourceLabel = BlankToNull(line[15..].Trim());
            else if (line.StartsWith("原始载荷：", StringComparison.Ordinal)) rawPayload = BlankToNull(line[5..].Trim());
            else if (line.StartsWith("Raw payload: ", StringComparison.Ordinal)) rawPayload = BlankToNull(line[13..].Trim());
        }
        return new(model, brand, sourceLabel, rawPayload);
    }

    private static string BuildJlcCompatiblePayload(ComponentRecord component, LabelMetadata metadata)
    {
        var values = new (string Key, string? Value)[]
        {
            ("on", $"local-{component.Sku}"), ("pc", component.Sku), ("pm", metadata.Model ?? component.Name),
            ("qty", component.Quantity.ToString(CultureInfo.InvariantCulture)), ("mc", null), ("cc", "1"),
            ("pdi", component.Sku), ("hp", null), ("pkg", component.PackageName), ("nm", component.Name),
            ("br", metadata.Brand), ("cat", component.Category), ("loc", component.Location),
        };
        return "{" + string.Join(',', values.Select(pair => $"{pair.Key}:{EncodeJlcValue(pair.Value)}")) + "}";
    }

    private static string EncodeJlcValue(string? value)
    {
        var normalized = value?.Trim() ?? string.Empty;
        return normalized.Length == 0 ? "null" : WebUtility.UrlEncode(normalized);
    }

    private static string EncodeCompact(string value)
    {
        var normalized = value.Trim();
        return normalized.Length == 0 ? "-" : WebUtility.UrlEncode(normalized);
    }

    private static string? BlankToNull(string value) => value.Length == 0 ? null : value;
    private sealed record LabelMetadata(string? Model, string? Brand, string? SourceLabel, string? RawPayload);
}
