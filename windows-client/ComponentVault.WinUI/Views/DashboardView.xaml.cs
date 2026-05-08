using ComponentVault.WinUI.Design;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.Views;

public sealed partial class DashboardView : Page
{
    public DashboardView()
    {
        InitializeComponent();
        DataContext = ViewModelResolver.ResolveMainViewModel();
    }

    private void OnOpenInventoryClicked(object sender, RoutedEventArgs e)
    {
        NavigateShell("Inventory");
    }

    private void OnOpenMovementsClicked(object sender, RoutedEventArgs e)
    {
        NavigateShell("Movements");
    }

    private void OnOpenSettingsClicked(object sender, RoutedEventArgs e)
    {
        NavigateShell("Settings");
    }

    private void NavigateShell(string tag)
    {
        if (((App)Application.Current).Window is MainWindow window)
        {
            window.NavigateTo(tag);
        }
    }
}
