using ComponentVault.WinUI.Views;
using ComponentVault.WinUI.Localization;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly ViewModels.MainViewModel _viewModel;

    public MainWindow()
    {
        InitializeComponent();
        _viewModel =
            ((App)Application.Current).MainViewModel
            ?? throw new InvalidOperationException(AppStrings.Get("Windows_App_MainViewModelMissing"));
        AppNavigationView.DataContext = _viewModel;
        NavigateTo("Overview");
    }

    public void NavigateTo(string tag)
    {
        var targetPage = tag switch
        {
            "Inventory" => typeof(ComponentsView),
            "Movements" => typeof(MovementsView),
            "Bom" => typeof(BomView),
            "Overview" => typeof(DashboardView),
            "Settings" => typeof(SettingsView),
            _ => null,
        };

        if (targetPage is null)
        {
            return;
        }

        var targetItem = tag switch
        {
            "Inventory" => InventoryItem,
            "Movements" => MovementsItem,
            "Bom" => BomItem,
            "Overview" => OverviewItem,
            "Settings" => SettingsItem,
            _ => null,
        };

        if (!ReferenceEquals(AppNavigationView.SelectedItem, targetItem))
        {
            AppNavigationView.SelectedItem = targetItem;
        }

        RootFrame.Navigate(targetPage);
    }

    private void OnSelectionChanged(
        NavigationView sender,
        NavigationViewSelectionChangedEventArgs args
    )
    {
        if (args.SelectedItemContainer?.Tag is not string tag)
        {
            return;
        }

        NavigateTo(tag);
    }

    private async void OnSyncNowClicked(object sender, RoutedEventArgs e)
    {
        var result = await _viewModel.RunSyncAsync();
        await ShowMessageAsync(
            result.IsSuccess
                ? AppStrings.Get("MainWindow_SyncSuccessTitle")
                : AppStrings.Get("MainWindow_SyncFailureTitle"),
            result.IsSuccess ? _viewModel.SyncConfiguration.LastSyncMessage : result.Message
        );
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = AppStrings.Get("Common_Close"),
            XamlRoot = AppNavigationView.XamlRoot,
        };
        await dialog.ShowAsync();
    }
}
