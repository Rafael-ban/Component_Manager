using System.Collections.ObjectModel;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.ViewModels;

public sealed class MainViewModel : ObservableObject
{
    private readonly InventoryStore _store;
    private readonly InventorySyncService _syncService;

    private DashboardSnapshot _dashboard;
    private SyncConfiguration _syncConfiguration;
    private string _componentSearchText = string.Empty;
    private bool _showLowStockOnly;
    private bool _isBusy;
    private string _statusMessage;
    private ComponentRecord? _selectedComponent;
    private StockMovementRecord? _selectedMovement;
    private IReadOnlyList<ComponentRecord> _allComponents = [];

    public MainViewModel(InventoryStore store, InventorySyncService syncService)
    {
        _store = store;
        _syncService = syncService;

        _store.Initialize();
        _dashboard = new DashboardSnapshot
        {
            ComponentCount = 0,
            TotalUnits = 0,
            LowStockCount = 0,
            MovementCount = 0,
        };
        _syncConfiguration = _store.GetSyncConfiguration();
        _statusMessage = _syncConfiguration.LastSyncMessage;

        Components = [];
        LowStockComponents = [];
        Movements = [];

        Refresh();
    }

    public DashboardSnapshot Dashboard
    {
        get => _dashboard;
        private set => SetProperty(ref _dashboard, value);
    }

    public SyncConfiguration SyncConfiguration
    {
        get => _syncConfiguration;
        private set
        {
            if (SetProperty(ref _syncConfiguration, value))
            {
                NotifySyncStateChanged();
            }
        }
    }

    public ObservableCollection<ComponentRecord> Components { get; }

    public ObservableCollection<ComponentRecord> LowStockComponents { get; }

    public ObservableCollection<StockMovementRecord> Movements { get; }

    public string ComponentSearchText
    {
        get => _componentSearchText;
        set
        {
            if (SetProperty(ref _componentSearchText, value))
            {
                ApplyComponentFilter();
            }
        }
    }

    public bool ShowLowStockOnly
    {
        get => _showLowStockOnly;
        set
        {
            if (SetProperty(ref _showLowStockOnly, value))
            {
                ApplyComponentFilter();
            }
        }
    }

    public bool IsBusy
    {
        get => _isBusy;
        private set
        {
            if (SetProperty(ref _isBusy, value))
            {
                OnPropertyChanged(nameof(CanRunSyncActions));
                OnPropertyChanged(nameof(CanRecordMovement));
                OnPropertyChanged(nameof(SyncActionHint));
            }
        }
    }

    public string StatusMessage
    {
        get => _statusMessage;
        private set => SetProperty(ref _statusMessage, value);
    }

    public ComponentRecord? SelectedComponent
    {
        get => _selectedComponent;
        set
        {
            if (SetProperty(ref _selectedComponent, value))
            {
                NotifySelectedComponentStateChanged();
            }
        }
    }

    public IReadOnlyList<ComponentRecord> AvailableComponents => _allComponents;

    public StockMovementRecord? SelectedMovement
    {
        get => _selectedMovement;
        set
        {
            if (SetProperty(ref _selectedMovement, value))
            {
                NotifySelectedMovementStateChanged();
            }
        }
    }

    public string ComponentInventorySummary =>
        $"{Components.Count} visible of {Dashboard.ComponentCount} tracked components";

    public string MovementFeedSummary =>
        $"{Movements.Count} recorded movements stored locally";

    public string InboundMovementCount =>
        Movements.Count(movement => movement.MovementType == "inbound").ToString();

    public string OutboundMovementCount =>
        Movements.Count(movement => movement.MovementType == "outbound").ToString();

    public string AdjustmentMovementCount =>
        Movements.Count(movement => movement.MovementType == "adjustment").ToString();

    public bool CanRunSyncActions => !IsBusy;

    public bool CanRecordMovement => AvailableComponents.Count > 0 && !IsBusy;

    public string SelectedComponentName =>
        SelectedComponent?.Name ?? "No component selected";

