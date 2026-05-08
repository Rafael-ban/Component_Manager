using System.Collections.ObjectModel;
using System.Linq;
using ComponentVault.WinUI.Models;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.Design;

public sealed class DesignMainViewModel
{
    private const string AllCategoriesOption = "全部分类";
    private const string AllLocationsOption = "全部仓位";

    public DesignMainViewModel()
    {
        Components = new ObservableCollection<ComponentRecord>(PreviewComponents);
        LowStockComponents = new ObservableCollection<ComponentRecord>(
            PreviewComponents.Where(component => component.IsLowStock)
        );
        Movements = new ObservableCollection<StockMovementRecord>(PreviewMovements);
        OverviewLowStockComponents = new ObservableCollection<ComponentRecord>(
            LowStockComponents.Take(5)
        );
        OverviewRecentMovements = new ObservableCollection<StockMovementRecord>(
            Movements.Take(5)
        );

        Dashboard = new DashboardSnapshot
        {
            ComponentCount = PreviewComponents.Count,
            TotalUnits = PreviewComponents.Sum(component => component.Quantity),
            LowStockCount = LowStockComponents.Count,
            MovementCount = PreviewMovements.Count,
        };

        SyncConfiguration = new SyncConfiguration
        {
            DeviceId = "windows-preview-device",
            ServerBaseUrl = "https://lab.example.net:8787",
            ApiToken = "preview-sync-token",
            AutoSyncEnabled = true,
            LastSyncedAt = "2026-05-08T10:18:00Z",
            LastSyncMessage = "同步完成。上传元器件 1 条、变动 1 条；下载变动 2 条。",
        };

        InventoryCategoryOptions = new ObservableCollection<string>(
            new[] { AllCategoriesOption }
                .Concat(
                    PreviewComponents
                        .Select(component => component.Category)
                        .Distinct()
                        .OrderBy(category => category)
                )
        );
        InventoryLocationOptions = new ObservableCollection<string>(
            new[] { AllLocationsOption }
                .Concat(
                    PreviewComponents
                        .Select(component => component.Location)
                        .Distinct()
                        .OrderBy(location => location)
                )
        );
        InventorySortOptions = new ObservableCollection<string>(
            [
                "最近更新",
                "库存紧张优先",
                "名称 A-Z",
                "库存数量",
            ]
        );

        SelectedComponentCategoryFilter = AllCategoriesOption;
        SelectedComponentLocationFilter = AllLocationsOption;
        SelectedComponentSortOption = "最近更新";
        SelectedComponent = Components.FirstOrDefault(component => component.Id == "cmp-2");
        SelectedMovement = Movements.FirstOrDefault();
        SelectedComponentRecentMovements = new ObservableCollection<StockMovementRecord>(
            PreviewMovements.Where(movement => movement.ComponentId == SelectedComponent?.Id).Take(4)
        );
        StatusMessage = "设计预览使用假数据，不会连接真实数据库或同步服务。";
    }

    private static IReadOnlyList<ComponentRecord> PreviewComponents { get; } =
    [
        new ComponentRecord
        {
            Id = "cmp-1",
            Sku = "CAP-100NF-0603",
            Name = "100nF 陶瓷电容",
            Category = "Capacitor",
            PackageName = "0603",
            Location = "A-01-03",
            Description = "用于 MCU 与传感器电源去耦，属于高频消耗料。",
            Quantity = 420,
            MinStock = 120,
            UpdatedAt = "2026-05-08T09:32:00Z",
            Deleted = false,
        },
        new ComponentRecord
        {
            Id = "cmp-2",
            Sku = "RES-10K-0402",
            Name = "10k 电阻",
            Category = "Resistor",
            PackageName = "0402",
            Location = "B-02-01",
            Description = "常用上拉和分压电阻，处于低库存预警区。",
            Quantity = 16,
            MinStock = 40,
            UpdatedAt = "2026-05-08T10:12:00Z",
            Deleted = false,
        },
        new ComponentRecord
        {
            Id = "cmp-3",
            Sku = "MCU-STM32G431CB",
            Name = "STM32G431CBT6",
            Category = "MCU",
            PackageName = "LQFP-48",
            Location = "C-04-02",
            Description = "主控料件，用于最新一版电机与电源控制板。",
            Quantity = 28,
            MinStock = 12,
            UpdatedAt = "2026-05-07T18:05:00Z",
            Deleted = false,
        },
        new ComponentRecord
        {
            Id = "cmp-4",
            Sku = "IC-TPS63070",
            Name = "TPS63070 升降压芯片",
            Category = "Power",
            PackageName = "VQFN-20",
            Location = "D-03-04",
            Description = "便携设备电源板核心器件，供应周期较长。",
            Quantity = 6,
            MinStock = 10,
            UpdatedAt = "2026-05-08T08:45:00Z",
            Deleted = false,
        },
    ];

