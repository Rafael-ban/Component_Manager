using ComponentVault.WinUI.Design;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.Views;

public sealed partial class SettingsView : Page
{
    private MainViewModel? RuntimeViewModel => ViewModelResolver.GetRuntimeViewModel(DataContext);

    private SyncConfiguration? CurrentSyncConfiguration =>
        DataContext switch
        {
            MainViewModel runtime => runtime.SyncConfiguration,
            DesignMainViewModel design => design.SyncConfiguration,
            _ => null,
        };

    public SettingsView()
    {
        InitializeComponent();
        DataContext = ViewModelResolver.ResolveMainViewModel();
        LoadValuesFromCurrentContext();
    }

    private void LoadValuesFromCurrentContext()
    {
        var sync = CurrentSyncConfiguration;
        if (sync is null)
        {
            return;
        }

        ServerUrlTextBox.Text = sync.ServerBaseUrl;
        ApiTokenBox.Password = sync.ApiToken;
        DeviceIdTextBox.Text = sync.DeviceId;
        LastSyncedTextBox.Text = sync.LastSyncedAt;
        LastResultTextBox.Text = sync.LastSyncMessage;
        AutoSyncToggle.IsOn = sync.AutoSyncEnabled;
    }

    private async void OnSaveSettingsClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null || viewModel.IsBusy)
        {
            return;
        }

        var result = viewModel.SaveSyncConfiguration(
            ServerUrlTextBox.Text,
            ApiTokenBox.Password,
            AutoSyncToggle.IsOn
        );
        LoadValuesFromCurrentContext();
        await ShowMessageAsync(result.IsSuccess ? "设置已保存" : "保存失败", result.Message);
    }

    private async void OnTestConnectionClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null || viewModel.IsBusy)
        {
            return;
        }

        viewModel.SaveSyncConfiguration(
            ServerUrlTextBox.Text,
            ApiTokenBox.Password,
            AutoSyncToggle.IsOn
        );
        var result = await viewModel.TestConnectionAsync();
        LoadValuesFromCurrentContext();
        await ShowMessageAsync(result.IsSuccess ? "连接成功" : "连接失败", result.Message);
    }

    private async void OnSyncNowClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null || viewModel.IsBusy)
        {
            return;
        }

        viewModel.SaveSyncConfiguration(
            ServerUrlTextBox.Text,
            ApiTokenBox.Password,
            AutoSyncToggle.IsOn
        );
        var result = await viewModel.RunSyncAsync();
        LoadValuesFromCurrentContext();
        await ShowMessageAsync(
            result.IsSuccess ? "同步完成" : "同步失败",
            result.IsSuccess ? viewModel.SyncConfiguration.LastSyncMessage : result.Message
        );
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = "关闭",
            XamlRoot = XamlRoot,
        };
        await dialog.ShowAsync();
    }
}
