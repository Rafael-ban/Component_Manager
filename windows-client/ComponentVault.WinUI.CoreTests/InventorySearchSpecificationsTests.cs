using System.Globalization;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.Services.Catalog;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class InventorySearchSpecificationsTests
{
    [Theory]
    [InlineData("c258 10KΩ")]
    [InlineData("RC0603FR0710KL ±1%")]
    [InlineData("电阻 0603")]
    [InlineData("10 kΩ")]
    [InlineData("10kohm")]
    public void Search_MatchesNormalizedTermsAcrossFields(string query)
    {
        Assert.True(InventorySearch.Matches(Component(), query));
    }

    [Fact]
    public void Search_RequiresEveryTermAndDoesNotCorrectDigits()
    {
        Assert.False(InventorySearch.Matches(Component(), "C25804 22kΩ"));
        Assert.False(InventorySearch.Matches(Component(), "C25805"));
        Assert.False(InventorySearch.Matches(Component(), "10.5kΩ"));
    }

    [Fact]
    public void OfficialSpecifications_UsesSourceValuesAndKeepsModelDistinct()
    {
        var metadata = new LcscProductMetadata("C25804", "RC0603FR-0710KL", "RC0603FR-0710KL", null,
            "0603", "电阻", null, "https://item.szlcsc.com/123.html", null,
            Parameters: new Dictionary<string, string> { ["阻值"] = "10kΩ", ["精度"] = "±1%" });

        Assert.Equal("RC0603FR-0710KL", OfficialSpecifications.AutoName(metadata));
        Assert.Equal("阻值 10kΩ · 精度 ±1%", OfficialSpecifications.FromDescription(
            "官方描述：贴片电阻 10kΩ ±1%", "电阻", CultureInfo.GetCultureInfo("zh-CN")));
        Assert.Equal("阻值 10kΩ · 精度 ±1%", OfficialSpecifications.FromDescription(
            Component().Description, "电阻", CultureInfo.GetCultureInfo("zh-CN")));
        Assert.Equal("阻值 10kΩ · 精度 ±1%", OfficialSpecifications.FromDescription(
            "参数：阻值：10kΩ\n参数：精度：±1%", "电阻", CultureInfo.GetCultureInfo("zh-CN")));
    }

    [Theory]
    [InlineData("容量", "100nF", "±10%", "CL10B104KB8NNNC")]
    [InlineData("电感量", "4.7µH", "±20%", "LQH32MN4R7K23L")]
    public void OfficialSpecifications_KeepsCapacitorAndInductorModelsUnchanged(
        string field, string value, string tolerance, string expected)
    {
        var model = field == "容量" ? "CL10B104KB8NNNC" : "LQH32MN4R7K23L";
        var metadata = new LcscProductMetadata("C1", model, model, null, null, field, null,
            "https://item.szlcsc.com/1.html", null,
            Parameters: new Dictionary<string, string> { [field] = value, ["精度"] = tolerance });

        Assert.Equal(expected, OfficialSpecifications.AutoName(metadata));
    }

    [Fact]
    public void Search_NormalizesMicroUnitVariants()
    {
        var item = ComponentWith("参数·容量：10μF");
        Assert.True(InventorySearch.Matches(item, "10uF"));
        Assert.True(InventorySearch.Matches(item, "10µF"));
    }

    [Fact]
    public void Search_FindsOfficialBrandAcrossSkuAndSpecificationTerms()
    {
        var item = ComponentWith("品牌：YAGEO\n参数·阻值：10kΩ");
        Assert.True(InventorySearch.Matches(item, "yageo 10kohm"));
        Assert.False(InventorySearch.Matches(item, "yageo 22kohm"));
    }

    [Fact]
    public void OfficialSpecifications_UsesControlledDescriptionFallback()
    {
        var metadata = new LcscProductMetadata("C1", "销售标题", "RC0603FR-0710KL", null, null,
            "电阻", null, "https://item.szlcsc.com/1.html", null,
            Description: "贴片电阻 10kΩ ±1%，供货详情");

        Assert.Equal("RC0603FR-0710KL", OfficialSpecifications.AutoName(metadata));
    }

    [Fact]
    public void OfficialSpecifications_DoesNotPutLongSalesCopyInName()
    {
        var longText = new string('X', 150);
        var metadata = new LcscProductMetadata("C1", longText, longText, null, null,
            "电容", null, "https://item.szlcsc.com/1.html", null,
            Parameters: new Dictionary<string, string> { ["容量"] = "100nF", ["精度"] = "±10%" },
            Description: longText);

        Assert.Equal("C1", OfficialSpecifications.AutoName(metadata));
    }

    [Fact]
    public void OfficialSpecifications_UsesBrandAndModelWithoutAddingValues()
    {
        var metadata = new LcscProductMetadata("C5126214", "FRH0603B1002TS", "FRH0603B1002TS", "FOJAN",
            "0603", "电阻", null, "https://item.szlcsc.com/1.html", null,
            Parameters: new Dictionary<string, string> { ["阻值"] = "10kΩ", ["精度"] = "±0.1%" });

        Assert.Equal("FOJAN FRH0603B1002TS", OfficialSpecifications.AutoName(metadata));
    }

    [Fact]
    public void OfficialSpecifications_ShowsVoltagePowerAndUnitsFromStoredNotes()
    {
        var capacitor = ComponentWith("型号：1206X107M6R3NT\n参数：容量：100uF\n参数：额定电压(V)：6.3\n参数：精度：±20%");
        Assert.Equal("容量 100uF · 耐压 6.3V · 精度 ±20%", OfficialSpecifications.FromDescription(
            capacitor.Description, capacitor.Category, CultureInfo.GetCultureInfo("zh-CN")));
        Assert.Equal("6.3V", capacitor.DisplaySpecificationFields.Single(field => field.Key == "耐压").Value);
        Assert.True(InventorySearch.Matches(capacitor, "耐压6.3V 100uF"));

        var resistor = new LcscProductMetadata("C5126214", "FRH0603B1002TS", "FRH0603B1002TS", "FOJAN",
            "0603", "电阻", null, "https://item.szlcsc.com/1.html", null,
            Description: "10kΩ ±0.1% 100mW 0603 Thick Film Resistor");
        Assert.Equal("阻值 10kΩ · 精度 ±0.1% · 功率 100mW",
            OfficialSpecifications.FromDescription($"官方描述：{resistor.Description}", resistor.Category,
                CultureInfo.GetCultureInfo("zh-CN")));
        Assert.Empty(OfficialSpecifications.Parse("型号：FRH0603B1002TS", "电阻"));
    }

    [Fact]
    public void OfficialSpecifications_ReadsEnglishParameterNotesAndVoltageAlias()
    {
        var capacitor = ComponentWith("参数·Capacitance：22pF\n参数·Voltage Rating：50V\n参数·Tolerance：±5%");
        Assert.Equal("容量 22pF · 耐压 50V · 精度 ±5%", OfficialSpecifications.FromDescription(
            capacitor.Description, capacitor.Category, CultureInfo.GetCultureInfo("zh-CN")));
        Assert.True(InventorySearch.Matches(capacitor, "耐压50V 22pF"));
        Assert.True(InventorySearch.Matches(capacitor, "VoltageRating50V 22pF"));
    }

    [Fact]
    public void OfficialSpecifications_LocalizesLabelsWithoutChangingSearch()
    {
        Assert.Equal("耐压", OfficialSpecifications.LocalizeLabel("耐压", CultureInfo.GetCultureInfo("zh-CN")));
        Assert.Equal("Voltage rating", OfficialSpecifications.LocalizeLabel("耐压", CultureInfo.GetCultureInfo("en-US")));
        Assert.Equal("Capacitance 22pF · Voltage rating 50V",
            OfficialSpecifications.FromDescription("参数·Capacitance：22pF\n参数·Voltage Rating：50V",
                "Capacitor", CultureInfo.GetCultureInfo("en-US")));
    }

    [Fact]
    public void OfficialSpecifications_DoesNotExtractSpecificationsEmbeddedInModelTokens()
    {
        Assert.Empty(OfficialSpecifications.Parse(
            "型号：ABC10kΩX50V\n官方描述：ABC10kΩX50V Ceramic Capacitor", "电容"));
        Assert.Empty(OfficialSpecifications.Parse(
            "官方描述：FRH0603B1002TS_10kΩP100mW_R", "电阻"));
    }

    private static ComponentRecord ComponentWith(string description) => new()
    {
        Id = "cmp-2", Sku = "C2", Name = "Capacitor", Category = "电容", PackageName = "0603",
        Location = "A-1", Description = description, Quantity = 1, MinStock = 0,
        UpdatedAt = "2026-09-27T00:00:00Z", Deleted = false,
    };

    private static ComponentRecord Component() => new()
    {
        Id = "cmp-1", Sku = "C25804", Name = "RC0603FR-0710KL", Category = "电阻", PackageName = "0603",
        Location = "A-1", Description = "型号：RC0603FR-0710KL\n参数·阻值：10kΩ\n参数·精度：±1%",
        Quantity = 1, MinStock = 0, UpdatedAt = "2026-09-27T00:00:00Z", Deleted = false,
    };
}
