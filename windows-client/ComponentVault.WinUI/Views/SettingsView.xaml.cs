using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using ComponentVault.WinUI.ViewModels;

namespace ComponentVault.WinUI.Views;

public sealed partial class SettingsView : Page
{
    private MainViewModel ViewModel => ((App)Application.Current).MainViewModel;

    public SettingsView()
    {
        InitializeComponent();
        DataContext = ViewModel;
        LoadValuesFromViewModel();
    }

    private void LoadValuesFromViewModel()
    {
        var sync = ViewModel.SyncConfiguration;
        ServerUrlTextBox.Text = sync.ServerBaseUrl;
        ApiTokenBox.Password = sync.ApiToken;
        DeviceIdTextBox.Text = sync.DeviceId;
        LastSyncedTextBox.Text = sync.LastSyncedAt;
        LastResultTextBox.Text = sync.LastSyncMessage;
        AutoSyncToggle.IsOn = sync.AutoSyncEnabled;
    }

    private async void OnSaveSettingsClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel.IsBusy)
        {
            return;
        }

        var result = ViewModel.SaveSyncConfiguration(
            ServerUrlTextBox.Text,
            ApiTokenBox.Password,
            AutoSyncToggle.IsOn
        );
        LoadValuesFromViewModel();
        await ShowMessageAsync(result.IsSuccess ? "Settings saved" : "Settings failed", result.Message);
    }

    private async void OnTestConnectionClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel.IsBusy)
        {
            return;
        }

        ViewModel.SaveSyncConfiguration(
            ServerUrlTextBox.Text,
            ApiTokenBox.Password,
            AutoSyncToggle.IsOn
        );
        var result = await ViewModel.TestConnectionAsync();
        LoadValuesFromViewModel();
        await ShowMessageAsync(result.IsSuccess ? "Connection ok" : "Connection failed", result.Message);
    }

    private async void OnSyncNowClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel.IsBusy)
        {
            return;
        }

        ViewModel.SaveSyncConfiguration(
            ServerUrlTextBox.Text,
            ApiTokenBox.Password,
            AutoSyncToggle.IsOn
        );
        var result = await ViewModel.RunSyncAsync();
        LoadValuesFromViewModel();
        await ShowMessageAsync(result.IsSuccess ? "Sync complete" : "Sync failed", result.IsSuccess ? ViewModel.SyncConfiguration.LastSyncMessage : result.Message);
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = "Close",
            XamlRoot = XamlRoot,
        };
        await dialog.ShowAsync();
    }
}
