using ComponentVault.WinUI.ViewModels;
using Microsoft.UI.Xaml;

namespace ComponentVault.WinUI.Design;

internal static class ViewModelResolver
{
    public static object ResolveMainViewModel()
    {
        if (Windows.ApplicationModel.DesignMode.DesignModeEnabled)
        {
            return new DesignMainViewModel();
        }

        if (Application.Current is App app)
        {
            return app.MainViewModel is not null
                ? app.MainViewModel
                : new DesignMainViewModel();
        }

        return new DesignMainViewModel();
    }

    public static MainViewModel? GetRuntimeViewModel(object? dataContext) =>
        dataContext as MainViewModel;
}