    private static IReadOnlyList<StockMovementRecord> PreviewMovements { get; } =
    [
        new StockMovementRecord
        {
            Id = "mov-1",
            ComponentId = "cmp-2",
            ComponentSku = "RES-10K-0402",
            ComponentName = "10k 电阻",
            MovementType = "outbound",
            Quantity = -24,
            Reason = "样机装配",
            Note = "预留给手持测试器小批次。",
            HappenedAt = "2026-05-08T09:05:00Z",
            UpdatedAt = "2026-05-08T09:05:00Z",
            Deleted = false,
        },
        new StockMovementRecord
        {
            Id = "mov-2",
            ComponentId = "cmp-1",
            ComponentSku = "CAP-100NF-0603",
            ComponentName = "100nF 陶瓷电容",
            MovementType = "inbound",
            Quantity = 200,
            Reason = "补货到货",
            Note = "供应商批次 2026-W19。",
            HappenedAt = "2026-05-08T08:10:00Z",
            UpdatedAt = "2026-05-08T08:10:00Z",
            Deleted = false,
        },
        new StockMovementRecord
        {
            Id = "mov-3",
            ComponentId = "cmp-3",
            ComponentSku = "MCU-STM32G431CB",
            ComponentName = "STM32G431CBT6",
            MovementType = "adjustment",
            Quantity = -2,
            Reason = "盘点修正",
            Note = "来料检查剔除 2 颗损伤芯片。",
            HappenedAt = "2026-05-07T17:52:00Z",
            UpdatedAt = "2026-05-07T17:52:00Z",
            Deleted = false,
        },
        new StockMovementRecord
        {
            Id = "mov-4",
            ComponentId = "cmp-4",
            ComponentSku = "IC-TPS63070",
            ComponentName = "TPS63070 升降压芯片",
            MovementType = "outbound",
            Quantity = -3,
            Reason = "工程验证",
            Note = "转给便携电源样机验证。",
            HappenedAt = "2026-05-07T15:20:00Z",
            UpdatedAt = "2026-05-07T15:20:00Z",
            Deleted = false,
        },
    ];

    public DashboardSnapshot Dashboard { get; }

    public SyncConfiguration SyncConfiguration { get; }

    public ObservableCollection<ComponentRecord> Components { get; }

    public ObservableCollection<ComponentRecord> LowStockComponents { get; }

    public ObservableCollection<StockMovementRecord> Movements { get; }

    public ObservableCollection<ComponentRecord> OverviewLowStockComponents { get; }

    public ObservableCollection<StockMovementRecord> OverviewRecentMovements { get; }

    public ObservableCollection<StockMovementRecord> SelectedComponentRecentMovements { get; }

    public ObservableCollection<string> InventoryCategoryOptions { get; }

    public ObservableCollection<string> InventoryLocationOptions { get; }

    public ObservableCollection<string> InventorySortOptions { get; }

    public string ComponentSearchText => string.Empty;

    public bool ShowLowStockOnly => false;

    public string SelectedComponentCategoryFilter { get; }

    public string SelectedComponentLocationFilter { get; }

    public string SelectedComponentSortOption { get; }

    public bool IsBusy => false;

    public string StatusMessage { get; }

    public ComponentRecord? SelectedComponent { get; }

    public StockMovementRecord? SelectedMovement { get; }

    public string ShellSyncSummary => $"最近同步：{SyncConfiguration.LastSyncedAt}";

