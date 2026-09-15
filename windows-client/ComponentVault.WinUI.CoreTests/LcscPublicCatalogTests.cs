using ComponentVault.WinUI.Services.Catalog;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class LcscPublicCatalogTests
{
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
}
