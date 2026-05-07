using System.Collections.ObjectModel;
using ComponentVault.WinUI.Models;
using Microsoft.UI.Xaml.Controls;

namespace ComponentVault.WinUI.Design;

public sealed class DesignMainViewModel
{
    public DesignMainViewModel()
    {
        Components = new ObservableCollection<ComponentRecord>(PreviewComponents);
        LowStockComponents = new ObservableCollection<ComponentRecord>(
            PreviewComponents.Where(component => component.IsLowStock)
        );
        Movements = new ObservableCollection<StockMovementRecord>(PreviewMovements);

        Dashboard = new DashboardSnapshot
        {
            ComponentCount = Components.Count,
            TotalUnits = Components.Sum(component => component.Quantity),
            LowStockCount = LowStockComponents.Count,
            MovementCount = Movements.Count,
        };

        SyncConfiguration = new SyncConfiguration
        {
            DeviceId = "windows-preview-device",
            ServerBaseUrl = "https://lab.example.net:8787",
            ApiToken = "preview-sync-token",
            AutoSyncEnabled = true,
            LastSyncedAt = "2026-05-08T10:18:00Z",
            LastSyncMessage = "同步完成。上传 元器件:1 变动:1；下载 元器件:0 变动:2。",
        };

        SelectedComponent = Components.FirstOrDefault(component => component.Id == "cmp-2");
        SelectedMovement = Movements.FirstOrDefault();
        StatusMessage = "设计态预览：当前页面使用假数据，不会连接真实数据库或同步服务。";
    }

    private static IReadOnlyList<ComponentRecord> PreviewComponents { get; } =
    [
        new ComponentRecord
        {
            Id = "cmp-1",
            Sku = "CAP-100NF-0603",
            Name = "100nF Ceramics Capacitor",
            Category = "Capacitor",
            PackageName = "0603",
            Location = "A-01-03",
            Description = "General-purpose decoupling capacitor for MCU and sensor rails.",
            Quantity = 420,
            MinStock = 120,
            UpdatedAt = "2026-05-08T09:32:00Z",
            Deleted = false,
        },
        new ComponentRecord
        {
            Id = "cmp-2",
            Sku = "RES-10K-0402",
            Name = "10k Ohm Resistor",
            Category = "Resistor",
            PackageName = "0402",
            Location = "B-02-01",
            Description = "Common pull-up and divider resistor kept in the desktop prototype kit.",
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
            Description = "Main controller used by the latest motor and power boards.",
            Quantity = 28,
            MinStock = 12,
            UpdatedAt = "2026-05-07T18:05:00Z",
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
            ComponentName = "10k Ohm Resistor",
            MovementType = "outbound",
            Quantity = -24,
            Reason = "Prototype assembly",
            Note = "Reserved for the handheld tester batch.",
            HappenedAt = "2026-05-08T09:05:00Z",
            UpdatedAt = "2026-05-08T09:05:00Z",
            Deleted = false,
        },
        new StockMovementRecord
        {
            Id = "mov-2",
            ComponentId = "cmp-1",
            ComponentSku = "CAP-100NF-0603",
            ComponentName = "100nF Ceramics Capacitor",
            MovementType = "inbound",
            Quantity = 200,
            Reason = "Restock delivery",
            Note = "Supplier batch 2026-W19.",
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
            Reason = "Stock correction",
            Note = "Removed two damaged units after incoming inspection.",
            HappenedAt = "2026-05-07T17:52:00Z",
            UpdatedAt = "2026-05-07T17:52:00Z",
            Deleted = false,
        },
    ];

    public DashboardSnapshot Dashboard { get; }

    public SyncConfiguration SyncConfiguration { get; }

    public ObservableCollection<ComponentRecord> Components { get; }

    public ObservableCollection<ComponentRecord> LowStockComponents { get; }

    public ObservableCollection<StockMovementRecord> Movements { get; }

    public bool IsBusy => false;

    public string StatusMessage { get; }

    public ComponentRecord? SelectedComponent { get; set; }

    public StockMovementRecord? SelectedMovement { get; set; }

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

    public bool CanRunSyncActions => true;

    public bool CanRecordMovement => Components.Count > 0;

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
        Components.Count == 0
            ? "请先新增元器件，再记录库存变动。"
            : "入库和出库请使用正数；库存调整可以填写正数或负数。";

    public string SyncEndpointDisplay => SyncConfiguration.ServerBaseUrl;

    public string SyncTokenDisplay => SyncConfiguration.ApiTokenMasked;

    public string SyncAutoSyncDisplay =>
        SyncConfiguration.AutoSyncEnabled ? "已启用" : "未启用";

    public string SyncRiskTitle => "可以开始同步";

    public string SyncRiskMessage =>
        "服务器地址和令牌均已配置，可执行手动同步或自动同步。";

    public InfoBarSeverity SyncRiskSeverity => InfoBarSeverity.Success;

    public string SyncActionHint => "设计器中不会执行真实保存、连接测试或同步。";
}
