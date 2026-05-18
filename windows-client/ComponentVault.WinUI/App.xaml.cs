using Microsoft.UI.Xaml;
using ComponentVault.WinUI.Localization;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.ViewModels;

namespace ComponentVault.WinUI;

public partial class App : Application
{
    public App()
    {
        InitializeComponent();
        UnhandledException += OnUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnCurrentDomainUnhandledException;
    }

    public MainViewModel? MainViewModel { get; private set; }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        try
        {
            var store = new InventoryStore();
            var syncApiClient = new SyncApiClient();
            var syncService = new InventorySyncService(store, syncApiClient);
            MainViewModel = new MainViewModel(store, syncService);

            Window = new MainWindow();
            Window.Activate();
        }
        catch (Exception exception)
        {
            var logPath = StartupDiagnostics.LogException("startup", exception);
            StartupDiagnostics.ShowStartupFailure(
                AppStrings.Get("Windows_App_StartupFailureTitle"),
                exception,
                logPath
            );
            throw;
        }
    }

    public Window? Window { get; private set; }

    private void OnUnhandledException(object sender, Microsoft.UI.Xaml.UnhandledExceptionEventArgs e)
    {
        var logPath = StartupDiagnostics.LogException("ui-unhandled", e.Exception);
        StartupDiagnostics.ShowRuntimeFailure(
            AppStrings.Get("Windows_App_FatalUiFailureTitle"),
            e.Exception,
            logPath
        );
    }

    private void OnCurrentDomainUnhandledException(object? sender, System.UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is not Exception exception)
        {
            return;
        }

        var logPath = StartupDiagnostics.LogException("domain-unhandled", exception);
        StartupDiagnostics.ShowRuntimeFailure(
            AppStrings.Get("Windows_App_FatalAppFailureTitle"),
            exception,
            logPath
        );
    }
}
