using ComponentVault.WinUI.Views;
using ComponentVault.WinUI.Localization;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI;

public sealed partial class MainWindow : Window
{
    private readonly ViewModels.MainViewModel _viewModel;
    private bool _updatingSelection;
    private bool _syncDialogOpen;

    public MainWindow()
    {
        InitializeComponent();
        Title = AppStrings.Get("Windows_App_WindowTitle");
        _viewModel =
            ((App)Application.Current).MainViewModel
            ?? throw new InvalidOperationException(AppStrings.Get("Windows_App_MainViewModelMissing"));
        AppNavigationView.DataContext = _viewModel;
        NavigateTo("Overview");
    }

    public void NavigateTo(string tag)
    {
        if (tag != "BatchInbound"
            && RootFrame.Content is BatchJlcInboundView batch
            && !batch.TryPrepareToLeave())
        {
            _updatingSelection = true;
            AppNavigationView.SelectedItem = InventoryItem;
            _updatingSelection = false;
            return;
        }

        var targetPage = tag switch
        {
            "Inventory" => typeof(ComponentsView),
            "BatchInbound" => typeof(BatchJlcInboundView),
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
            "BatchInbound" => InventoryItem,
            "Movements" => MovementsItem,
            "Bom" => BomItem,
            "Overview" => OverviewItem,
            "Settings" => SettingsItem,
            _ => null,
        };

        if (RootFrame.CurrentSourcePageType == targetPage)
        {
            UpdateBackState();
            return;
        }

        if (!ReferenceEquals(AppNavigationView.SelectedItem, targetItem))
        {
            _updatingSelection = true;
            AppNavigationView.SelectedItem = targetItem;
            _updatingSelection = false;
        }

        RootFrame.Navigate(targetPage);
        if (tag != "BatchInbound")
        {
            RootFrame.BackStack.Clear();
        }
        UpdateBackState();
    }

    private void OnSelectionChanged(
        NavigationView sender,
        NavigationViewSelectionChangedEventArgs args
    )
    {
        if (_updatingSelection)
        {
            return;
        }

        if (args.SelectedItemContainer?.Tag is not string tag)
        {
            return;
        }

        NavigateTo(tag);
    }

    private void OnBackRequested(NavigationView sender, NavigationViewBackRequestedEventArgs args)
    {
        GoBack();
    }

    public void GoBack()
    {
        if (!RootFrame.CanGoBack
            || RootFrame.Content is BatchJlcInboundView batch && !batch.TryPrepareToLeave())
        {
            return;
        }

        RootFrame.GoBack();
        AppNavigationView.SelectedItem = InventoryItem;
        UpdateBackState();
    }

    private void UpdateBackState()
    {
        AppNavigationView.IsBackEnabled = RootFrame.CanGoBack;
    }

    private async void OnSyncNowClicked(object sender, RoutedEventArgs e)
    {
        if (_viewModel.IsBusy || _syncDialogOpen)
        {
            return;
        }

        _syncDialogOpen = true;
        try
        {
            var result = await _viewModel.RunSyncAsync();
            await ShowMessageAsync(
                result.IsSuccess
                    ? AppStrings.Get("MainWindow_SyncSuccessTitle")
                    : AppStrings.Get("MainWindow_SyncFailureTitle"),
                result.IsSuccess ? _viewModel.SyncConfiguration.LastSyncMessage : result.Message
            );
        }
        finally
        {
            _syncDialogOpen = false;
        }
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