    public string SelectedComponentSubtitle =>
        SelectedComponent is null
            ? "Select a component from the list to inspect package, location, and reorder posture."
            : $"{SelectedComponent.Sku} | {SelectedComponent.Category} | {SelectedComponent.PackageName}";

    public string SelectedComponentQuantityText => (SelectedComponent?.Quantity ?? 0).ToString();

    public string SelectedComponentMinStockText => (SelectedComponent?.MinStock ?? 0).ToString();

    public string SelectedComponentLocation =>
        SelectedComponent?.Location ?? "No storage location selected.";

    public string SelectedComponentUpdatedAt =>
        SelectedComponent?.UpdatedAt ?? "No selection";

    public string SelectedComponentDescription =>
        string.IsNullOrWhiteSpace(SelectedComponent?.Description)
            ? "No description recorded for this component."
            : SelectedComponent.Description;

    public string SelectedComponentStatusTitle =>
        SelectedComponent?.Status ?? "Selection needed";

    public string SelectedComponentStatusMessage =>
        SelectedComponent is null
            ? "Choose a row to review stock risk and storage details."
            : SelectedComponent.IsLowStock
                ? "Quantity is at or below the configured minimum stock."
                : "Quantity is above the configured minimum stock.";

    public InfoBarSeverity SelectedComponentStatusSeverity =>
        SelectedComponent is null
            ? InfoBarSeverity.Informational
            : SelectedComponent.IsLowStock
                ? InfoBarSeverity.Warning
                : InfoBarSeverity.Success;

    public string SelectedComponentActionHint =>
        SelectedComponent is null
            ? "Edit and soft delete actions become available after you select a component."
            : "Edit and soft delete actions now target the selected component.";

    public string SelectedMovementTitle =>
        SelectedMovement?.ComponentName ?? "No movement selected";

    public string SelectedMovementSubtitle =>
        SelectedMovement is null
            ? "Select a stock movement to inspect the exact quantity change and audit note."
            : $"{SelectedMovement.ComponentSku} | {SelectedMovement.HappenedAt}";

    public string SelectedMovementQuantityText =>
        SelectedMovement?.QuantityLabel ?? "0";

    public string SelectedMovementTypeText =>
        SelectedMovement?.MovementTypeLabel ?? "Selection needed";

    public string SelectedMovementReason =>
        string.IsNullOrWhiteSpace(SelectedMovement?.Reason)
            ? "No reason recorded."
            : SelectedMovement.Reason;

    public string SelectedMovementNote =>
        string.IsNullOrWhiteSpace(SelectedMovement?.Note)
            ? "No note recorded for this movement."
            : SelectedMovement.Note;

    public string SelectedMovementUpdatedAt =>
        SelectedMovement?.UpdatedAt ?? "No selection";

    public string SelectedMovementStatusTitle =>
        SelectedMovement?.MovementTypeLabel ?? "Audit detail";

    public string SelectedMovementStatusMessage =>
        SelectedMovement is null
            ? "Use the list to review recent inbound, outbound, and adjustment activity."
            : SelectedMovement.MovementType switch
            {
                "inbound" => "This movement increases the on-hand quantity for the related component.",
                "outbound" => "This movement decreases the on-hand quantity for the related component.",
                "adjustment" => "This movement manually corrects the stored quantity for the related component.",
                _ => "This movement is stored in the local audit trail.",
            };

    public InfoBarSeverity SelectedMovementStatusSeverity =>
        SelectedMovement is null
            ? InfoBarSeverity.Informational
            : SelectedMovement.MovementType switch
            {
                "inbound" => InfoBarSeverity.Success,
                "outbound" => InfoBarSeverity.Warning,
                "adjustment" => InfoBarSeverity.Informational,
                _ => InfoBarSeverity.Informational,
            };

    public string SelectedMovementActionHint =>
        AvailableComponents.Count == 0
            ? "Add a component before recording stock movement."
            : "Use positive values for inbound and outbound. Adjustment can be positive or negative.";

    public string SyncEndpointDisplay =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
            ? "Not configured"
            : SyncConfiguration.ServerBaseUrl;

