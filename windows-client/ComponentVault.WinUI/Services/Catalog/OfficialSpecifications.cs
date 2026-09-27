using System.Globalization;
using System.Text.RegularExpressions;

namespace ComponentVault.WinUI.Services.Catalog;

public sealed record SpecificationField(string Key, string Value)
{
    public string Label => OfficialSpecifications.LocalizeLabel(Key, CultureInfo.CurrentUICulture);
}

public static class OfficialSpecifications
{
    private static readonly IReadOnlyDictionary<string, string> EnglishLabels = new Dictionary<string, string>
    {
        ["阻值"] = "Resistance", ["容量"] = "Capacitance", ["电感量"] = "Inductance",
        ["耐压"] = "Voltage rating", ["精度"] = "Tolerance", ["功率"] = "Power rating",
    };

    public static string LocalizeLabel(string key, CultureInfo culture) =>
        culture.TwoLetterISOLanguageName.Equals("zh", StringComparison.OrdinalIgnoreCase)
            ? key
            : EnglishLabels.GetValueOrDefault(key, key);

    private static readonly (string Label, string[] Keys)[] Fields =
    [
        ("阻值", ["阻值", "电阻值", "Resistance"]),
        ("容量", ["容值", "容量", "电容量", "Capacitance"]),
        ("电感量", ["电感量", "电感值", "感值", "Inductance"]),
        ("耐压", ["耐压", "额定电压", "工作电压", "Voltage Rating", "Rated Voltage", "Voltage"]),
        ("精度", ["精度", "容差", "误差", "阻值精度", "Tolerance"]),
        ("功率", ["功率", "额定功率", "Power Rating", "Rated Power", "Power"]),
    ];

    public static string AutoName(LcscProductMetadata metadata)
    {
        var model = metadata.Model?.Trim();
        if (string.IsNullOrWhiteSpace(model) || model.Length > 80)
            model = metadata.Name?.Trim();
        if (string.IsNullOrWhiteSpace(model) || model.Length > 100)
            model = metadata.Sku;
        var brand = metadata.Brand?.Trim();
        return string.IsNullOrWhiteSpace(brand) || model.StartsWith(brand + " ", StringComparison.OrdinalIgnoreCase)
            ? model
            : $"{brand} {model}";
    }

    public static IReadOnlyList<SpecificationField> Parse(string? description, string? category = null)
    {
        if (string.IsNullOrWhiteSpace(description)) return [];
        var values = new Dictionary<string, string>(StringComparer.Ordinal);
        string? officialDescription = null;
        foreach (var rawLine in description.Split(['\r', '\n', '；'], StringSplitOptions.RemoveEmptyEntries))
        {
            var line = rawLine.Trim();
            if (line.StartsWith("官方描述：", StringComparison.Ordinal))
                officialDescription = line[5..].Trim();
            if (!line.StartsWith("参数·", StringComparison.Ordinal)
                && !line.StartsWith("参数：", StringComparison.Ordinal)
                && !line.StartsWith("参数:", StringComparison.Ordinal)) continue;
            var parameter = line[3..];
            var separator = parameter.IndexOfAny(['：', ':']);
            if (separator <= 0 || separator == parameter.Length - 1) continue;
            var rawKey = parameter[..separator].Trim();
            var key = Regex.Replace(rawKey, @"\s*[（(][^）)]*[）)]\s*$", "").Trim();
            var value = parameter[(separator + 1)..].Trim();
            foreach (var (label, keys) in Fields)
            {
                if (values.ContainsKey(label) || !keys.Any(candidate => key.Equals(candidate, StringComparison.OrdinalIgnoreCase))) continue;
                if (value.Length is 0 or > 64) break;
                var unit = Regex.Match(rawKey, @"[（(]\s*(Ω|ohm|[pnumµμ]?F|[numµμ]?H|m?V|m?W)\s*[）)]$",
                    RegexOptions.IgnoreCase | RegexOptions.CultureInvariant, TimeSpan.FromMilliseconds(50));
                if (unit.Success && Regex.IsMatch(value, @"^\d{1,8}(?:\.\d{1,8})?$"))
                    value += unit.Groups[1].Value;
                values[label] = value;
                break;
            }
        }

        // Only an explicitly stored official description may supply missing values.
        if (officialDescription is not null && category is not null)
        {
            if (FindPrimary(officialDescription, category) is { } primary)
                values.TryAdd(primary.Label, primary.Value);
            if (values.Count > 0 && FindTolerance(officialDescription) is { } tolerance)
                values.TryAdd("精度", tolerance);
            if (IsCategory(category, "电容", "Capacitor") && FindUnit(officialDescription, @"\d{1,6}(?:\.\d{1,6})?\s*(?:mV|V)") is { } voltage)
                values.TryAdd("耐压", voltage);
            if (IsCategory(category, "电阻", "Resistor") && FindUnit(officialDescription, @"\d{1,6}(?:\.\d{1,6})?\s*(?:mW|W)") is { } power)
                values.TryAdd("功率", power);
        }
        return Fields.Where(field => values.ContainsKey(field.Label))
            .Select(field => new SpecificationField(field.Label, values[field.Label])).ToArray();
    }

    public static string FromDescription(string? description, string? category = null, CultureInfo? culture = null) =>
        string.Join(" · ", Parse(description, category).Select(field =>
            $"{LocalizeLabel(field.Key, culture ?? CultureInfo.CurrentUICulture)} {field.Value}"));

    public static string SearchText(string? description, string? category = null) =>
        string.Join(" ", Parse(description, category).SelectMany(field =>
            new[] { $"{field.Key} {field.Value}", $"{EnglishLabels[field.Key]} {field.Value}" }));

    private static bool IsCategory(string category, string chinese, string english) =>
        category.Contains(chinese, StringComparison.Ordinal) || category.Contains(english, StringComparison.OrdinalIgnoreCase);

    private static (string Label, string Value)? FindPrimary(string description, string category)
    {
        var (label, pattern) = category switch
        {
            var value when IsCategory(value, "电阻", "Resistor")
                => ("阻值", @"\d{1,6}(?:\.\d{1,6})?\s*(?:[kKmM]?Ω|[kKmM]?ohm)"),
            var value when IsCategory(value, "电容", "Capacitor")
                => ("容量", @"\d{1,6}(?:\.\d{1,6})?\s*(?:[pnumµμ]?F)"),
            var value when IsCategory(value, "电感", "Inductor")
                => ("电感量", @"\d{1,6}(?:\.\d{1,6})?\s*(?:[numµμ]?H)"),
            _ => (string.Empty, string.Empty),
        };
        return pattern.Length == 0 ? null : FindUnit(description, pattern) is { } found ? (label, found) : null;
    }

    private static string? FindTolerance(string description) =>
        FindUnit(description, @"(?:±|\+/-)\s*\d{1,3}(?:\.\d{1,3})?\s*%");

    private static string? FindUnit(string description, string pattern)
    {
        var match = Regex.Match(description, $@"(?<![\p{{L}}\p{{N}}_])(?:{pattern})(?![\p{{L}}\p{{N}}_])", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant,
            TimeSpan.FromMilliseconds(50));
        return match.Success ? match.Value.Trim() : null;
    }
}
