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
        $"当前显示 {Components.Count} 项，共跟踪 {Dashboard.ComponentCount} 项元器件";

    public string MovementFeedSummary =>
        $"本机已记录 {Movements.Count} 条库存变动";

    public string InboundMovementCount =>
        Movements.Count(movement => movement.MovementType == "inbound").ToString();

    public string OutboundMovementCount =>
        Movements.Count(movement => movement.MovementType == "outbound").ToString();

    public string AdjustmentMovementCount =>
        Movements.Count(movement => movement.MovementType == "adjustment").ToString();

    public bool CanRunSyncActions => !IsBusy;

    public bool CanRecordMovement => AvailableComponents.Count > 0 && !IsBusy;

    public string SelectedComponentName =>
        SelectedComponent?.Name ?? "尚未选择元器件";

    public string SelectedComponentSubtitle =>
        SelectedComponent is null
            ? "请先从左侧列表选择一项，以查看封装、库位和补货风险。"
            : $"{SelectedComponent.Sku} | {SelectedComponent.Category} | {SelectedComponent.PackageName}";

    public string SelectedComponentQuantityText => (SelectedComponent?.Quantity ?? 0).ToString();

    public string SelectedComponentMinStockText => (SelectedComponent?.MinStock ?? 0).ToString();

    public string SelectedComponentLocation =>
        SelectedComponent?.Location ?? "尚未选择库位。";

    public string SelectedComponentUpdatedAt =>
        SelectedComponent?.UpdatedAt ?? "尚未选择";

    public string SelectedComponentDescription =>
        string.IsNullOrWhiteSpace(SelectedComponent?.Description)
            ? "当前元器件没有填写说明。"
            : SelectedComponent.Description;

    public string SelectedComponentStatusTitle =>
        SelectedComponent?.Status ?? "等待选择";

    public string SelectedComponentStatusMessage =>
        SelectedComponent is null
            ? "选择一项后，这里会显示库存风险和存放详情。"
            : SelectedComponent.IsLowStock
                ? "当前库存已低于或等于最低库存，建议尽快补货。"
                : "当前库存高于最低库存阈值。";

    public InfoBarSeverity SelectedComponentStatusSeverity =>
        SelectedComponent is null
            ? InfoBarSeverity.Informational
            : SelectedComponent.IsLowStock
                ? InfoBarSeverity.Warning
                : InfoBarSeverity.Success;

    public string SelectedComponentActionHint =>
        SelectedComponent is null
            ? "选择元器件后，才能执行编辑和软删除。"
            : "编辑和软删除操作将作用于当前选中的元器件。";

    public string SelectedMovementTitle =>
        SelectedMovement?.ComponentName ?? "尚未选择变动记录";

    public string SelectedMovementSubtitle =>
        SelectedMovement is null
            ? "选择一条记录后，这里会显示数量变化、原因和备注。"
            : $"{SelectedMovement.ComponentSku} | {SelectedMovement.HappenedAt}";

    public string SelectedMovementQuantityText =>
        SelectedMovement?.QuantityLabel ?? "0";

    public string SelectedMovementTypeText =>
        SelectedMovement?.MovementTypeLabel ?? "等待选择";

    public string SelectedMovementReason =>
        string.IsNullOrWhiteSpace(SelectedMovement?.Reason)
            ? "未填写原因。"
            : SelectedMovement.Reason;

    public string SelectedMovementNote =>
        string.IsNullOrWhiteSpace(SelectedMovement?.Note)
            ? "未填写备注。"
            : SelectedMovement.Note;

    public string SelectedMovementUpdatedAt =>
        SelectedMovement?.UpdatedAt ?? "尚未选择";

    public string SelectedMovementStatusTitle =>
        SelectedMovement?.MovementTypeLabel ?? "审计详情";

    public string SelectedMovementStatusMessage =>
        SelectedMovement is null
            ? "从左侧列表选择记录后，可检查最近的入库、出库与调整明细。"
            : SelectedMovement.MovementType switch
            {
                "inbound" => "这条记录会增加关联元器件的现存数量。",
                "outbound" => "这条记录会减少关联元器件的现存数量。",
                "adjustment" => "这条记录用于手动修正关联元器件的库存数量。",
                _ => "这条记录已存入本地审计日志。",
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
            ? "请先新增元器件，再记录库存变动。"
            : "入库和出库请使用正数；库存调整可以填写正数或负数。";

    public string SyncEndpointDisplay =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
            ? "未配置"
            : SyncConfiguration.ServerBaseUrl;

    public string SyncTokenDisplay =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ApiTokenMasked)
            ? "未配置"
            : SyncConfiguration.ApiTokenMasked;

    public string SyncAutoSyncDisplay =>
        SyncConfiguration.AutoSyncEnabled ? "已启用" : "未启用";

    public string SyncRiskTitle =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
            ? "需要填写服务器地址"
            : string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
                ? "需要填写 API 令牌"
                : SyncConfiguration.ApiToken == "change-me"
                    ? "仍在使用默认令牌"
                    : "可以开始同步";

    public string SyncRiskMessage =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
            ? "填写自建服务端地址后，才能进行连接测试和同步。"
            : string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
                ? "测试连接前，请先填写共享 API 令牌。"
                : SyncConfiguration.ApiToken == "change-me"
                    ? "在本地开发之外使用此桌面客户端前，请先替换默认令牌。"
                    : "服务器地址和令牌均已配置，可执行手动同步或自动同步。";

    public InfoBarSeverity SyncRiskSeverity =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
        || string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
            ? InfoBarSeverity.Warning
            : SyncConfiguration.ApiToken == "change-me"
                ? InfoBarSeverity.Warning
                : InfoBarSeverity.Success;

    public string SyncActionHint =>
        IsBusy
            ? "当前有同步相关操作正在执行，请等待完成后再发起新的操作。"
            : "建议先保存配置，再测试连接或立即同步。";

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
                    ? "元器件已在本机创建。"
                    : "元器件改动已保存到本机。"
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
            return OperationResult.Failure("请先选择一个要删除的元器件。");
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
