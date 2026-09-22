using ComponentVault.WinUI.Services.Catalog;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class LcscPublicCatalogTests
{
    private const string InternationalRoutePage = """
        <script type="application/ld+json">{"@type":"Product","sku":"C88002","mpn":"EN-MODEL"}</script>
        """;
    private const string DomesticRoutePage = """
        <script id="__NEXT_DATA__">{"props":{"pageProps":{"soData":{"searchResult":{"productRecordList":[{
          "lightProductModel":"中文型号","productVO":{"productCode":"C88002","productId":"88002"}
        }]}}}}}</script>
        """;

    [Fact]
    public async Task LookupDetailedAsync_EnglishPrefersInternationalWithoutDomesticOverwrite()
    {
        var requestedHosts = new List<string>();
        var lookup = new LcscPublicLookup(
            (uri, _, _) => { requestedHosts.Add(uri.Host); return Task.FromResult(uri.Host == "www.lcsc.com" ? InternationalRoutePage : DomesticRoutePage); },
            () => System.Globalization.CultureInfo.GetCultureInfo("en-US"));

        var result = await lookup.LookupDetailedAsync("C88002");

        Assert.Equal(LcscCatalogSource.International, result.ResolvedSource);
        Assert.Equal("EN-MODEL", result.Metadata?.Model);
        Assert.Equal("lcsc_public_web", result.Metadata?.Source);
        Assert.Contains("international LCSC", result.Metadata!.LookupNotice!);
        Assert.Equal(["www.lcsc.com"], requestedHosts);
        Assert.False(result.UsedFallback);
    }

    [Fact]
    public async Task LookupDetailedAsync_ChineseBlockedFallsBackWithVisibleReason()
    {
        var lookup = new LcscPublicLookup(
            (uri, _, _) => Task.FromResult(uri.Host == "so.szlcsc.com" ? "<script>var _xvasu='challenge';</script>" : InternationalRoutePage),
            () => System.Globalization.CultureInfo.GetCultureInfo("zh-CN"));

        var result = await lookup.LookupDetailedAsync("C88002");

        Assert.Equal(LcscCatalogSource.International, result.ResolvedSource);
        Assert.True(result.UsedFallback);
        Assert.Equal(LcscCatalogFailureKind.Blocked, Assert.Single(result.Attempts).Failure);
        Assert.Contains("站点验证阻断", result.UserMessage);
        Assert.Contains("国际 LCSC", result.UserMessage);
        Assert.Equal(result.UserMessage, result.Metadata?.LookupNotice);
    }

    [Fact]
    public async Task SearchPreferredAsync_ExplainsInternationalKeywordSearchLimitation()
    {
        var lookup = new LcscPublicLookup(
            (_, _, _) => Task.FromResult(DomesticRoutePage),
            () => System.Globalization.CultureInfo.GetCultureInfo("en-US"));

        var result = await lookup.SearchPreferredAsync("resistor");

        Assert.Equal(LcscCatalogSource.Domestic, result.Source);
        Assert.NotNull(result.Notice);
        Assert.Contains("do not provide keyword search", result.Notice);
    }

    [Fact]
    public void ParsePage_RequiresExactProductSku()
    {
        const string html = """
            <script type="application/ld+json">
            {"@type":"Product","sku":"C999","description":"Wrong product","image":"https://assets.lcsc.com/a.jpg"}
            </script>
            """;

        Assert.Null(LcscPublicCatalog.ParsePage("C123", html));
    }

    [Fact]
    public void ParsePage_ReadsExactProductAndOfficialCategory()
    {
        const string html = """
            <script type="application/ld+json">
            {"@graph":[{"@type":"Product","sku":"C123","mpn":"RC0603","description":"10k resistor","brand":{"name":"Brand"},"category":"Resistors/Chip Resistor - Surface Mount","image":["https://evil.example/a.jpg",{"contentUrl":"https://assets.lcsc.com/a.jpg"}],"additionalProperty":[{"name":"Package","value":"0603"}]}]}
            </script>
            """;

        var result = LcscPublicCatalog.ParsePage("c123", html);

        Assert.NotNull(result);
        Assert.Equal("C123", result.Sku);
        Assert.Equal("RC0603", result.Name);
        Assert.Equal("10k resistor", result.Description);
        Assert.Equal("电阻", result.Category);
        Assert.Equal("0603", result.PackageName);
        Assert.Equal("https://assets.lcsc.com/a.jpg", result.ImageUrl);
    }

    [Theory]
    [InlineData("http://assets.lcsc.com/a.jpg")]
    [InlineData("https://assets.lcsc.com.evil.example/a.jpg")]
    [InlineData("https://user@assets.lcsc.com/a.jpg")]
    [InlineData("https://assets.lcsc.com:444/a.jpg")]
    public void TrustedImageUrl_RejectsUntrustedUrls(string url) =>
        Assert.Null(LcscPublicCatalog.TrustedImageUrl(url));

    [Fact]
    public void NormalizeCategory_PreservesOfficialChineseName()
    {
        Assert.Equal("贴片电阻", LcscPublicCatalog.NormalizeCategory("贴片电阻"));
    }

    [Fact]
    public void ParseChinaSearchPage_ReadsNextDataParametersAndActualProductId()
    {
        const string html = """
            <html><script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"soData":{"searchResult":{"productRecordList":[{
              "lightCatalogName":"电阻器 &amp; 电位器","lightProductName":"<b>贴片电阻</b> 10kΩ",
              "lightProductModel":"RC0603FR-0710KL","lightBrandName":"YAGEO","lightStandard":"0603",
              "paramLinkedMap":{"阻值":"10kΩ","精度":"±1%","封装":"0603"},
              "productVO":{"productCode":"c25804","productId":123456,"stockNumber":99999,
                "breviaryImageUrl":"https://img.szlcsc.com/a.jpg",
                "fileTypeVOList":[{"detailVOList":[{"fileUrl":"upload/public/pdf/a.pdf"}]}]}
            }]}}}}}
            </script></html>
            """;

        var result = Assert.Single(LcscPublicCatalog.ParseChinaSearchPage(html));

        Assert.Equal("C25804", result.Sku);
        Assert.Equal("RC0603FR-0710KL", result.Name);
        Assert.Equal("贴片电阻 10kΩ", result.Description);
        Assert.Equal("电阻器 & 电位器", result.Category);
        Assert.Equal("https://item.szlcsc.com/123456.html", result.OfficialUrl);
        Assert.Equal("https://atta.szlcsc.com/upload/public/pdf/a.pdf", result.DatasheetUrl);
        Assert.Equal("±1%", result.Parameters!["精度"]);
    }

    [Fact]
    public void ParseChinaSearchPage_PreservesJsonEscapingBeforeCleaningFields()
    {
        const string html = """
            <script id="__NEXT_DATA__">{"props":{"pageProps":{"soData":{"searchResult":{"productRecordList":[{
              "lightProductName":"Comparator &quot;precision&quot;","productVO":{"productCode":"C7","productId":"77"}
            }]}}}}}</script>
            """;
        var item = Assert.Single(LcscPublicCatalog.ParseChinaSearchPage(html));
        Assert.Equal("Comparator \"precision\"", item.Name);
    }

    [Fact]
    public void ParseChinaSearchPage_DistinguishesUnexpectedPageFromEmptyResults()
    {
        Assert.Throws<InvalidDataException>(() => LcscPublicCatalog.ParseChinaSearchPage("<html>ordinary page</html>"));
        const string empty = """<script id="__NEXT_DATA__">{"props":{"pageProps":{"soData":{"searchResult":{"productRecordList":[]}}}}}</script>""";
        Assert.Empty(LcscPublicCatalog.ParseChinaSearchPage(empty));
    }

    [Fact]
    public void ParseChinaSearchPage_RequiresRealProductIdAndValidCNumber()
    {
        const string html = """
            <script id="__NEXT_DATA__">{"props":{"pageProps":{"soData":{"searchResult":{"productRecordList":[
              {"productVO":{"productCode":"C123","productId":"C123","productName":"wrong id"}},
              {"productVO":{"productCode":"SKU-1","productId":"456","productName":"wrong sku"}}
            ]}}}}}</script>
            """;

        Assert.Empty(LcscPublicCatalog.ParseChinaSearchPage(html));
    }

    [Fact]
    public void ExactChinaMatch_DoesNotAcceptApproximateSearchResult()
    {
        var products = new[]
        {
            new LcscProductMetadata("C1234", "approximate", null, null, null, "电阻", null, "https://item.szlcsc.com/1.html", null),
            new LcscProductMetadata("C123", "exact", null, null, null, "电阻", null, "https://item.szlcsc.com/2.html", null),
        };

        Assert.Equal("exact", LcscPublicCatalog.ExactChinaMatch("c123", products)?.Name);
    }

    [Fact]
    public void ParseChinaSearchPage_ReportsVerificationInsteadOfParsingChallenge()
    {
        Assert.Throws<LcscDomesticBlockedException>(() =>
            LcscPublicCatalog.ParseChinaSearchPage("<script>var _xvasu='token';</script>"));
    }

    [Theory]
    [InlineData("http://atta.szlcsc.com/a.pdf")]
    [InlineData("https://atta.szlcsc.com.evil.example/a.pdf")]
    [InlineData("https://user@atta.szlcsc.com/a.pdf")]
    public void TrustedDatasheetUrl_RejectsUnsafeUrls(string url) =>
        Assert.Null(LcscPublicCatalog.TrustedDatasheetUrl(url));
}
