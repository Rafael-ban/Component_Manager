using System.Collections.Concurrent;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using ComponentVault.WinUI.Services;

namespace ComponentVault.WinUI.Services.Catalog;

public sealed record LcscProductMetadata(
    string Sku, string Name, string? Model, string? Brand, string? PackageName,
    string Category, string? CategoryPath, string OfficialUrl, string? ImageUrl,
    string? DatasheetUrl = null, IReadOnlyDictionary<string, string>? Parameters = null
);

public sealed class LcscDomesticBlockedException : IOException
{
    public LcscDomesticBlockedException() : base("LCSC verification page returned") { }
}

public static partial class LcscPublicCatalog
{
    private static readonly ConcurrentDictionary<string, string> ImageCache = new(StringComparer.OrdinalIgnoreCase);
    private static readonly IReadOnlyDictionary<string, string> ExactCategories = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
    {
        ["crystals, oscillators, resonators/crystals"] = "晶体",
        ["capacitors/ceramic capacitors"] = "电容",
        ["resistors/chip resistor - surface mount"] = "电阻",
        ["integrated circuits (ics)/embedded/microcontrollers"] = "微控制器",
        ["LED Drivers/LED Drivers ICs"] = "LED驱动",
    };
    private static readonly HashSet<string> TrustedImageHosts = new(StringComparer.OrdinalIgnoreCase)
    {
        "assets.lcsc.com", "www.lcsc.com", "img.szlcsc.com", "image.szlcsc.com", "static.szlcsc.com",
    };

    public static string? NormalizeSku(string? value) =>
        value?.Trim().ToUpperInvariant() is { } sku && SkuPattern().IsMatch(sku) ? sku : null;

    public static Uri? ProductUri(string? sku) => NormalizeSku(sku) is { } normalized
        ? new Uri($"https://www.lcsc.com/product-detail/{normalized}.html") : null;

    public static Uri? ChinaSearchUri(string? keyword) => keyword?.Trim() is { Length: > 0 } normalized
        ? new Uri($"https://so.szlcsc.com/global.html?k={Uri.EscapeDataString(normalized)}") : null;

