using Microsoft.UI.Xaml;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.ViewModels;

namespace ComponentVault.WinUI;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        var store = new InventoryStore();
        var syncApiClient = new SyncApiClient();
        var syncService = new InventorySyncService(store, syncApiClient);
        MainViewModel = new MainViewModel(store, syncService);
    }

    public MainViewModel MainViewModel { get; }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        Window = new MainWindow();
        Window.Activate();
    }

    public Window? Window { get; private set; }
}