    public string SyncTokenDisplay =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ApiTokenMasked)
            ? "Not configured"
            : SyncConfiguration.ApiTokenMasked;

    public string SyncAutoSyncDisplay =>
        SyncConfiguration.AutoSyncEnabled ? "Enabled" : "Disabled";

    public string SyncRiskTitle =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
            ? "Server URL required"
            : string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
                ? "API token required"
                : SyncConfiguration.ApiToken == "change-me"
                    ? "Default token in use"
                    : "Ready to sync";

    public string SyncRiskMessage =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
            ? "Enter the self-hosted server URL to enable connection tests and sync."
            : string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
                ? "Enter the shared API token before testing the connection."
                : SyncConfiguration.ApiToken == "change-me"
                    ? "Replace the default token before using this desktop client outside local development."
                    : "Server URL and token are configured for manual or automatic sync.";

    public InfoBarSeverity SyncRiskSeverity =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
        || string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
            ? InfoBarSeverity.Warning
            : SyncConfiguration.ApiToken == "change-me"
                ? InfoBarSeverity.Warning
                : InfoBarSeverity.Success;

    public string SyncActionHint =>
        IsBusy
            ? "A sync-related action is running. Wait for completion before starting another one."
            : "Save configuration changes before testing the connection or running sync now.";

    public void Refresh()
    {
        Dashboard = _store.GetDashboardSnapshot();
        _allComponents = _store.GetComponents();
        ReplaceCollection(
            LowStockComponents,
            _store.GetLowStockComponents()
        );
        ReplaceCollection(Movements, _store.GetMovements());
        ApplyMovementSelection();
        SyncConfiguration = _store.GetSyncConfiguration();
        StatusMessage = SyncConfiguration.LastSyncMessage;
        OnPropertyChanged(nameof(CanRecordMovement));
        ApplyComponentFilter();
    }

    public OperationResult SaveComponent(ComponentDraft draft)
    {
        try
        {
            var savedComponent = _store.SaveComponent(draft);
            Refresh();
            SelectedComponent = Components.FirstOrDefault(component => component.Id == savedComponent.Id);
            return OperationResult.Success(
                draft.Id is null
                    ? "Component created locally."
                    : "Component changes saved locally."
            );
        }
        catch (Exception exception)
        {
            return OperationResult.Failure(exception.Message);
        }
    }

    public OperationResult DeleteSelectedComponent()
    {
        if (SelectedComponent is null)
        {
            return OperationResult.Failure("Select a component to delete.");
        }

        var result = _store.SoftDeleteComponent(SelectedComponent.Id);
        Refresh();
        SelectedComponent = null;
        return result;
    }

    public OperationResult RecordMovement(MovementEntryDraft draft)
    {
        try
        {
            var result = _store.RecordMovement(draft);
            Refresh();
            SelectedComponent = Components.FirstOrDefault(component => component.Id == draft.ComponentId);
            SelectedMovement = Movements.FirstOrDefault(movement => movement.ComponentId == draft.ComponentId);
            return result;
        }
        catch (Exception exception)
        {
            return OperationResult.Failure(exception.Message);
        }
    }

    public OperationResult SaveSyncConfiguration(
        string serverBaseUrl,
        string apiToken,
        bool autoSyncEnabled
    )
    {
        var result = _store.SaveSyncConfiguration(serverBaseUrl, apiToken, autoSyncEnabled);
        Refresh();
        return result;
    }

    public async Task<OperationResult> TestConnectionAsync()
    {
        IsBusy = true;
        try
        {
            var result = await _syncService.TestConnectionAsync();
            StatusMessage = result.Message;
            return result;
        }
        finally
        {
            IsBusy = false;
        }
    }

    public async Task<SyncRunResult> RunSyncAsync()
    {
        IsBusy = true;
        try
        {
            var result = await _syncService.RunSyncAsync();
            Refresh();
            StatusMessage = result.IsSuccess ? SyncConfiguration.LastSyncMessage : result.Message;
            return result;
        }
        finally
        {
            IsBusy = false;
        }
    }

    private void ApplyComponentFilter()
    {
        IEnumerable<ComponentRecord> filtered = _allComponents;

        if (ShowLowStockOnly)
        {
            filtered = filtered.Where(component => component.IsLowStock);
        }

        if (!string.IsNullOrWhiteSpace(ComponentSearchText))
        {
            var query = ComponentSearchText.Trim();
            filtered = filtered.Where(component =>
                component.Name.Contains(query, StringComparison.OrdinalIgnoreCase)
                || component.Sku.Contains(query, StringComparison.OrdinalIgnoreCase)
                || component.Category.Contains(query, StringComparison.OrdinalIgnoreCase)
                || component.Location.Contains(query, StringComparison.OrdinalIgnoreCase)
            );
        }

        ReplaceCollection(Components, filtered);
        OnPropertyChanged(nameof(ComponentInventorySummary));

        if (Components.Count == 0)
        {
            SelectedComponent = null;
            return;
        }

        if (SelectedComponent is null)
        {
            SelectedComponent = Components[0];
            return;
        }

        var currentSelection = Components.FirstOrDefault(component =>
            component.Id == SelectedComponent.Id
        );
        SelectedComponent = currentSelection ?? Components[0];
    }

    private void ApplyMovementSelection()
    {
        OnPropertyChanged(nameof(MovementFeedSummary));
        OnPropertyChanged(nameof(InboundMovementCount));
        OnPropertyChanged(nameof(OutboundMovementCount));
        OnPropertyChanged(nameof(AdjustmentMovementCount));

        if (Movements.Count == 0)
        {
            SelectedMovement = null;
            return;
        }

        if (SelectedMovement is null)
        {
            SelectedMovement = Movements[0];
            return;
        }

        var currentSelection = Movements.FirstOrDefault(movement =>
            movement.Id == SelectedMovement.Id
        );
        SelectedMovement = currentSelection ?? Movements[0];
    }

    private static void ReplaceCollection<T>(
        ObservableCollection<T> collection,
        IEnumerable<T> items
    )
    {
        collection.Clear();
        foreach (var item in items)
        {
            collection.Add(item);
        }
    }

    private void NotifySelectedComponentStateChanged()
    {
        OnPropertyChanged(nameof(SelectedComponentName));
        OnPropertyChanged(nameof(SelectedComponentSubtitle));
        OnPropertyChanged(nameof(SelectedComponentQuantityText));
        OnPropertyChanged(nameof(SelectedComponentMinStockText));
        OnPropertyChanged(nameof(SelectedComponentLocation));
        OnPropertyChanged(nameof(SelectedComponentUpdatedAt));
        OnPropertyChanged(nameof(SelectedComponentDescription));
        OnPropertyChanged(nameof(SelectedComponentStatusTitle));
        OnPropertyChanged(nameof(SelectedComponentStatusMessage));
        OnPropertyChanged(nameof(SelectedComponentStatusSeverity));
        OnPropertyChanged(nameof(SelectedComponentActionHint));
    }

    private void NotifySelectedMovementStateChanged()
    {
        OnPropertyChanged(nameof(SelectedMovementTitle));
        OnPropertyChanged(nameof(SelectedMovementSubtitle));
        OnPropertyChanged(nameof(SelectedMovementQuantityText));
        OnPropertyChanged(nameof(SelectedMovementTypeText));
        OnPropertyChanged(nameof(SelectedMovementReason));
        OnPropertyChanged(nameof(SelectedMovementNote));
        OnPropertyChanged(nameof(SelectedMovementUpdatedAt));
        OnPropertyChanged(nameof(SelectedMovementStatusTitle));
        OnPropertyChanged(nameof(SelectedMovementStatusMessage));
        OnPropertyChanged(nameof(SelectedMovementStatusSeverity));
        OnPropertyChanged(nameof(SelectedMovementActionHint));
    }

    private void NotifySyncStateChanged()
    {
        OnPropertyChanged(nameof(SyncEndpointDisplay));
        OnPropertyChanged(nameof(SyncTokenDisplay));
        OnPropertyChanged(nameof(SyncAutoSyncDisplay));
        OnPropertyChanged(nameof(SyncRiskTitle));
        OnPropertyChanged(nameof(SyncRiskMessage));
        OnPropertyChanged(nameof(SyncRiskSeverity));
    }
}
