using System.Text;

namespace ComponentVault.WinUI.Services.Bom;

public static class BomShortageCsvExporter
{
    public static byte[] Export(BomDocument document, BomPreview preview)
    {
        var matchedRowNumbers = preview.Lines.SelectMany(line => line.SourceRows).ToHashSet();
        var issues = preview.Issues.GroupBy(issue => issue.RowNumber).ToDictionary(group => group.Key, group => string.Join("；", group.Select(item => item.Message)));
        var rows = new List<string[]> { ["SKU", "型号", "需求数量", "当前库存", "缺料数量", "匹配状态"] };
        foreach (var line in preview.Lines)
        {
            var source = document.Rows.First(row => line.SourceRows.Contains(row.RowNumber));
            var shortage = Math.Max(0, line.RequiredQuantity - line.AvailableQuantity);
            rows.Add([source.Sku ?? "", source.SupplierPartNumber ?? "", line.RequiredQuantity.ToString(), line.AvailableQuantity.ToString(), shortage.ToString(), shortage > 0 ? "缺料" : "库存充足"]);
        }
        foreach (var source in document.Rows.Where(row => !matchedRowNumbers.Contains(row.RowNumber)))
        {
            var required = checked((source.Quantity ?? 0) * preview.BatchQuantity);
            rows.Add([source.Sku ?? "", source.SupplierPartNumber ?? "", required.ToString(), "0", required.ToString(), issues.GetValueOrDefault(source.RowNumber, "未匹配")]);
        }
        var text = string.Join("\r\n", rows.Select(row => string.Join(",", row.Select(Cell)))) + "\r\n";
        return new UTF8Encoding(true).GetPreamble().Concat(Encoding.UTF8.GetBytes(text)).ToArray();
    }

    public static int ShortageCount(BomDocument document, BomPreview preview) =>
        preview.Lines.Count(line => line.AvailableQuantity < line.RequiredQuantity) +
        document.Rows.Count(row => !preview.Lines.Any(line => line.SourceRows.Contains(row.RowNumber)));

    private static string Cell(string raw)
    {
        var trimmed = raw.TrimStart();
        var safe = trimmed.Length > 0 && "=+-@".Contains(trimmed[0]) ? "'" + raw : raw;
        return $"\"{safe.Replace("\"", "\"\"")}\"";
    }
}
