using ComponentVault.WinUI.Localization;
using Microsoft.UI.Xaml.Data;

namespace ComponentVault.WinUI.Converters;

public sealed class CategoryDisplayConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language)
    {
        return CategoryDisplay.Localize(value as string);
    }

    public object ConvertBack(object value, Type targetType, object parameter, string language)
    {
        return value;
    }
}
