using System.Net;
using System.Collections.Concurrent;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace ComponentVault.WinUI.Services.Catalog;

public sealed record LcscProductMetadata(
    string Sku, string Name, string? Model, string? Brand, string? PackageName,
    string Category, string? CategoryPath, string OfficialUrl, string? ImageUrl
);

public static partial class LcscPublicCatalog
{
    private static readonly ConcurrentDictionary<string, string> ImageCache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly IReadOnlyDictionary<string, string> ExactCategories =
        new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            ["crystals, oscillators, resonators/crystals"] = "晶体",
            ["capacitors/ceramic capacitors"] = "电容",
            ["resistors/chip resistor - surface mount"] = "电阻",
            ["integrated circuits (ics)/embedded/microcontrollers"] = "微控制器",
            ["LED Drivers/LED Drivers ICs"] = "LED驱动",
        };

    public static string? NormalizeSku(string? value) =>
        value?.Trim().ToUpperInvariant() is { } sku && SkuPattern().IsMatch(sku) ? sku : null;

    public static Uri? ProductUri(string? sku) => NormalizeSku(sku) is { } normalized
        ? new Uri($"https://www.lcsc.com/product-detail/{normalized}.html")
        : null;

    public static Uri? ChinaSearchUri(string? sku) => NormalizeSku(sku) is { } normalized
        ? new Uri($"https://so.szlcsc.com/global.html?k={Uri.EscapeDataString(normalized)}")
        : null;

    public static string? TrustedImageUrl(string? value)
    {
        if (!Uri.TryCreate(value?.Trim(), UriKind.Absolute, out var uri)) return null;
        return uri.Scheme == Uri.UriSchemeHttps
            && uri.UserInfo.Length == 0
            && uri.IsDefaultPort
            && (uri.Host.Equals("assets.lcsc.com", StringComparison.OrdinalIgnoreCase)
                || uri.Host.Equals("www.lcsc.com", StringComparison.OrdinalIgnoreCase))
            ? uri.AbsoluteUri : null;
    }

    public static string NormalizeCategory(string? officialPath)
    {
        var path = officialPath?.Trim() ?? string.Empty;
        if (path.Length == 0 || path.Any(character => character is >= '\u4e00' and <= '\u9fff')) return path;
        return ExactCategories.GetValueOrDefault(path, path);
    }

    public static string? CachedImageUrl(string? sku) =>
        NormalizeSku(sku) is { } normalized && ImageCache.TryGetValue(normalized, out var value)
            ? value : null;

    internal static void CacheImage(string sku, string? imageUrl)
    {
        if (NormalizeSku(sku) is { } normalized && TrustedImageUrl(imageUrl) is { } trusted)
        {
            ImageCache[normalized] = trusted;
            if (ImageCache.Count > 64)
                foreach (var key in ImageCache.Keys.Take(ImageCache.Count - 64)) ImageCache.TryRemove(key, out _);
        }
    }

    public static LcscProductMetadata? ParsePage(string sku, string html)
    {
        var expected = NormalizeSku(sku);
        if (expected is null) return null;
        foreach (Match match in JsonLdPattern().Matches(html))
        {
            try
            {
                using var document = JsonDocument.Parse(WebUtility.HtmlDecode(match.Groups[1].Value), new JsonDocumentOptions { MaxDepth = 32 });
                foreach (var product in Products(document.RootElement))
                {
                    if (NormalizeSku(Text(product, "sku")) != expected) continue;
                    var model = Text(product, "mpn");
                    var name = Text(product, "description") ?? Text(product, "name");
                    if (string.IsNullOrWhiteSpace(name) || name.Equals(expected, StringComparison.OrdinalIgnoreCase) || name.Equals(model, StringComparison.OrdinalIgnoreCase)) continue;
                    var brand = product.TryGetProperty("brand", out var brandNode) && brandNode.ValueKind == JsonValueKind.Object ? Text(brandNode, "name") : Text(product, "brand");
                    var categoryPath = Text(product, "category");
                    var package = AdditionalPackage(product);
                    return new(expected, name, model, brand, package, NormalizeCategory(categoryPath), categoryPath, ProductUri(expected)!.AbsoluteUri, ProductImage(product));
                }
            }
            catch (JsonException) { }
        }
        return null;
    }

    private static IEnumerable<JsonElement> Products(JsonElement node)
    {
        if (node.ValueKind == JsonValueKind.Array)
            foreach (var item in node.EnumerateArray()) foreach (var product in Products(item)) yield return product;
        if (node.ValueKind != JsonValueKind.Object) yield break;
        if (IsProduct(node)) yield return node;
        if (node.TryGetProperty("@graph", out var graph)) foreach (var product in Products(graph)) yield return product;
    }

    private static bool IsProduct(JsonElement node)
    {
        if (!node.TryGetProperty("@type", out var type)) return false;
        return type.ValueKind == JsonValueKind.String && type.GetString() == "Product"
            || type.ValueKind == JsonValueKind.Array && type.EnumerateArray().Any(item => item.GetString() == "Product");
    }

    private static string? AdditionalPackage(JsonElement product)
    {
        if (!product.TryGetProperty("additionalProperty", out var properties) || properties.ValueKind != JsonValueKind.Array) return null;
        foreach (var property in properties.EnumerateArray())
            if (Text(property, "name")?.Equals("Package", StringComparison.OrdinalIgnoreCase) == true) return Text(property, "value");
        return null;
    }

    private static string? ProductImage(JsonElement product)
    {
        if (!product.TryGetProperty("image", out var image)) return null;
        if (image.ValueKind == JsonValueKind.String) return TrustedImageUrl(image.GetString());
        if (image.ValueKind == JsonValueKind.Array)
            foreach (var item in image.EnumerateArray()) { var found = item.ValueKind == JsonValueKind.String ? TrustedImageUrl(item.GetString()) : ProductImageObject(item); if (found is not null) return found; }
        return ProductImageObject(image);
    }

    private static string? ProductImageObject(JsonElement image) => image.ValueKind == JsonValueKind.Object ? TrustedImageUrl(Text(image, "contentUrl") ?? Text(image, "url")) : null;
    private static string? Text(JsonElement node, string name) => node.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String ? value.GetString()?.Trim() : null;

    [GeneratedRegex("^C[0-9]{1,10}$", RegexOptions.CultureInvariant)] private static partial Regex SkuPattern();
    [GeneratedRegex("<script\\b[^>]*\\btype\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script\\s*>", RegexOptions.IgnoreCase | RegexOptions.Singleline | RegexOptions.CultureInvariant)] private static partial Regex JsonLdPattern();
}

