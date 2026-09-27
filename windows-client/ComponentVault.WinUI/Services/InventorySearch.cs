using System.Globalization;
using System.Text;
using ComponentVault.WinUI.Localization;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services.Catalog;

namespace ComponentVault.WinUI.Services;

public static class InventorySearch
{
    public static bool Matches(ComponentRecord component, string query)
    {
        var terms = Normalize(query).Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (terms.Length == 0) return true;

        var fields = new[]
        {
            component.Sku, component.Name, component.Category,
            CategoryDisplay.Localize(component.Category), component.PackageName,
            component.Location, component.Description,
            OfficialSpecifications.SearchText(component.Description, component.Category),
        }.Select(Normalize).ToArray();

        return terms.All(term => fields.Any(field =>
            field.Contains(term, StringComparison.Ordinal) ||
            field.Replace(" ", "", StringComparison.Ordinal).Contains(term, StringComparison.Ordinal)));
    }

    private static string Normalize(string value)
    {
        var normalized = value.Normalize(NormalizationForm.FormKC).ToLowerInvariant()
            .Replace('μ', 'u').Replace('µ', 'u').Replace("ω", "ohm", StringComparison.Ordinal);
        var result = new StringBuilder(normalized.Length);
        var spaced = true;
        for (var index = 0; index < normalized.Length; index++)
        {
            var character = normalized[index];
            var decimalPoint = character == '.' && index > 0 && index + 1 < normalized.Length
                && char.IsDigit(normalized[index - 1]) && char.IsDigit(normalized[index + 1]);
            if (char.IsLetterOrDigit(character) || character is 'µ' or '%' || decimalPoint)
            {
                result.Append(character);
                spaced = false;
            }
            else if (!spaced)
            {
                result.Append(' ');
                spaced = true;
            }
        }
        return result.ToString().TrimEnd();
    }
}