    public static string? TrustedImageUrl(string? value) => TrustedHttpsUrl(value, TrustedImageHosts);
    public static string? TrustedDatasheetUrl(string? value) => TrustedHttpsUrl(value, new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "atta.szlcsc.com" });

    private static string? TrustedHttpsUrl(string? value, IReadOnlySet<string> hosts)
    {
        if (!Uri.TryCreate(value?.Trim(), UriKind.Absolute, out var uri)) return null;
        return uri.Scheme == Uri.UriSchemeHttps && uri.UserInfo.Length == 0 && uri.IsDefaultPort && hosts.Contains(uri.Host) ? uri.AbsoluteUri : null;
    }

    public static string NormalizeCategory(string? officialPath)
    {
        var path = CleanText(officialPath) ?? string.Empty;
        if (path.Length == 0 || path.Any(character => character is >= '\u4e00' and <= '\u9fff')) return path;
        return ExactCategories.GetValueOrDefault(path, path);
    }

    public static string? CachedImageUrl(string? sku) => NormalizeSku(sku) is { } normalized && ImageCache.TryGetValue(normalized, out var value) ? value : null;

    internal static void CacheImage(string sku, string? imageUrl)
    {
        if (NormalizeSku(sku) is { } normalized && TrustedImageUrl(imageUrl) is { } trusted)
        {
            ImageCache[normalized] = trusted;
            if (ImageCache.Count > 64)
                foreach (var key in ImageCache.Keys.Take(ImageCache.Count - 64)) ImageCache.TryRemove(key, out _);
        }
    }

    public static IReadOnlyList<LcscProductMetadata> ParseChinaSearchPage(string html)
    {
        if (LooksLikeVerificationPage(html)) throw new LcscDomesticBlockedException();
        var match = NextDataPattern().Match(html);
        if (!match.Success) throw new InvalidDataException("国内商城页面缺少 __NEXT_DATA__ 搜索数据。");
        try
        {
            using var document = JsonDocument.Parse(match.Groups[1].Value, new JsonDocumentOptions { MaxDepth = 64 });
            if (!TryProperty(document.RootElement, out var records, "props", "pageProps", "soData", "searchResult", "productRecordList") || records.ValueKind != JsonValueKind.Array)
                throw new InvalidDataException("国内商城页面不包含预期的搜索结果结构。");
            return records.EnumerateArray().Select(ParseChinaRecord).Where(item => item is not null).Cast<LcscProductMetadata>().Take(20).ToArray();
        }
        catch (JsonException exception) { throw new InvalidDataException("国内商城搜索数据不是有效 JSON。", exception); }
    }

    public static LcscProductMetadata? ExactChinaMatch(string sku, IEnumerable<LcscProductMetadata> products)
    {
        var expected = NormalizeSku(sku);
        return expected is null ? null : products.FirstOrDefault(item => item.Sku.Equals(expected, StringComparison.OrdinalIgnoreCase));
    }

    private static LcscProductMetadata? ParseChinaRecord(JsonElement record)
    {
        if (!record.TryGetProperty("productVO", out var product) || product.ValueKind != JsonValueKind.Object) return null;
        var sku = NormalizeSku(Text(product, "productCode"));
        var productId = Text(product, "productId");
        if (sku is null || productId is null || !productId.All(char.IsAsciiDigit)) return null;
        var parameters = StringMap(record, "paramLinkedMap");
        var model = CleanText(Text(record, "lightProductModel")) ?? CleanText(Text(product, "productModel"));
        var categoryPath = CleanText(Text(record, "lightCatalogName")) ?? CleanText(Text(product, "productType"));
        var name = CleanText(Text(record, "lightProductName")) ?? CleanText(Text(product, "productName")) ?? model ?? sku;
        var brand = CleanText(Text(record, "lightBrandName")) ?? CleanText(Text(product, "productGradePlateName"));
        var packageName = CleanText(Text(record, "lightStandard")) ?? CleanText(Text(product, "encapsulationModel"))
            ?? parameters.FirstOrDefault(pair => pair.Key.Contains("封装", StringComparison.Ordinal) || pair.Key.Equals("Package", StringComparison.OrdinalIgnoreCase)).Value;
        return new(sku, name, model, brand, packageName, NormalizeCategory(categoryPath), categoryPath,
            $"https://item.szlcsc.com/{productId}.html", TrustedImageUrl(Text(product, "breviaryImageUrl")), ProductDatasheet(product), parameters);
    }

    private static string? ProductDatasheet(JsonElement product)
    {
        if (!product.TryGetProperty("fileTypeVOList", out var groups) || groups.ValueKind != JsonValueKind.Array) return null;
        foreach (var group in groups.EnumerateArray())
        {
            if (!group.TryGetProperty("detailVOList", out var details) || details.ValueKind != JsonValueKind.Array) continue;
            foreach (var detail in details.EnumerateArray())
            {
                var raw = Text(detail, "fileUrl");
                if (raw is null) continue;
                var candidate = raw.StartsWith("https://", StringComparison.OrdinalIgnoreCase) ? raw : $"https://atta.szlcsc.com/{raw.TrimStart('/')}";
                if (TrustedDatasheetUrl(candidate) is { } trusted) return trusted;
            }
        }
        return null;
    }

    private static Dictionary<string, string> StringMap(JsonElement node, string name)
    {
        var output = new Dictionary<string, string>(StringComparer.Ordinal);
        if (!node.TryGetProperty(name, out var map) || map.ValueKind != JsonValueKind.Object) return output;
        foreach (var property in map.EnumerateObject())
        {
            var key = CleanText(property.Name);
            var value = CleanText(property.Value.ValueKind == JsonValueKind.String ? property.Value.GetString() : property.Value.ToString());
            if (key is not null && value is not null) output[key] = value;
        }
        return output;
    }

    private static bool TryProperty(JsonElement node, out JsonElement value, params string[] path)
    {
        value = node;
        foreach (var name in path)
            if (value.ValueKind != JsonValueKind.Object || !value.TryGetProperty(name, out value)) return false;
        return true;
    }

    private static bool LooksLikeVerificationPage(string html) => new[] { "_xvasu", "_xvtsc", "_xvpfs", "_xvpts", "Security verification" }
        .Any(token => html.Contains(token, StringComparison.OrdinalIgnoreCase));

    private static string? CleanText(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        var cleaned = WhitespacePattern().Replace(WebUtility.HtmlDecode(HtmlTagPattern().Replace(value, " ")).Replace('\u00a0', ' '), " ").Trim();
        return cleaned.Length == 0 ? null : cleaned;
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
                    return new(expected, name, model, brand, AdditionalPackage(product), NormalizeCategory(categoryPath), categoryPath, ProductUri(expected)!.AbsoluteUri, ProductImage(product));
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
    private static string? Text(JsonElement node, string name)
    {
        if (!node.TryGetProperty(name, out var value)) return null;
        return value.ValueKind switch { JsonValueKind.String => value.GetString()?.Trim(), JsonValueKind.Number => value.GetRawText(), _ => null };
    }

    [GeneratedRegex("^C[0-9]{1,10}$", RegexOptions.CultureInvariant)] private static partial Regex SkuPattern();
    [GeneratedRegex("<script\\b[^>]*\\btype\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script\\s*>", RegexOptions.IgnoreCase | RegexOptions.Singleline | RegexOptions.CultureInvariant)] private static partial Regex JsonLdPattern();
    [GeneratedRegex("<script\\b[^>]*\\bid\\s*=\\s*[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script\\s*>", RegexOptions.IgnoreCase | RegexOptions.Singleline | RegexOptions.CultureInvariant)] private static partial Regex NextDataPattern();
    [GeneratedRegex("<[^>]+>", RegexOptions.CultureInvariant)] private static partial Regex HtmlTagPattern();
    [GeneratedRegex("\\s+", RegexOptions.CultureInvariant)] private static partial Regex WhitespacePattern();
}

