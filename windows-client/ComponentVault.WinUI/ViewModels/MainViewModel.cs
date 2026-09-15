using System.Collections.ObjectModel;
using System.Linq;
using ComponentVault.WinUI.Localization;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.Services;
using ComponentVault.WinUI.Services.Bom;
using ComponentVault.WinUI.Services.Migration;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.ViewModels;

public sealed class MainViewModel : ObservableObject
{
    private const string AllCategoriesOption = "全部分类";
    private const string AllLocationsOption = "全部仓位";
    private const string SortUpdatedDescOption = "最近更新";
    private const string SortNameAscOption = "名称 A-Z";
    private const string SortLowStockFirstOption = "库存紧张优先";
    private const string SortQuantityDescOption = "库存数量";
    private static readonly TimeSpan AutoSyncDelay = TimeSpan.FromMilliseconds(750);

    private readonly InventoryStore _store;
    private readonly InventorySyncService _syncService;
    private readonly BomPlanningService _bomPlanningService = new();
    private readonly SemaphoreSlim _syncGate = new(1, 1);
    private readonly object _autoSyncLock = new();
    private CancellationTokenSource? _autoSyncDelayCancellation;

    private DashboardSnapshot _dashboard;
    private SyncConfiguration _syncConfiguration;
    private string _componentSearchText = string.Empty;
    private bool _showLowStockOnly;
    private bool _isBusy;
    private string _statusMessage;
    private ComponentRecord? _selectedComponent;
    private StockMovementRecord? _selectedMovement;
    private IReadOnlyList<ComponentRecord> _allComponents = [];
    private string _selectedComponentCategoryFilter = AllCategoriesOption;
    private string _selectedComponentLocationFilter = AllLocationsOption;
    private string _selectedComponentSortOption = SortUpdatedDescOption;

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
        OverviewLowStockComponents = [];
        OverviewRecentMovements = [];
        SelectedComponentRecentMovements = [];
        StorageLocations = [];
        InventoryCategoryOptions = [];
        InventoryLocationOptions = [];
        InventorySortOptions = new ObservableCollection<string>(
            [
                SortUpdatedDescOption,
                SortLowStockFirstOption,
                SortNameAscOption,
                SortQuantityDescOption,
            ]
        );

        Refresh();
        ScheduleAutoSync();
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

    public ObservableCollection<ComponentRecord> OverviewLowStockComponents { get; }

    public ObservableCollection<StockMovementRecord> OverviewRecentMovements { get; }

    public ObservableCollection<StockMovementRecord> SelectedComponentRecentMovements { get; }
    public ObservableCollection<StorageLocationRecord> StorageLocations { get; }

    public ObservableCollection<string> InventoryCategoryOptions { get; }

    public ObservableCollection<string> InventoryLocationOptions { get; }

    public ObservableCollection<string> InventorySortOptions { get; }

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

    public string SelectedComponentCategoryFilter
    {
        get => _selectedComponentCategoryFilter;
        set
        {
            if (SetProperty(ref _selectedComponentCategoryFilter, value))
            {
                ApplyComponentFilter();
            }
        }
    }

    public string SelectedComponentLocationFilter
    {
        get => _selectedComponentLocationFilter;
        set
        {
            if (SetProperty(ref _selectedComponentLocationFilter, value))
            {
                ApplyComponentFilter();
            }
        }
    }

