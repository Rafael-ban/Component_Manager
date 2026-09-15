using System.Text.Json;
using System.Text.RegularExpressions;

namespace ComponentVault.WinUI.Services.BatchInbound;

public static partial class JlcQrParser
{
    public static JlcParsedLabel Parse(string raw)
    {
        var text = raw.Trim(); if (text.Length == 0) return Fail("扫码文本为空。");
        if (CPattern().IsMatch(text)) return new(text.ToUpperInvariant(), null, null, null, null, null, null, null, "数量缺失，请手动填写正整数。");
        if (text.StartsWith('{'))
        {
            try
            {
                Dictionary<string, string?> values; try { using var doc = JsonDocument.Parse(text, new JsonDocumentOptions { MaxDepth = 8 }); var root = doc.RootElement; if (root.ValueKind != JsonValueKind.Object) return Fail("QR JSON 必须是对象。"); values = root.EnumerateObject().ToDictionary(x => x.Name, x => x.Value.ValueKind is JsonValueKind.String or JsonValueKind.Number ? x.Value.ToString().Trim() : null, StringComparer.OrdinalIgnoreCase); } catch (JsonException) { values = text.Trim('{', '}').Split(',', StringSplitOptions.RemoveEmptyEntries).Select(part => part.Split(':', 2)).Where(x => x.Length == 2).ToDictionary(x => x[0].Trim().Trim('"', '\''), x => (string?)x[1].Trim().Trim('"', '\''), StringComparer.OrdinalIgnoreCase); if (values.Count == 0) return Fail("QR 对象无法解析。"); }
                string? Get(string key) => values.GetValueOrDefault(key);
                var sku = Get("pc")?.ToUpperInvariant(); if (sku is null || !CPattern().IsMatch(sku)) return Fail("pc 不是合法的立创 C 编号。");
                int? quantity = null; var qty = Get("qty"); if (qty is not null && int.TryParse(qty, System.Globalization.NumberStyles.None, System.Globalization.CultureInfo.InvariantCulture, out var parsed) && parsed > 0) quantity = parsed;
                return new(sku, quantity, Get("on"), Get("pm"), Get("mc"), Get("cc"), Get("on"), Get("pdi"), quantity is null ? "qty 缺失或不是正整数，请手动填写。" : null);
            }
            catch (Exception exception) when (exception is ArgumentException or FormatException) { return Fail("QR 对象无法解析。"); }
        }
        if (Uri.TryCreate(text, UriKind.Absolute, out var uri))
        {
            var matches = CAnywhere().Matches(Uri.UnescapeDataString(uri.AbsoluteUri)).Select(m => m.Value.ToUpperInvariant()).Distinct().ToArray();
            return matches.Length == 1 ? new(matches[0], null, null, null, null, null, null, null, "数量缺失，请手动填写正整数。") : Fail("URI 必须只包含一个明确的 C 编号；订单号或普通数字不能作为 SKU。");
        }
        return Fail("无法识别标准嘉立创 QR、裸 C 编号或包含唯一 C 编号的 URI。");
    }
    private static JlcParsedLabel Fail(string reason) => new("", null, null, null, null, null, null, null, reason);
    [GeneratedRegex("^C[0-9]{1,10}$", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)] private static partial Regex CPattern();
    [GeneratedRegex("(?<![A-Z0-9])C[0-9]{1,10}(?![A-Z0-9])", RegexOptions.IgnoreCase | RegexOptions.CultureInvariant)] private static partial Regex CAnywhere();
}