public sealed class LcscPublicLookup
{
    private const int MaxPageBytes = 4 * 1024 * 1024;
    private readonly HttpClient _client;
    private static readonly ConcurrentDictionary<string, (DateTimeOffset At, LcscProductMetadata? Value)> ExactCache = new();

    public LcscPublicLookup()
    {
        _client = new HttpClient(new HttpClientHandler { AllowAutoRedirect = true }) { Timeout = TimeSpan.FromSeconds(8) };
        _client.DefaultRequestHeaders.UserAgent.ParseAdd("ComponentVault-Windows/0.3");
        _client.DefaultRequestHeaders.AcceptLanguage.ParseAdd("zh-CN,zh;q=0.9");
    }

    public async Task<IReadOnlyList<LcscProductMetadata>> SearchChinaAsync(string keyword, CancellationToken cancellationToken = default)
    {
        var uri = LcscPublicCatalog.ChinaSearchUri(keyword) ?? throw new ArgumentException("Search keyword is empty.", nameof(keyword));
        AppDiagnostics.Record(DiagnosticEvent.CatalogDomestic,DiagnosticOutcome.Started);
        IReadOnlyList<LcscProductMetadata> results;
        try { results=LcscPublicCatalog.ParseChinaSearchPage(await GetPageAsync(uri,DiagnosticEvent.CatalogDomestic,cancellationToken)); }
        catch(LcscDomesticBlockedException exception){AppDiagnostics.Record(DiagnosticEvent.CatalogDomestic,DiagnosticOutcome.Blocked,exceptionType:exception.GetType());throw;}
        catch(InvalidDataException exception){AppDiagnostics.Record(DiagnosticEvent.CatalogDomestic,DiagnosticOutcome.Parser,exceptionType:exception.GetType());throw;}
        AppDiagnostics.Record(DiagnosticEvent.CatalogDomestic,DiagnosticOutcome.Success);
        foreach (var item in results) LcscPublicCatalog.CacheImage(item.Sku, item.ImageUrl);
        return results;
    }

