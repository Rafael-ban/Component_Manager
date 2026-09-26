using System.Text.RegularExpressions;

namespace ComponentVault.WinUI.Services.Catalog;

public static class OfficialSpecifications
{
    private static readonly (string Label, string[] Keys)[] Fields =
    [
        ("阻值", ["阻值", "Resistance"]),
        ("容量", ["容值", "容量", "Capacitance"]),
        ("电感量", ["电感量", "电感值", "Inductance"]),
        ("精度", ["精度", "容差", "误差", "Tolerance"]),
    ];

    public static string AutoName(LcscProductMetadata metadata)
    {
        var values = new List<string>();
        if (metadata.Parameters is not null)
        {
            foreach (var (label, keys) in Fields)
            {
                if (label == "精度") continue;
                var value = metadata.Parameters.FirstOrDefault(pair =>
                    keys.Any(key => pair.Key.Equals(key, StringComparison.OrdinalIgnoreCase))).Value;
                if (ShortValue(value) is { } shortValue) values.Add(shortValue);
            }
        }
        if (values.Count == 0 && metadata.Description is { } description)
        {
            var found = FindPrimary(description, metadata.Category);
            if (found is not null) values.Add(found.Value.Value);
        }
        if (values.Count == 0)
        {
            var rcl = metadata.Category.Contains("电阻", StringComparison.Ordinal)
                || metadata.Category.Contains("电容", StringComparison.Ordinal)
                || metadata.Category.Contains("电感", StringComparison.Ordinal)
                || metadata.Category.Contains("Resistor", StringComparison.OrdinalIgnoreCase)
                || metadata.Category.Contains("Capacitor", StringComparison.OrdinalIgnoreCase)
                || metadata.Category.Contains("Inductor", StringComparison.OrdinalIgnoreCase);
            if (rcl) return metadata.Model is { Length: <= 80 } model ? model : metadata.Sku;
            return metadata.Name.Length <= 100 ? metadata.Name : metadata.Model is { Length: <= 80 } fallback ? fallback : metadata.Sku;
        }
        var tolerance = metadata.Parameters?.FirstOrDefault(pair =>
            Fields[^1].Keys.Any(key => pair.Key.Equals(key, StringComparison.OrdinalIgnoreCase))).Value;
        if (ShortValue(tolerance) is { } shortTolerance) values.Add(shortTolerance);
        else if (metadata.Description is { } source)
        {
            if (FindTolerance(source) is { } found) values.Add(found);
        }
        var name = metadata.Model is { Length: <= 80 } shortModel ? shortModel : metadata.Sku;
        return string.Join(" · ", new[] { name }.Concat(values)
            .Distinct(StringComparer.OrdinalIgnoreCase));
    }

    private static string? ShortValue(string? value) =>
        string.IsNullOrWhiteSpace(value) || value.Trim().Length > 32 ? null : value.Trim();

    public static string FromDescription(string? description, string? category = null)
    {
        if (string.IsNullOrWhiteSpace(description)) return string.Empty;
        var values = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (var rawLine in description.Split(['\r', '\n', '；'], StringSplitOptions.RemoveEmptyEntries))
        {
            var line = rawLine.Trim();
            if (!line.StartsWith("参数·", StringComparison.Ordinal)
                && !line.StartsWith("参数：", StringComparison.Ordinal)
                && !line.StartsWith("参数:", StringComparison.Ordinal)) continue;
            var parameter = line[3..];
            var separator = parameter.IndexOfAny(['：', ':']);
            if (separator <= 0 || separator == parameter.Length - 1) continue;
            var key = parameter[..separator].Trim();
            var value = parameter[(separator + 1)..].Trim();
            foreach (var (label, keys) in Fields)
                if (!values.ContainsKey(label) && keys.Any(candidate => key.Equals(candidate, StringComparison.OrdinalIgnoreCase)))
                    values[label] = value;
        }
        if (values.Count == 0 && category is not null && FindPrimary(description, category) is { } primary)
            values[primary.Label] = primary.Value;
        if (!values.ContainsKey("精度") && values.Count > 0 && FindTolerance(description) is { } tolerance)
            values["精度"] = tolerance;
        return string.Join(" · ", Fields.Where(field => values.ContainsKey(field.Label))
            .Select(field => $"{field.Label} {values[field.Label]}"));
    }

    private static (string Label, string Value)? FindPrimary(string description, string category)
    {
        var (label, pattern) = category switch
        {
            var value when value.Contains("电阻", StringComparison.Ordinal) || value.Contains("Resistor", StringComparison.OrdinalIgnoreCase)
                => ("阻值", @"\d{1,6}(?:\.\d{1,6})?\s*(?:[kKmM]?Ω|[kKmM]?ohm)"),
            var value when value.Contains("电容", StringComparison.Ordinal) || value.Contains("Capacitor", StringComparison.OrdinalIgnoreCase)
                => ("容量", @"\d{1,6}(?:\.\d{1,6})?\s*(?:[pnumµμ]?F)"),
            var value when value.Contains("电感", StringComparison.Ordinal) || value.Contains("Inductor", StringComparison.OrdinalIgnoreCase)
                => ("电感量", @"\d{1,6}(?:\.\d{1,6})?\s*(?:[numµμ]?H)"),
            _ => (string.Empty, string.Empty),
        };
        if (pattern.Length == 0) return null;
        var match = Regex.Match(description, pattern, RegexOptions.IgnoreCase | RegexOptions.CultureInvariant, TimeSpan.FromMilliseconds(50));
        return match.Success ? (label, match.Value.Trim()) : null;
    }

    private static string? FindTolerance(string description)
    {
        var match = Regex.Match(description, @"(?:±|\+/-)\s*\d{1,3}(?:\.\d{1,3})?\s*%",
            RegexOptions.CultureInvariant, TimeSpan.FromMilliseconds(50));
        return match.Success ? match.Value.Trim() : null;
    }
}