public sealed class LcscPublicLookup
{
    private readonly HttpClient _client;
    private static readonly ConcurrentDictionary<string, (DateTimeOffset At, LcscProductMetadata? Value)> Cache = new();
    public LcscPublicLookup()
    {
        _client = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = TimeSpan.FromSeconds(6) };
        _client.DefaultRequestHeaders.UserAgent.ParseAdd("ComponentVault-Windows/0.3");
    }

    public async Task<LcscProductMetadata?> LookupAsync(string sku, CancellationToken cancellationToken = default)
    {
        var normalized = LcscPublicCatalog.NormalizeSku(sku);
        var uri = LcscPublicCatalog.ProductUri(normalized);
        if (normalized is null || uri is null) return null;
        if (Cache.TryGetValue(normalized, out var cached) && DateTimeOffset.UtcNow - cached.At < (cached.Value is null ? TimeSpan.FromSeconds(30) : TimeSpan.FromDays(7))) return cached.Value;
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(6));
        using var response = await _client.GetAsync(uri, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
        response.EnsureSuccessStatusCode();
        if (response.Content.Headers.ContentLength is > 2 * 1024 * 1024) throw new InvalidDataException("商品页面超过 2 MiB 限制。");
        using var body = await response.Content.ReadAsStreamAsync(timeout.Token);
        using var output = new MemoryStream();
        var buffer = new byte[8192];
        int count;
        while ((count = await body.ReadAsync(buffer.AsMemory(), timeout.Token)) > 0)
        {
            if (output.Length + count > 2 * 1024 * 1024) throw new InvalidDataException("商品页面超过 2 MiB 限制。");
            output.Write(buffer, 0, count);
        }
        var html = System.Text.Encoding.UTF8.GetString(output.ToArray());
        var metadata = LcscPublicCatalog.ParsePage(normalized, html);
        LcscPublicCatalog.CacheImage(normalized, metadata?.ImageUrl);
        Cache[normalized] = (DateTimeOffset.UtcNow, metadata);
        if (Cache.Count > 64) Cache.TryRemove(Cache.OrderBy(pair => pair.Value.At).First().Key, out _);
        return metadata;
    }
}
