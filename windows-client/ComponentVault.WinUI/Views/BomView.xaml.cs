using System.Security.Cryptography;
using ComponentVault.WinUI.Services.Bom;
using ComponentVault.WinUI.ViewModels;
using ComponentVault.WinUI.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;
using Windows.Storage;
using WinRT.Interop;

namespace ComponentVault.WinUI.Views;

public sealed partial class BomView : Page
{
    private readonly BomFileReader _reader = new();
    private readonly BomPlanningService _planner = new();
    private readonly Dictionary<int, string> _selections = [];
    private BomDocument? _document;
    private BomPreview? _preview;
    private BomTableInspection? _inspection;
    private BomColumnMapping? _mapping;
    private string? _filePath;
    private string _releaseId = Guid.NewGuid().ToString("N");
    private string _batchId = NewBatchId();
    private bool _confirmed;
    private MainViewModel ViewModel => ((App)Application.Current).MainViewModel!;

    public BomView() => InitializeComponent();

    private async void OnChooseFileClicked(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker();
        picker.FileTypeFilter.Add(".csv");
        picker.FileTypeFilter.Add(".xlsx");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(((App)Application.Current).Window));
        var file = await picker.PickSingleFileAsync();
        if (file is null) return;
        try
        {
            _filePath = file.Path;
            string? sheet = null;
            if (file.FileType.Equals(".xlsx", StringComparison.OrdinalIgnoreCase))
            {
                var names = _reader.GetWorksheetNames(file.Path);
                if (names.Count > 1) sheet = await ChooseSheetAsync(names);
                if (names.Count > 1 && sheet is null) return;
            }
            _inspection = _reader.Inspect(file.Path, sheet);
            _mapping = _inspection.AutomaticMapping;
            if (_mapping.Quantity is null || _mapping.Sku is null && _mapping.Model is null)
            {
                _document = null;
                _selections.Clear();
                ResetBatchIdentity();
                MappingButton.IsEnabled = true;
                ExportButton.IsEnabled = false;
                ConfirmButton.IsEnabled = false;
                SummaryText.Text = $"{_inspection.SourceName} · 未自动识别必需列，请调整列映射。";
                await ShowMessageAsync("需要列映射", "请选择需求数量列，并至少选择 SKU 或型号列。原文件已保留，可直接继续映射。");
                return;
            }
            _document = _reader.Read(file.Path, sheet, _mapping);
            _selections.Clear();
            ResetBatchIdentity();
            RefreshPreview();
        }
        catch (Exception exception) { await ShowMessageAsync("BOM 读取失败", exception.Message); }
    }

    private void RefreshPreview()
    {
        if (_document is null) return;
        var value = BatchQuantityBox.Value;
        var batches = double.IsFinite(value) && value >= 1 && value <= int.MaxValue && value == Math.Truncate(value)
            ? (int)value : 0;
        _preview = ViewModel.CreateBomPreview(_document, ProjectNameBox.Text, batches, _selections);
        PreviewList.ItemsSource = _preview.Lines.Select(line => $"{PreviewIdentity(line)} · 单批 {line.UnitQuantity} · 总需 {line.RequiredQuantity} · 库存 {line.AvailableQuantity} · 扣料 {AllocationPlan(line)}")
            .Concat(_preview.Issues.Select(issue => $"第 {issue.RowNumber?.ToString() ?? "-"} 行 · {issue.Message}")).ToArray();
        SummaryText.Text = $"{_document.SourceName}{(_document.WorksheetName is null ? "" : $" / {_document.WorksheetName}")} · {_preview.Lines.Count} 个匹配 · {_preview.Issues.Count} 个阻止项";
        ResolveButton.IsEnabled = _preview.SelectionGroups is { Count: > 0 };
        MappingButton.IsEnabled = _inspection is not null && !_confirmed;
        ExportButton.IsEnabled = _preview is not null;
        ConfirmButton.IsEnabled = _preview.CanConfirm && !_confirmed;
    }

    private string PreviewIdentity(BomConsumptionLine line)
    {
        var source = _document?.Rows.FirstOrDefault(row => line.SourceRows.Contains(row.RowNumber));
        return string.Join(" · ", new[] { line.ComponentSku, source?.SupplierPartNumber, source?.PackageName }.Where(value => !string.IsNullOrWhiteSpace(value)).Distinct());
    }

    private string AllocationPlan(BomConsumptionLine line)
    {
        var component = ViewModel.Components.FirstOrDefault(item => item.Id == line.ComponentId);
        if (component is null) return "不可用";
        var remaining = line.RequiredQuantity;
        var parts = new List<string>();
        foreach (var allocation in component.Allocations.Where(item => item.Quantity > 0).OrderBy(item => item.LocationId, StringComparer.Ordinal))
        {
            if (remaining == 0) break;
            var take = Math.Min(remaining, allocation.Quantity);
            parts.Add($"{allocation.LocationId} × {take}");
            remaining -= take;
        }
        return remaining == 0 ? string.Join("，", parts) : "分配不足";
    }

    private async void OnResolveClicked(object sender, RoutedEventArgs e)
    {
        if (_preview is null) return;
        var panel = new StackPanel { Spacing = 12, Width = 520 };
        var choices = new Dictionary<int, BomMatchCandidate>();
        foreach (var pair in _preview.SelectionGroups ?? new Dictionary<int, IReadOnlyList<int>>())
        {
            panel.Children.Add(new TextBlock
            {
                Text = $"BOM 第 {string.Join("、", pair.Value)} 行",
                FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
            });
            var box = new AutoSuggestBox
            {
                PlaceholderText = "搜索 SKU、型号、封装或名称",
                HorizontalAlignment = HorizontalAlignment.Stretch,
            };
            box.TextChanged += (_, args) =>
            {
                if (args.Reason == AutoSuggestionBoxTextChangeReason.UserInput)
                    box.ItemsSource = _planner.SearchInventory(ViewModel.Components, box.Text);
            };
            box.SuggestionChosen += (_, args) =>
            {
                if (args.SelectedItem is BomMatchCandidate candidate) choices[pair.Key] = candidate;
            };
            var currentId = _selections.GetValueOrDefault(pair.Key)
                ?? _preview.Lines.FirstOrDefault(line => line.SourceRows.Contains(pair.Key))?.ComponentId;
            if (currentId is { } selectedId &&
                ViewModel.Components.FirstOrDefault(item => item.Id == selectedId) is { } selected)
            {
                var selectedCandidate = _planner.SearchInventory(ViewModel.Components, selected.Sku)
                    .FirstOrDefault(item => item.ComponentId == selectedId);
                if (selectedCandidate is not null)
                {
                    choices[pair.Key] = selectedCandidate;
                    box.Text = selectedCandidate.ToString();
                }
            }
            panel.Children.Add(box);
        }
        var dialog = new ContentDialog { Title = "选择库存匹配", Content = panel, PrimaryButtonText = "应用", CloseButtonText = "取消", XamlRoot = XamlRoot };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        foreach (var pair in choices)
        {
            var sourceRows = _preview.SelectionGroups?.GetValueOrDefault(pair.Key) ?? [pair.Key];
            foreach (var sourceRow in sourceRows) _selections[sourceRow] = pair.Value.ComponentId;
        }
        RefreshPreview();
    }

    private async void OnConfirmClicked(object sender, RoutedEventArgs e)
    {
        RefreshPreview();
        if (_confirmed || _preview is not { CanConfirm: true } preview || _filePath is null) return;
        var dialog = new ContentDialog { Title = "确认批量扣减", Content = $"项目：{preview.ProjectName}\n生产批数：{preview.BatchQuantity}\n库存项：{preview.Lines.Count}", PrimaryButtonText = "确认扣减", CloseButtonText = "取消", DefaultButton = ContentDialogButton.Close, XamlRoot = XamlRoot };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        var hash = Convert.ToHexString(SHA256.HashData(await File.ReadAllBytesAsync(_filePath)));
        var result = ViewModel.ConfirmBomConsumption(new(_releaseId, _batchId, preview.ProjectName, preview.BatchQuantity, hash, preview.Lines));
        if (result.IsSuccess) _confirmed = true;
        await ShowMessageAsync(result.IsSuccess ? "BOM 扣减完成" : "BOM 扣减失败", result.Message);
        RefreshPreview();
    }

    private void OnInputsChanged(object sender, TextChangedEventArgs e) => RefreshPreview();

    private void OnBatchQuantityChanged(NumberBox sender, NumberBoxValueChangedEventArgs args) => RefreshPreview();

    private void OnNewBatchClicked(object sender, RoutedEventArgs e)
    {
        ResetBatchIdentity();
        RefreshPreview();
    }

    private async void OnMappingClicked(object sender, RoutedEventArgs e)
    {
        if (_inspection is null || _filePath is null) return;
        var panel = new StackPanel { Spacing = 8, Width = 520 };
        var choices = _inspection.Headers.Select((header, index) => new ColumnChoice(index, $"{index + 1}. {(string.IsNullOrWhiteSpace(header) ? "未命名" : header)}")).ToArray();
        ComboBox Add(string label, int? selected, bool optional)
        {
            panel.Children.Add(new TextBlock { Text = label });
            var items = optional ? new[] { new ColumnChoice(null, "不使用") }.Concat(choices).ToArray() : choices;
            var box = new ComboBox { ItemsSource = items, DisplayMemberPath = nameof(ColumnChoice.Label), SelectedItem = items.FirstOrDefault(item => item.Index == selected), HorizontalAlignment = HorizontalAlignment.Stretch };
            panel.Children.Add(box); return box;
        }
        var sku = Add("SKU", _mapping?.Sku, true); var model = Add("型号", _mapping?.Model, true);
        var quantity = Add("需求数量（必选）", _mapping?.Quantity, false); var package = Add("封装", _mapping?.Package, true);
        var name = Add("名称", _mapping?.Name, true); var reference = Add("位号", _mapping?.Reference, true);
        var dialog = new ContentDialog { Title = "调整 BOM 列映射", Content = panel, PrimaryButtonText = "重新预览", CloseButtonText = "取消", XamlRoot = XamlRoot };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        try
        {
            _mapping = new(((ColumnChoice?)sku.SelectedItem)?.Index, ((ColumnChoice?)model.SelectedItem)?.Index, ((ColumnChoice?)package.SelectedItem)?.Index, ((ColumnChoice?)quantity.SelectedItem)?.Index, ((ColumnChoice?)name.SelectedItem)?.Index, ((ColumnChoice?)reference.SelectedItem)?.Index);
            _mapping.Validate(_inspection.Headers.Count);
            _document = _reader.Read(_filePath, _inspection.WorksheetName, _mapping);
            _selections.Clear(); ResetBatchIdentity(); RefreshPreview();
        }
        catch (Exception exception) { await ShowMessageAsync("列映射无效", exception.Message); }
    }

    private async void OnExportClicked(object sender, RoutedEventArgs e)
    {
        if (_document is null || _preview is null) return;
        var picker = new FileSavePicker { SuggestedFileName = $"{Path.GetFileNameWithoutExtension(_document.SourceName)}-缺料" };
        picker.FileTypeChoices.Add("CSV", new List<string> { ".csv" });
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(((App)Application.Current).Window));
        var file = await picker.PickSaveFileAsync(); if (file is null) return;
        await FileIO.WriteBytesAsync(file, BomShortageCsvExporter.Export(_document, _preview));
        var count = BomShortageCsvExporter.ShortageCount(_document, _preview);
        await ShowMessageAsync("CSV 已导出", count == 0 ? "当前预览没有缺料。" : $"已导出 {count} 项缺料或未匹配记录。");
    }

    private void ResetBatchIdentity()
    {
        _releaseId = Guid.NewGuid().ToString("N");
        _batchId = NewBatchId();
        _confirmed = false;
    }

    private static string NewBatchId() => $"bom-{DateTimeOffset.UtcNow:yyyyMMddHHmmss}-{Guid.NewGuid():N}";

    private async Task<string?> ChooseSheetAsync(IReadOnlyList<string> names)
    {
        var box = new ComboBox { ItemsSource = names, SelectedIndex = 0, MinWidth = 360 };
        var dialog = new ContentDialog { Title = "选择工作表", Content = box, PrimaryButtonText = "读取", CloseButtonText = "取消", XamlRoot = XamlRoot };
        return await dialog.ShowAsync() == ContentDialogResult.Primary ? box.SelectedItem as string : null;
    }

    private async Task ShowMessageAsync(string title, string message) => await new ContentDialog { Title = title, Content = message, CloseButtonText = "关闭", XamlRoot = XamlRoot }.ShowAsync();

    private sealed record ColumnChoice(int? Index, string Label);
}