    public string ComponentInventorySummary =>
        $"当前显示 {Components.Count} 项，共跟踪 {Dashboard.ComponentCount} 项元器件。";

    public string InventoryFilterSummary =>
        $"结果 {Components.Count}/{Dashboard.ComponentCount} · 排序：{SelectedComponentSortOption}";

    public string InventoryWorkspaceSummary =>
        $"分类 {InventoryCategoryOptions.Count - 1} 个 · 仓位 {InventoryLocationOptions.Count - 1} 个 · 低库存 {Dashboard.LowStockCount} 项";

    public string InventoryEmptyMessage => "当前筛选条件下没有结果。";

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
        Movements.Sum(movement => movement.Quantity).ToString("+#,0;-#,0;0");

    public string OverviewLowStockSummary =>
        $"共有 {Dashboard.LowStockCount} 项低库存，这里展示最需要关注的 {OverviewLowStockComponents.Count} 项。";

    public string OverviewRecentMovementSummary =>
        $"最近 {OverviewRecentMovements.Count} 条变动可用于快速回看操作。";

    public string OverviewSyncSummary =>
        $"{ShellSyncSummary} · {SyncConfiguration.LastSyncMessage}";

    public bool CanRunSyncActions => true;

    public bool CanRecordMovement => Components.Count > 0;

    public bool CanEditSelectedComponent => SelectedComponent is not null;

    public bool CanDeleteSelectedComponent => SelectedComponent is not null;

    public string SelectedComponentName =>
        SelectedComponent?.Name ?? "尚未选择元器件";

    public string SelectedComponentSubtitle =>
        SelectedComponent is null
            ? "请先从左侧列表中选择一项。"
            : $"{SelectedComponent.Sku} | {SelectedComponent.Category} | {SelectedComponent.PackageName}";

    public string SelectedComponentQuantityText => (SelectedComponent?.Quantity ?? 0).ToString();

    public string SelectedComponentMinStockText => (SelectedComponent?.MinStock ?? 0).ToString();

    public string SelectedComponentStockDeltaText =>
        SelectedComponent is null
            ? "0"
            : (SelectedComponent.Quantity - SelectedComponent.MinStock).ToString("+#,0;-#,0;0");

    public string SelectedComponentLocation =>
        SelectedComponent?.Location ?? "尚未选择仓位。";

    public string SelectedComponentUpdatedAt =>
        SelectedComponent?.UpdatedAt ?? "尚未选择";

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
        $"最近 {SelectedComponentRecentMovements.Count} 条记录与当前元器件相关。";

    public string SelectedComponentActionHint =>
        "编辑会保留本地优先与同步语义；删除采用软删除，不会直接清除历史记录。";

    public string SelectedMovementTitle =>
        SelectedMovement?.ComponentName ?? "尚未选择变动记录";

    public string SelectedMovementSubtitle =>
        SelectedMovement is null
            ? "从左侧台账中选择一条记录。"
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
        SelectedMovement?.UpdatedAt ?? "尚未选择";

    public string SelectedMovementTimeline =>
        SelectedMovement is null
            ? "尚未选择记录"
            : $"{SelectedMovement.HappenedAt} · 更新于 {SelectedMovement.UpdatedAt}";

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
        "入库和出库请使用正数；库存调整可以填写正数或负数。";

    public string SyncEndpointDisplay => SyncConfiguration.ServerBaseUrl;

    public string SyncTokenDisplay => SyncConfiguration.ApiTokenMasked;

    public string SyncAutoSyncDisplay =>
        SyncConfiguration.AutoSyncEnabled ? "已启用" : "未启用";

    public string SyncRiskTitle => "同步配置已就绪";

    public string SyncRiskMessage =>
        "服务端地址和令牌均已配置，可以执行手动同步或开启自动同步。";

    public InfoBarSeverity SyncRiskSeverity => InfoBarSeverity.Success;

    public string SyncConfigurationSummary =>
        $"设备 {SyncConfiguration.DeviceId} · {SyncAutoSyncDisplay} · 最近结果：{SyncConfiguration.LastSyncMessage}";

    public string SyncActionHint =>
        "设计器中不会执行真实保存、连接测试或同步。";
}
