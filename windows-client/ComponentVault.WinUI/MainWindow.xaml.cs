using ComponentVault.WinUI.Views;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly ViewModels.MainViewModel _viewModel;

    public MainWindow()
    {
        InitializeComponent();
        _viewModel = ((App)Application.Current).MainViewModel;
        AppNavigationView.SelectedItem = DashboardItem;
        RootFrame.Navigate(typeof(DashboardView));
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

        switch (tag)
        {
            case "Dashboard":
                RootFrame.Navigate(typeof(DashboardView));
                break;
            case "Components":
                RootFrame.Navigate(typeof(ComponentsView));
                break;
            case "Movements":
                RootFrame.Navigate(typeof(MovementsView));
                break;
            case "Settings":
                RootFrame.Navigate(typeof(SettingsView));
                break;
        }
    }

    private async void OnSyncNowClicked(object sender, RoutedEventArgs e)
    {
        var result = await _viewModel.RunSyncAsync();
        await ShowMessageAsync(
            result.IsSuccess ? "Sync complete" : "Sync failed",
            result.IsSuccess ? _viewModel.SyncConfiguration.LastSyncMessage : result.Message
        );
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = "Close",
            XamlRoot = AppNavigationView.XamlRoot,
        };
        await dialog.ShowAsync();
    }
}
