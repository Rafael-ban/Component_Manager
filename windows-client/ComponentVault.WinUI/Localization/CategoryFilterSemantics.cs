using System.Globalization;
using ComponentVault.WinUI.Services.Catalog;

namespace ComponentVault.WinUI.Localization;

internal static class CategoryFilterSemantics
{
    internal static string Key(string? category) => LcscPublicCatalog.NormalizeCategory(category?.Trim() ?? string.Empty);

    internal static IReadOnlyList<string> Options(IEnumerable<string> categories) => categories
        .Where(category => !string.IsNullOrWhiteSpace(category))
        .Select(category => category.Trim())
        .GroupBy(Key, StringComparer.OrdinalIgnoreCase)
        .Select(group => group.FirstOrDefault(category => !Key(category).Equals(category, StringComparison.Ordinal)) ?? group.First())
        .OrderBy(category => category, StringComparer.OrdinalIgnoreCase)
        .ToArray();

    internal static bool SameCategory(string rawCategory, string selectedKey) =>
        Key(rawCategory).Equals(Key(selectedKey), StringComparison.OrdinalIgnoreCase);

    internal static bool MatchesSearch(string rawCategory, string query)
    {
        var needle = query.Trim();
        return needle.Length == 0
            || rawCategory.Contains(needle, StringComparison.OrdinalIgnoreCase)
            || Key(rawCategory).Contains(needle, StringComparison.OrdinalIgnoreCase)
            || CategoryDisplay.Localize(rawCategory, CultureInfo.GetCultureInfo("zh-CN")).Contains(needle, StringComparison.OrdinalIgnoreCase);
    }
}