    public async Task<LcscProductMetadata?> LookupAsync(string sku, CancellationToken cancellationToken = default)
    {
        var normalized = LcscPublicCatalog.NormalizeSku(sku);
        if (normalized is null) return null;
        Exception? domesticFailure = null;
        try
        {
            var domestic = LcscPublicCatalog.ExactChinaMatch(normalized, await SearchChinaAsync(normalized, cancellationToken));
            if (domestic is not null) return domestic;
        }
        catch (Exception exception) when (exception is LcscDomesticBlockedException or HttpRequestException or TaskCanceledException)
        {
            domesticFailure = exception;
        }
        var uri = LcscPublicCatalog.ProductUri(normalized)!;
        if (ExactCache.TryGetValue(normalized, out var cached) && DateTimeOffset.UtcNow - cached.At < (cached.Value is null ? TimeSpan.FromSeconds(30) : TimeSpan.FromDays(7))) return cached.Value;
        AppDiagnostics.Record(DiagnosticEvent.CatalogInternational,DiagnosticOutcome.Started);
        LcscProductMetadata? metadata;
        try { metadata=LcscPublicCatalog.ParsePage(normalized,await GetPageAsync(uri,DiagnosticEvent.CatalogInternational,cancellationToken)); }
        catch(InvalidDataException exception){AppDiagnostics.Record(DiagnosticEvent.CatalogInternational,DiagnosticOutcome.Parser,exceptionType:exception.GetType());throw;}
        AppDiagnostics.Record(DiagnosticEvent.CatalogInternational,metadata is null?DiagnosticOutcome.Parser:DiagnosticOutcome.Success);
        if (metadata is null && domesticFailure is LcscDomesticBlockedException) throw domesticFailure;
        LcscPublicCatalog.CacheImage(normalized, metadata?.ImageUrl);
        ExactCache[normalized] = (DateTimeOffset.UtcNow, metadata);
        if (ExactCache.Count > 64) ExactCache.TryRemove(ExactCache.OrderBy(pair => pair.Value.At).First().Key, out _);
        return metadata;
    }

    private async Task<string> GetPageAsync(Uri uri,DiagnosticEvent activity,CancellationToken cancellationToken)
    {
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(TimeSpan.FromSeconds(8));
        HttpResponseMessage response;
        try { response=await _client.GetAsync(uri,HttpCompletionOption.ResponseHeadersRead,timeout.Token); }
        catch(TaskCanceledException exception){AppDiagnostics.Record(activity,DiagnosticOutcome.Timeout,exceptionType:exception.GetType());throw;}
        catch(HttpRequestException exception) when(exception.InnerException is System.Net.Sockets.SocketException){AppDiagnostics.Record(activity,DiagnosticOutcome.Dns,exceptionType:exception.GetType());throw;}
        catch(HttpRequestException exception) when(exception.InnerException is System.Security.Authentication.AuthenticationException){AppDiagnostics.Record(activity,DiagnosticOutcome.Tls,exceptionType:exception.GetType());throw;}
        catch(HttpRequestException exception){AppDiagnostics.Record(activity,DiagnosticOutcome.Failed,exceptionType:exception.GetType());throw;}
        using(response){if(!response.IsSuccessStatusCode){AppDiagnostics.Record(activity,DiagnosticOutcome.Http,(int)response.StatusCode);response.EnsureSuccessStatusCode();}
        if (response.Content.Headers.ContentLength is > MaxPageBytes) throw new InvalidDataException("商品页面超过 4 MiB 限制。");
        using var body = await response.Content.ReadAsStreamAsync(timeout.Token);
        using var output = new MemoryStream();
        var buffer = new byte[8192];
        int count;
        while ((count = await body.ReadAsync(buffer.AsMemory(), timeout.Token)) > 0)
        {
            if (output.Length + count > MaxPageBytes) throw new InvalidDataException("商品页面超过 4 MiB 限制。");
            output.Write(buffer, 0, count);
        }
        return Encoding.UTF8.GetString(output.ToArray());}
    }
}