    public string SelectedComponentSortOption
    {
        get => _selectedComponentSortOption;
        set
        {
            if (SetProperty(ref _selectedComponentSortOption, value))
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
                UpdateSelectedComponentRecentMovements();
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

    public string ShellSyncSummary =>
        string.IsNullOrWhiteSpace(SyncConfiguration.LastSyncedAt)
            ? "尚未完成同步"
            : $"最近同步：{SyncConfiguration.LastSyncedAt}";

    public string ComponentInventorySummary =>
        $"当前显示 {Components.Count} 项，共跟踪 {Dashboard.ComponentCount} 项元器件。";

    public string InventoryFilterSummary
    {
        get
        {
            var summaries = new List<string> { $"结果 {Components.Count}/{Dashboard.ComponentCount}" };

            if (ShowLowStockOnly)
            {
                summaries.Add("低库存");
            }

            if (!string.IsNullOrWhiteSpace(ComponentSearchText))
            {
                summaries.Add($"搜索：{ComponentSearchText.Trim()}");
            }

            if (SelectedComponentCategoryFilter != AllCategoriesOption)
            {
                summaries.Add($"分类：{CategoryDisplay.Localize(SelectedComponentCategoryFilter)}");
            }

            if (SelectedComponentLocationFilter != AllLocationsOption)
            {
                summaries.Add($"仓位：{SelectedComponentLocationFilter}");
            }

            summaries.Add($"排序：{SelectedComponentSortOption}");
            return string.Join(" · ", summaries);
        }
    }

    public string InventoryWorkspaceSummary =>
        $"分类 {Math.Max(InventoryCategoryOptions.Count - 1, 0)} 个 · 仓位 {Math.Max(InventoryLocationOptions.Count - 1, 0)} 个 · 低库存 {Dashboard.LowStockCount} 项";

    public string InventoryEmptyMessage =>
        Dashboard.ComponentCount == 0
            ? "还没有元器件。先新增一个元器件开始建库。"
            : "当前筛选条件下没有结果，尝试清空搜索或切换筛选。";

    public string MovementFeedSummary =>
        $"本机已记录 {Movements.Count} 条库存变动。";

    public string MovementWorkspaceSummary =>
        $"入库 {InboundMovementCount} · 出库 {OutboundMovementCount} · 调整 {AdjustmentMovementCount} · 净变化 {MovementNetQuantityText}";

    public string InboundMovementCount =>
        Movements.Count(movement => movement.MovementType == "inbound").ToString();

    public string OutboundMovementCount =>
        Movements.Count(movement => movement.MovementType == "outbound").ToString();

    public string AdjustmentMovementCount =>
        Movements.Count(movement => movement.MovementType == "adjustment").ToString();

    public string MovementNetQuantityText =>
        Movements.Sum(movement => movement.QuantityChange).ToString("+#,0;-#,0;0");

    public string OverviewLowStockSummary =>
        OverviewLowStockComponents.Count == 0
            ? "当前没有低库存预警。"
            : $"共有 {Dashboard.LowStockCount} 项低库存，这里展示最需要关注的 {OverviewLowStockComponents.Count} 项。";

    public string OverviewRecentMovementSummary =>
        OverviewRecentMovements.Count == 0
            ? "最近还没有新的出入库记录。"
            : $"最近 {OverviewRecentMovements.Count} 条变动可用于快速回看操作。";

    public string OverviewSyncSummary =>
        string.IsNullOrWhiteSpace(SyncConfiguration.LastSyncMessage)
            ? $"{ShellSyncSummary} · 等待首次同步"
            : $"{ShellSyncSummary} · {SyncConfiguration.LastSyncMessage}";

    public bool CanRunSyncActions => !IsBusy;

    public bool CanRecordMovement => AvailableComponents.Count > 0 && !IsBusy;

    public bool CanEditSelectedComponent => SelectedComponent is not null;

    public bool CanDeleteSelectedComponent => SelectedComponent is not null;

    public string SelectedComponentName =>
        SelectedComponent?.Name ?? "尚未选择元器件";

    public string SelectedComponentSubtitle =>
        SelectedComponent is null
            ? "请先从左侧列表中选择一项，以查看封装、仓位和补货风险。"
            : $"{SelectedComponent.Sku} | {SelectedComponent.DisplayCategory} | {SelectedComponent.PackageName}";

    public string SelectedComponentQuantityText => (SelectedComponent?.Quantity ?? 0).ToString();

    public string SelectedComponentOutboundQuantityText =>
        (SelectedComponent?.CumulativeOutboundQuantity ?? 0).ToString();

    public string SelectedComponentStatisticalTotalText =>
        (SelectedComponent?.StatisticalTotal ?? 0).ToString();

    public string SelectedComponentMinStockText => (SelectedComponent?.MinStock ?? 0).ToString();

    public string SelectedComponentStockDeltaText =>
        SelectedComponent is null
            ? "0"
            : (SelectedComponent.Quantity - SelectedComponent.MinStock).ToString("+#,0;-#,0;0");

    public string SelectedComponentLocation =>
        SelectedComponent?.Location ?? "尚未选择仓位。";

    public string SelectedComponentUpdatedAt =>
        SelectedComponent?.DisplayUpdatedAt ?? "尚未选择";

    public string SelectedComponentDescription =>
        string.IsNullOrWhiteSpace(SelectedComponent?.Description)
            ? "当前元器件没有填写备注或描述。"
            : SelectedComponent.Description;

    public string SelectedComponentStatusTitle =>
        SelectedComponent?.Status ?? "等待选择";

    public string SelectedComponentStatusMessage =>
        SelectedComponent is null
            ? "选择元器件后，这里会显示库存风险、仓位和最近变动摘要。"
            : SelectedComponent.IsLowStock
                ? "当前库存已低于或等于最低库存，建议尽快补货。"
                : "当前库存高于最低库存阈值，可继续支持日常领用。";

    public InfoBarSeverity SelectedComponentStatusSeverity =>
        SelectedComponent is null
            ? InfoBarSeverity.Informational
            : SelectedComponent.IsLowStock
                ? InfoBarSeverity.Warning
                : InfoBarSeverity.Success;

    public string SelectedComponentMovementSummary =>
        SelectedComponent is null
            ? "选择元器件后，在这里查看最近的出入库摘要。"
            : SelectedComponentRecentMovements.Count == 0
                ? "该元器件还没有关联的出入库记录。"
                : $"最近 {SelectedComponentRecentMovements.Count} 条记录与当前元器件相关。";

    public string SelectedComponentActionHint =>
        SelectedComponent is null
            ? "选择元器件后，才能执行编辑和软删除。"
            : "编辑会保留本地优先与同步语义；删除采用软删除，不会直接清除历史记录。";

    public string SelectedMovementTitle =>
        SelectedMovement?.ComponentName ?? "尚未选择变动记录";

    public string SelectedMovementSubtitle =>
        SelectedMovement is null
            ? "从左侧台账中选择一条记录，以查看数量变化、原因和审计字段。"
            : $"{SelectedMovement.ComponentSku} | {SelectedMovement.MovementTypeLabel}";

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
        SelectedMovement?.DisplayUpdatedAt ?? "尚未选择";

    public string SelectedMovementTimeline =>
        SelectedMovement is null
            ? "尚未选择记录"
            : SelectedMovement.DisplayTimeline;

    public string SelectedMovementStatusTitle =>
        SelectedMovement?.MovementTypeLabel ?? "审计详情";

    public string SelectedMovementStatusMessage =>
        SelectedMovement is null
            ? "选择一条记录后，这里会显示对库存的影响和原因摘要。"
            : SelectedMovement.MovementType switch
            {
                "inbound" => "这条记录会增加关联元器件的库存数量。",
                "outbound" => "这条记录会减少关联元器件的库存数量。",
                "adjustment" => "这条记录用于手动修正关联元器件的库存数量。",
                _ => "这条记录已经写入本地审计台账。",
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
            ? "缺少服务器地址"
            : string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
                ? "缺少 API 令牌"
                : SyncConfiguration.ApiToken == "change-me"
                    ? "请替换默认令牌"
                    : "同步配置已就绪";

    public string SyncRiskMessage =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
            ? "填写服务端地址后，Windows 客户端才能执行连接测试与手动同步。"
            : string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
                ? "当前尚未填写 API 令牌，测试连接与同步会失败。"
                : SyncConfiguration.ApiToken == "change-me"
                    ? "当前仍在使用默认示例令牌，建议立即替换为真实共享令牌。"
                    : "连接信息看起来完整，可以执行连接测试或立即同步。";

    public InfoBarSeverity SyncRiskSeverity =>
        string.IsNullOrWhiteSpace(SyncConfiguration.ServerBaseUrl)
        || string.IsNullOrWhiteSpace(SyncConfiguration.ApiToken)
        || SyncConfiguration.ApiToken == "change-me"
            ? InfoBarSeverity.Warning
            : InfoBarSeverity.Success;

    public string SyncConfigurationSummary =>
        $"设备 {SyncConfiguration.DeviceId} · {SyncAutoSyncDisplay} · 最近结果：{SyncConfiguration.LastSyncMessage}";

    public string SyncActionHint =>
        IsBusy
            ? "正在执行连接测试或同步，请等待当前操作完成。"
            : "所有写入仍然会先落到本地 SQLite；同步仅在你主动触发或开启自动同步后发生。";

    public void Refresh()
    {
        Dashboard = _store.GetDashboardSnapshot();
        var components = _store.GetComponents();
        var outboundTotals = _store.GetCumulativeOutboundQuantities();
        _allComponents = components.Select(component => new ComponentRecord
        {
            Id = component.Id,
            Sku = component.Sku,
            Name = component.Name,
            Category = component.Category,
            PackageName = component.PackageName,
            Location = component.Location,
            Description = component.Description,
            Quantity = component.Quantity,
            MinStock = component.MinStock,
            UpdatedAt = component.UpdatedAt,
            Deleted = component.Deleted,
            CumulativeOutboundQuantity = outboundTotals.GetValueOrDefault(component.Id),
            Allocations = component.Allocations,
            BaseUpdatedAt = component.BaseUpdatedAt,
        }).ToArray();
        ReplaceCollection(StorageLocations, _store.GetStorageLocations());
        ReplaceCollection(LowStockComponents, _store.GetLowStockComponents());
        ReplaceCollection(Movements, _store.GetMovements());
        SyncConfiguration = _store.GetSyncConfiguration();
        StatusMessage = string.IsNullOrWhiteSpace(SyncConfiguration.LastSyncMessage)
            ? "尚未执行同步。"
            : SyncConfiguration.LastSyncMessage;

        UpdateInventoryFilterOptions();
        UpdateOverviewCollections();
        ApplyMovementSelection();
        ApplyComponentFilter();

        OnPropertyChanged(nameof(CanRecordMovement));
        OnPropertyChanged(nameof(ComponentInventorySummary));
        OnPropertyChanged(nameof(MovementFeedSummary));
        OnPropertyChanged(nameof(MovementWorkspaceSummary));
        OnPropertyChanged(nameof(MovementNetQuantityText));
        OnPropertyChanged(nameof(OverviewLowStockSummary));
        OnPropertyChanged(nameof(OverviewRecentMovementSummary));
        OnPropertyChanged(nameof(OverviewSyncSummary));
        OnPropertyChanged(nameof(SyncConfigurationSummary));
        OnPropertyChanged(nameof(ShellSyncSummary));
    }

    public OperationResult SaveComponent(ComponentDraft draft)
    {
        try
        {
            var savedComponent = _store.SaveComponent(draft);
            Refresh();
            SelectedComponent = Components.FirstOrDefault(component => component.Id == savedComponent.Id);
            ScheduleAutoSync();
            return OperationResult.Success(
                draft.Id is null ? "元器件已新增。" : "元器件已更新。"
            );
        }
        catch (Exception exception)
        {
            return OperationResult.Failure(exception.Message);
        }
    }

    public OperationResult SaveStorageLocation(string id, string name)
    {
        var result = _store.SaveStorageLocation(id, name);
        if (result.IsSuccess) { Refresh(); ScheduleAutoSync(); }
        return result;
    }
    public OperationResult DeleteStorageLocation(string id)
    {
        var result = _store.DeleteStorageLocation(id);
        if (result.IsSuccess) { Refresh(); ScheduleAutoSync(); }
        return result;
    }

    private InventoryRestorePreview? _approvedInventoryRestorePreview;
    public void ExportInventoryWorkbook(string path) => new InventoryWorkbookBackup(_store).Export(path);
    public InventoryRestorePreview PreviewInventoryWorkbook(string path) => _approvedInventoryRestorePreview = new InventoryWorkbookBackup(_store).Preview(path);
    public OperationResult ImportInventoryWorkbook(string path)
    {
        var result = new InventoryWorkbookBackup(_store).ImportNewOnly(path, _approvedInventoryRestorePreview);
        _approvedInventoryRestorePreview = null;
        if (result.IsSuccess) { Refresh(); ScheduleAutoSync(); }
        return result;
    }

    public OperationResult DeleteSelectedComponent()
    {
        if (SelectedComponent is null)
        {
            return OperationResult.Failure("请先选择一个元器件。");
        }

        var result = _store.SoftDeleteComponent(SelectedComponent.Id);
        Refresh();
        if (result.IsSuccess)
        {
            ScheduleAutoSync();
        }
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
            if (result.IsSuccess)
            {
                ScheduleAutoSync();
            }
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
        ScheduleAutoSync();
        return result;
    }

    public BomPreview CreateBomPreview(
        BomDocument document,
        string projectName,
        int batchQuantity,
        IReadOnlyDictionary<int, string>? selectedComponentIds = null
    ) => _bomPlanningService.CreatePreview(
        document,
        new BomImportOptions(projectName, batchQuantity),
        _store.GetComponents(),
        selectedComponentIds
    );

    public BomConfirmResult ConfirmBomConsumption(BomConfirmRequest request)
    {
        var result = _store.ConfirmBomConsumption(request);
        Refresh();
        if (result.IsSuccess && !result.AlreadyApplied)
        {
            ScheduleAutoSync();
        }
        return result;
    }

    public ComponentHubPreview PreviewComponentHubMigration(
        string filePath,
        DuplicateSkuPolicy duplicatePolicy
    ) => new ComponentHubMigrationReader().Read(filePath, _store.GetComponents(), duplicatePolicy);

    public OperationResult ImportComponentHub(ComponentHubImportRequest request)
    {
        var result = _store.ImportComponentHub(request);
        Refresh();
        if (result.IsSuccess) ScheduleAutoSync();
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

    public Task<SyncRunResult> RunSyncAsync() => RunSyncAsync(CancellationToken.None);

    private async Task<SyncRunResult> RunSyncAsync(CancellationToken gateCancellationToken)
    {
        await _syncGate.WaitAsync(gateCancellationToken);
        try
        {
            IsBusy = true;
            var result = await _syncService.RunSyncAsync();
            Refresh();
            StatusMessage = result.IsSuccess ? SyncConfiguration.LastSyncMessage : result.Message;
            return result;
        }
        finally
        {
            IsBusy = false;
            _syncGate.Release();
        }
    }

    private void ScheduleAutoSync()
    {
        if (!SyncConfiguration.AutoSyncEnabled)
        {
            lock (_autoSyncLock)
            {
                _autoSyncDelayCancellation?.Cancel();
                _autoSyncDelayCancellation?.Dispose();
                _autoSyncDelayCancellation = null;
            }
            return;
        }

        CancellationTokenSource cancellation;
        lock (_autoSyncLock)
        {
            _autoSyncDelayCancellation?.Cancel();
            _autoSyncDelayCancellation?.Dispose();
            cancellation = new CancellationTokenSource();
            _autoSyncDelayCancellation = cancellation;
        }

        _ = RunScheduledAutoSyncAsync(cancellation);
    }

    private async Task RunScheduledAutoSyncAsync(CancellationTokenSource cancellation)
    {
        try
        {
            await Task.Delay(AutoSyncDelay, cancellation.Token);
            await RunSyncAsync(cancellation.Token);
        }
        catch (OperationCanceledException) when (cancellation.IsCancellationRequested)
        {
        }
        catch (Exception exception)
        {
            StatusMessage = $"自动同步失败：{exception.Message}";
        }
        finally
        {
            lock (_autoSyncLock)
            {
                if (ReferenceEquals(_autoSyncDelayCancellation, cancellation))
                {
                    _autoSyncDelayCancellation = null;
                    cancellation.Dispose();
                }
            }
        }
    }

    private void ApplyComponentFilter()
    {
        IEnumerable<ComponentRecord> filtered = _allComponents;

        if (ShowLowStockOnly)
        {
            filtered = filtered.Where(component => component.IsLowStock);
        }

        if (SelectedComponentCategoryFilter != AllCategoriesOption)
        {
            filtered = filtered.Where(component => component.Category == SelectedComponentCategoryFilter);
        }

        if (SelectedComponentLocationFilter != AllLocationsOption)
        {
            filtered = filtered.Where(component => component.Location == SelectedComponentLocationFilter);
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

        filtered = SelectedComponentSortOption switch
        {
            SortLowStockFirstOption => filtered
                .OrderBy(component => component.IsLowStock ? 0 : 1)
                .ThenBy(component => component.Quantity - component.MinStock)
                .ThenBy(component => component.Name),
            SortNameAscOption => filtered.OrderBy(component => component.Name),
            SortQuantityDescOption => filtered
                .OrderByDescending(component => component.Quantity)
                .ThenBy(component => component.Name),
            _ => filtered.OrderByDescending(component => component.UpdatedAt),
        };

        ReplaceCollection(Components, filtered);
        OnPropertyChanged(nameof(ComponentInventorySummary));
        OnPropertyChanged(nameof(InventoryFilterSummary));
        OnPropertyChanged(nameof(InventoryWorkspaceSummary));
        OnPropertyChanged(nameof(InventoryEmptyMessage));

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
        OnPropertyChanged(nameof(MovementWorkspaceSummary));
        OnPropertyChanged(nameof(InboundMovementCount));
        OnPropertyChanged(nameof(OutboundMovementCount));
        OnPropertyChanged(nameof(AdjustmentMovementCount));
        OnPropertyChanged(nameof(MovementNetQuantityText));

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

    private void UpdateInventoryFilterOptions()
    {
        var categoryOptions = _allComponents
            .Select(component => component.Category)
            .Where(category => !string.IsNullOrWhiteSpace(category))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(category => category, StringComparer.OrdinalIgnoreCase);
        ReplaceCollection(
            InventoryCategoryOptions,
            new[] { AllCategoriesOption }.Concat(categoryOptions)
        );

        if (!InventoryCategoryOptions.Contains(_selectedComponentCategoryFilter))
        {
            _selectedComponentCategoryFilter = AllCategoriesOption;
            OnPropertyChanged(nameof(SelectedComponentCategoryFilter));
        }

        var locationOptions = _allComponents
            .Select(component => component.Location)
            .Where(location => !string.IsNullOrWhiteSpace(location))
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(location => location, StringComparer.OrdinalIgnoreCase);
        ReplaceCollection(
            InventoryLocationOptions,
            new[] { AllLocationsOption }.Concat(locationOptions)
        );

        if (!InventoryLocationOptions.Contains(_selectedComponentLocationFilter))
        {
            _selectedComponentLocationFilter = AllLocationsOption;
            OnPropertyChanged(nameof(SelectedComponentLocationFilter));
        }

        if (!InventorySortOptions.Contains(_selectedComponentSortOption))
        {
            _selectedComponentSortOption = SortUpdatedDescOption;
            OnPropertyChanged(nameof(SelectedComponentSortOption));
        }
    }

    private void UpdateOverviewCollections()
    {
        ReplaceCollection(OverviewLowStockComponents, LowStockComponents.Take(5));
        ReplaceCollection(OverviewRecentMovements, Movements.Take(5));
        OnPropertyChanged(nameof(OverviewLowStockSummary));
        OnPropertyChanged(nameof(OverviewRecentMovementSummary));
    }

    private void UpdateSelectedComponentRecentMovements()
    {
        var componentId = SelectedComponent?.Id;
        var recentMovements = string.IsNullOrWhiteSpace(componentId)
            ? []
            : Movements
                .Where(movement => movement.ComponentId == componentId)
                .Take(4);
        ReplaceCollection(SelectedComponentRecentMovements, recentMovements);
        OnPropertyChanged(nameof(SelectedComponentMovementSummary));
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
        OnPropertyChanged(nameof(CanEditSelectedComponent));
        OnPropertyChanged(nameof(CanDeleteSelectedComponent));
        OnPropertyChanged(nameof(SelectedComponentName));
        OnPropertyChanged(nameof(SelectedComponentSubtitle));
        OnPropertyChanged(nameof(SelectedComponentQuantityText));
        OnPropertyChanged(nameof(SelectedComponentOutboundQuantityText));
        OnPropertyChanged(nameof(SelectedComponentStatisticalTotalText));
        OnPropertyChanged(nameof(SelectedComponentMinStockText));
        OnPropertyChanged(nameof(SelectedComponentStockDeltaText));
        OnPropertyChanged(nameof(SelectedComponentLocation));
        OnPropertyChanged(nameof(SelectedComponentUpdatedAt));
        OnPropertyChanged(nameof(SelectedComponentDescription));
        OnPropertyChanged(nameof(SelectedComponentStatusTitle));
        OnPropertyChanged(nameof(SelectedComponentStatusMessage));
        OnPropertyChanged(nameof(SelectedComponentStatusSeverity));
        OnPropertyChanged(nameof(SelectedComponentMovementSummary));
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
        OnPropertyChanged(nameof(SelectedMovementTimeline));
        OnPropertyChanged(nameof(SelectedMovementStatusTitle));
        OnPropertyChanged(nameof(SelectedMovementStatusMessage));
        OnPropertyChanged(nameof(SelectedMovementStatusSeverity));
        OnPropertyChanged(nameof(SelectedMovementActionHint));
    }

    private void NotifySyncStateChanged()
    {
        OnPropertyChanged(nameof(ShellSyncSummary));
        OnPropertyChanged(nameof(SyncEndpointDisplay));
        OnPropertyChanged(nameof(SyncTokenDisplay));
        OnPropertyChanged(nameof(SyncAutoSyncDisplay));
        OnPropertyChanged(nameof(SyncRiskTitle));
        OnPropertyChanged(nameof(SyncRiskMessage));
        OnPropertyChanged(nameof(SyncRiskSeverity));
        OnPropertyChanged(nameof(OverviewSyncSummary));
        OnPropertyChanged(nameof(SyncConfigurationSummary));
    }
}
