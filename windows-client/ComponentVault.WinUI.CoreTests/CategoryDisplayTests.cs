using System.Globalization;
using ComponentVault.WinUI.Localization;
using Xunit;

namespace ComponentVault.WinUI.CoreTests;

public sealed class CategoryDisplayTests
{
    [Fact]
    public void Localize_ChineseUiUsesKnownOfficialLeafForHistoricalEnglishPath()
    {
        Assert.Equal(
            "线性稳压器",
            CategoryDisplay.Localize(
                "Integrated Circuits (ICs)/Power Management (PMIC)/Voltage Regulators - Linear",
                CultureInfo.GetCultureInfo("zh-CN")));
    }

    [Fact]
    public void Localize_PreservesUnknownAndEnglishUiValues()
    {
        Assert.Equal("User Parent / Custom Leaf", CategoryDisplay.Localize("User Parent/Custom Leaf", CultureInfo.GetCultureInfo("zh-CN")));
        const string officialPath = "Integrated Circuits (ICs)/Embedded/Microcontrollers";
        Assert.Equal(officialPath, CategoryDisplay.Localize(officialPath, CultureInfo.GetCultureInfo("en-US")));
    }

    [Fact]
    public void FilterSemantics_MergesKnownEnglishAndChineseWithoutChangingUnknownCategories()
    {
        const string english = "Integrated Circuits (ICs)/Embedded/Microcontrollers";
        Assert.Equal([english], CategoryFilterSemantics.Options(["微控制器", english]));
        Assert.True(CategoryFilterSemantics.SameCategory(english, "微控制器"));
        Assert.True(CategoryFilterSemantics.SameCategory("微控制器", "微控制器"));
        Assert.Equal(
            ["Another/Leaf", "User Parent/Custom Leaf"],
            CategoryFilterSemantics.Options(["User Parent/Custom Leaf", "Another/Leaf"]));
        Assert.False(CategoryFilterSemantics.SameCategory("User Parent/Custom Leaf", "Another/Leaf"));
    }

    [Fact]
    public void FilterSemantics_ChineseSearchMatchesHistoricalEnglishWhileRawEnglishStillMatches()
    {
        const string historical = "Integrated Circuits (ICs)/Power Management (PMIC)/Voltage Regulators - Linear";
        Assert.True(CategoryFilterSemantics.MatchesSearch(historical, "线性稳压器"));
        Assert.True(CategoryFilterSemantics.MatchesSearch(historical, "Power Management"));
    }
}
