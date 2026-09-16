using Microsoft.UI.Xaml;
using ComponentVault.WinUI.Localization;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.ViewModels;

namespace ComponentVault.WinUI;

public partial class App : Application
{
    private const string StartupSmokeMarkerVariable = "COMPONENT_VAULT_STARTUP_SMOKE_FILE";

    public App()
    {
        InitializeComponent();
        ConfigureStartupSmokeDiagnostics();
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
            PrepareStartupSmokeIfRequested();
            Window.Activate();
        }
        catch (Exception exception)
        {
            var logPath = StartupDiagnostics.LogException("startup", exception);
            if (!string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable(StartupSmokeMarkerVariable)))
            {
                throw;
            }
            StartupDiagnostics.ShowStartupFailure(
                AppStrings.Get("Windows_App_StartupFailureTitle"),
                exception,
                logPath
            );
            throw;
        }
    }

    public Window? Window { get; private set; }

    private void ConfigureStartupSmokeDiagnostics()
    {
        if (!IsStartupSmokeRequested())
        {
            return;
        }

        DebugSettings.IsXamlResourceReferenceTracingEnabled = true;
        DebugSettings.IsBindingTracingEnabled = true;
        DebugSettings.XamlResourceReferenceFailed += (_, args) =>
            StartupDiagnostics.LogMessage("xaml-resource", args.Message);
        DebugSettings.BindingFailed += (_, args) =>
            StartupDiagnostics.LogMessage("xaml-binding", args.Message);
    }

    private void PrepareStartupSmokeIfRequested()
    {
        var markerPath = Environment.GetEnvironmentVariable(StartupSmokeMarkerVariable);
        if (string.IsNullOrWhiteSpace(markerPath) || Window?.Content is not FrameworkElement root)
        {
            return;
        }

        root.Loaded += (_, _) =>
        {
            File.WriteAllText(markerPath, "ready");
            Window?.Close();
        };
    }

    private void OnUnhandledException(object sender, Microsoft.UI.Xaml.UnhandledExceptionEventArgs e)
    {
        var logPath = StartupDiagnostics.LogException("ui-unhandled", e.Exception);
        if (IsStartupSmokeRequested())
        {
            return;
        }
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
        if (IsStartupSmokeRequested())
        {
            return;
        }
        StartupDiagnostics.ShowRuntimeFailure(
            AppStrings.Get("Windows_App_FatalAppFailureTitle"),
            exception,
            logPath
        );
    }

    private static bool IsStartupSmokeRequested() =>
        !string.IsNullOrWhiteSpace(Environment.GetEnvironmentVariable(StartupSmokeMarkerVariable));
}
