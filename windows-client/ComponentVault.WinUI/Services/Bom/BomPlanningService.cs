using ComponentVault.WinUI.Models;

namespace ComponentVault.WinUI.Services.Bom;

public sealed class BomPlanningService
{
    public BomPreview CreatePreview(
        BomDocument document,
        BomImportOptions options,
        IReadOnlyList<ComponentRecord> inventory,
        IReadOnlyDictionary<int, string>? selectedComponentIds = null
    )
    {
        var issues = new List<BomIssue>();
        var candidates = new Dictionary<int, IReadOnlyList<BomMatchCandidate>>();
        if (string.IsNullOrWhiteSpace(options.ProjectName))
        {
            issues.Add(new(null, "invalid_project", "项目名称不能为空。"));
        }
        if (options.BatchQuantity <= 0)
        {
            issues.Add(new(null, "invalid_batch_quantity", "批次数量必须是正整数。"));
        }

        var active = inventory.Where(item => !item.Deleted).ToArray();
        var aggregates = new Dictionary<string, Aggregate>(StringComparer.OrdinalIgnoreCase);
        foreach (var row in document.Rows)
        {
            if (row.Quantity is null or <= 0)
            {
                issues.Add(new(row.RowNumber, "invalid_quantity", "用量必须是正整数。"));
                continue;
            }

            var sku = Normalize(row.Sku);
            var part = Normalize(row.SupplierPartNumber);
            var package = Normalize(row.PackageName);
            if (sku.Length == 0 && (part.Length == 0 || package.Length == 0))
            {
                issues.Add(new(row.RowNumber, "missing_identity", "需要 SKU，或供应商型号与封装。"));
                continue;
            }

            var key = sku.Length > 0 ? $"sku:{sku}" : $"part:{part}|package:{package}";
            if (!aggregates.TryGetValue(key, out var aggregate))
            {
                aggregate = new Aggregate(row, 0, []);
            }
            int unitQuantity;
            try
            {
                unitQuantity = checked(aggregate.UnitQuantity + row.Quantity.Value);
            }
            catch (OverflowException)
            {
                issues.Add(new(row.RowNumber, "quantity_overflow", "聚合用量超出支持范围。"));
                continue;
            }
            aggregate = aggregate with
            {
                UnitQuantity = unitQuantity,
                SourceRows = [.. aggregate.SourceRows, row.RowNumber],
            };
            aggregates[key] = aggregate;
        }

        var matchedLines = new Dictionary<string, BomConsumptionLine>(StringComparer.Ordinal);
        foreach (var aggregate in aggregates.Values)
        {
            var row = aggregate.Row;
            var selectedId = selectedComponentIds?.GetValueOrDefault(aggregate.SourceRows[0]);
            var exact = selectedId is null
                ? FindExactMatches(active, row)
                : active.Where(item => item.Id == selectedId).ToArray();
            if (exact.Count != 1)
            {
                var rowNumber = aggregate.SourceRows[0];
                var code = exact.Count == 0 ? "unmatched" : "ambiguous";
                issues.Add(new(rowNumber, code, exact.Count == 0 ? "未找到精确库存匹配。" : "找到多个精确库存匹配。"));
                candidates[rowNumber] = (exact.Count > 0 ? exact : FindFuzzyCandidates(active, row))
                    .Select(ToCandidate)
                    .ToArray();
                continue;
            }

            var component = exact[0];
            int required;
            try
            {
                required = checked(aggregate.UnitQuantity * options.BatchQuantity);
            }
            catch (OverflowException)
            {
                issues.Add(new(aggregate.SourceRows[0], "quantity_overflow", "批次总用量超出支持范围。"));
                continue;
            }
            var line = new BomConsumptionLine(
                component.Id,
                component.Sku,
                aggregate.UnitQuantity,
                required,
                component.Quantity,
                component.UpdatedAt,
                aggregate.SourceRows
            );
            if (matchedLines.TryGetValue(component.Id, out var existingLine))
            {
                try
                {
                    line = existingLine with
                    {
                        UnitQuantity = checked(existingLine.UnitQuantity + line.UnitQuantity),
                        RequiredQuantity = checked(existingLine.RequiredQuantity + line.RequiredQuantity),
                        SourceRows = [.. existingLine.SourceRows, .. line.SourceRows],
                    };
                }
                catch (OverflowException)
                {
                    issues.Add(new(aggregate.SourceRows[0], "quantity_overflow", "同一库存项的合计用量超出支持范围。"));
                    continue;
                }
            }
            matchedLines[component.Id] = line;
        }

        var lines = matchedLines.Values.ToList();
        foreach (var line in lines.Where(line => line.RequiredQuantity > line.AvailableQuantity))
            issues.Add(new(line.SourceRows[0], "insufficient_stock", $"{line.ComponentSku} 库存不足：需要 {line.RequiredQuantity}，现有 {line.AvailableQuantity}。"));

        if (document.Rows.Count == 0)
        {
            issues.Add(new(null, "empty_bom", "BOM 中没有可读取的数据行。"));
        }
        return new(options.ProjectName.Trim(), options.BatchQuantity, lines, issues, candidates);
    }

    private static IReadOnlyList<ComponentRecord> FindExactMatches(
        IReadOnlyList<ComponentRecord> inventory,
        BomSourceRow row
    )
    {
        var sku = Normalize(row.Sku);
        if (sku.Length > 0)
        {
            return inventory.Where(item => Normalize(item.Sku) == sku).ToArray();
        }

        var part = Normalize(row.SupplierPartNumber);
        var package = Normalize(row.PackageName);
        return inventory.Where(item =>
            Normalize(item.PackageName) == package && ExtractPartNumbers(item.Description).Contains(part)
        ).ToArray();
    }

    private static IReadOnlyList<ComponentRecord> FindFuzzyCandidates(
        IReadOnlyList<ComponentRecord> inventory,
        BomSourceRow row
    )
    {
        var query = Normalize(row.Sku) is { Length: > 0 } sku
            ? sku
            : Normalize(row.SupplierPartNumber);
        if (query.Length < 2)
        {
            return [];
        }
        return inventory.Where(item =>
            Normalize(item.Sku).Contains(query, StringComparison.Ordinal)
            || Normalize(item.Name).Contains(query, StringComparison.Ordinal)
            || Normalize(item.Description).Contains(query, StringComparison.Ordinal)
        ).Take(8).ToArray();
    }

    private static BomMatchCandidate ToCandidate(ComponentRecord component) =>
        new(component.Id, component.Sku, component.Name, component.PackageName);

    private static HashSet<string> ExtractPartNumbers(string description)
    {
        var values = new HashSet<string>(StringComparer.Ordinal);
        foreach (var note in description.Split(['；', ';', '\r', '\n'], StringSplitOptions.RemoveEmptyEntries))
        {
            var trimmed = note.Trim();
            foreach (var prefix in new[] { "型号：", "型号:", "MPN：", "MPN:", "MODEL：", "MODEL:" })
            {
                if (!trimmed.StartsWith(prefix, StringComparison.OrdinalIgnoreCase)) continue;
                var value = Normalize(trimmed[prefix.Length..]);
                if (value.Length > 0) values.Add(value);
                break;
            }
        }
        return values;
    }

    private static string Normalize(string? value) => value?.Trim().ToUpperInvariant() ?? string.Empty;

    private sealed record Aggregate(BomSourceRow Row, int UnitQuantity, IReadOnlyList<int> SourceRows);
}
