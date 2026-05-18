using System.Globalization;
using Microsoft.Windows.ApplicationModel.Resources;

namespace ComponentVault.WinUI.Localization;

internal static class AppStrings
{
    private static readonly ResourceLoader Loader = new();

    internal static string Get(string key)
    {
        var value = Loader.GetString(key);
        return string.IsNullOrWhiteSpace(value) ? key : value;
    }

    internal static string Format(string key, params object[] args)
    {
        return string.Format(CultureInfo.CurrentCulture, Get(key), args);
    }
}
