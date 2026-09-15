using ComponentVault.WinUI.Design;
using ComponentVault.WinUI.Localization;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.ViewModels;
using ComponentVault.WinUI.Services.Updates;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System.Reflection;

namespace ComponentVault.WinUI.Views;

public sealed partial class SettingsView : Page
{
    private readonly GitHubReleaseClient _releaseClient = new();
    private CancellationTokenSource? _updateCancellation;
    private GitHubReleaseInfo? _latestRelease;
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
        CurrentVersionText.Text = GetCurrentVersion();
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
        await ShowMessageAsync(
            result.IsSuccess
                ? AppStrings.Get("Settings_Dialog_SaveSuccessTitle")
                : AppStrings.Get("Settings_Dialog_SaveFailureTitle"),
            result.Message
        );
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
        await ShowMessageAsync(
            result.IsSuccess
                ? AppStrings.Get("Settings_Dialog_TestSuccessTitle")
                : AppStrings.Get("Settings_Dialog_TestFailureTitle"),
            result.Message
        );
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
            result.IsSuccess
                ? AppStrings.Get("Settings_Dialog_SyncSuccessTitle")
                : AppStrings.Get("Settings_Dialog_SyncFailureTitle"),
            result.IsSuccess ? viewModel.SyncConfiguration.LastSyncMessage : result.Message
        );
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = AppStrings.Get("Common_Close"),
            XamlRoot = XamlRoot,
        };
        await dialog.ShowAsync();
    }

    private async void OnCheckUpdateClicked(object sender, RoutedEventArgs e)
    {
        if (_updateCancellation is not null) return;
        _updateCancellation = new CancellationTokenSource();
        CheckUpdateButton.IsEnabled = false;
        UpdateProgressRing.IsActive = true;
        UpdateInfoBar.Severity = InfoBarSeverity.Informational;
        UpdateInfoBar.Title = "正在检查更新";
        UpdateInfoBar.Message = "正在读取公开 GitHub Release…";
        try
        {
            var result = await _releaseClient.CheckAsync(CurrentVersionText.Text, _updateCancellation.Token);
            _latestRelease = result.Release;
            UpdateInfoBar.Title = result.IsSuccess ? "更新检查完成" : "无法完成更新检查";
            UpdateInfoBar.Message = result.Message;
            UpdateInfoBar.Severity = result.Comparison == UpdateComparison.UpdateAvailable
                ? InfoBarSeverity.Success
                : result.IsSuccess ? InfoBarSeverity.Informational : InfoBarSeverity.Warning;
            LatestVersionText.Text = result.Release?.Tag ?? "不可用";
            PublishedAtText.Text = result.Release?.PublishedAt?.ToLocalTime().ToString("yyyy-MM-dd HH:mm") ?? "—";
            ReleaseNotesText.Text = string.IsNullOrWhiteSpace(result.Release?.Notes) ? "该 Release 没有正文更新日志。" : result.Release.Notes;
            var canDownloadUpdate = result.Comparison == UpdateComparison.UpdateAvailable;
            DownloadMsixButton.IsEnabled = canDownloadUpdate && result.Release?.MsixDownload is not null;
            DownloadPortableButton.IsEnabled = canDownloadUpdate && result.Release?.PortableDownload is not null;
            UpdateAssetHint.Text = result.Release is not null && (result.Release.MsixDownload is null || result.Release.PortableDownload is null)
                ? "此 Release 缺少一个或多个 Windows 平台包，请打开发布页核对资产。应用不会自动安装或添加签名证书。"
                : "应用不会自动下载、安装或添加签名证书。更新时请继续使用与当前相同的安装方式；数据保留情况取决于安装方式。";
        }
        catch (OperationCanceledException) { }
        finally
        {
            _updateCancellation?.Dispose();
            _updateCancellation = null;
            CheckUpdateButton.IsEnabled = true;
            UpdateProgressRing.IsActive = false;
        }
    }

    private async void OnDownloadMsixClicked(object sender, RoutedEventArgs e)
    {
        if (_latestRelease?.MsixDownload is { } uri
            && GitHubReleaseParser.ValidateAssetUrl(uri.AbsoluteUri, _latestRelease.Tag, GitHubReleaseParser.MsixAssetName) is { } trusted)
            await LaunchTrustedUriAsync(trusted, "MSIX 下载");
    }

    private async void OnDownloadPortableClicked(object sender, RoutedEventArgs e)
    {
        if (_latestRelease?.PortableDownload is { } uri
            && GitHubReleaseParser.ValidateAssetUrl(uri.AbsoluteUri, _latestRelease.Tag, GitHubReleaseParser.PortableAssetName) is { } trusted)
            await LaunchTrustedUriAsync(trusted, "便携版下载");
    }

    private async void OnOpenReleasesClicked(object sender, RoutedEventArgs e)
    {
        var uri = _latestRelease?.ReleasePage
            ?? new Uri("https://github.com/Rafael-ban/Component_Manager/releases");
        var trusted = _latestRelease is null
            ? GitHubReleaseParser.ValidateReleasePage(uri.AbsoluteUri)
            : GitHubReleaseParser.ValidateReleasePage(uri.AbsoluteUri, _latestRelease.Tag);
        if (trusted is not null) await LaunchTrustedUriAsync(trusted, "发布页");
    }

    private async Task LaunchTrustedUriAsync(Uri uri, string destination)
    {
        try
        {
            if (await Windows.System.Launcher.LaunchUriAsync(uri)) return;
            UpdateInfoBar.Severity = InfoBarSeverity.Warning;
            UpdateInfoBar.Title = "无法打开浏览器";
            UpdateInfoBar.Message = $"系统没有打开{destination}，请检查默认浏览器设置。";
        }
        catch (Exception exception)
        {
            UpdateInfoBar.Severity = InfoBarSeverity.Warning;
            UpdateInfoBar.Title = "无法打开浏览器";
            UpdateInfoBar.Message = $"打开{destination}失败：{exception.Message}";
        }
    }

    private void OnPageUnloaded(object sender, RoutedEventArgs e) => _updateCancellation?.Cancel();

    private static string GetCurrentVersion()
    {
        try
        {
            var version = Windows.ApplicationModel.Package.Current.Id.Version;
            return $"{version.Major}.{version.Minor}.{version.Build}.{version.Revision}";
        }
        catch
        {
            return Assembly.GetExecutingAssembly().GetName().Version?.ToString() ?? "未知";
        }
    }
}
