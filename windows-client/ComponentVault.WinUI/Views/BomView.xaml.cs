using System.Security.Cryptography;
using ComponentVault.WinUI.Services.Bom;
using ComponentVault.WinUI.ViewModels;
using ComponentVault.WinUI.Models;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace ComponentVault.WinUI.Views;

public sealed partial class BomView : Page
{
    private readonly BomFileReader _reader = new();
    private readonly Dictionary<int, string> _selections = [];
    private BomDocument? _document;
    private BomPreview? _preview;
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
            _document = _reader.Read(file.Path, sheet);
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
        PreviewList.ItemsSource = _preview.Lines.Select(line => $"{line.ComponentSku} · 单批 {line.UnitQuantity} · 总需 {line.RequiredQuantity} · 库存 {line.AvailableQuantity} · 扣料 {AllocationPlan(line)}")
            .Concat(_preview.Issues.Select(issue => $"第 {issue.RowNumber?.ToString() ?? "-"} 行 · {issue.Message}")).ToArray();
        SummaryText.Text = $"{_document.SourceName}{(_document.WorksheetName is null ? "" : $" / {_document.WorksheetName}")} · {_preview.Lines.Count} 个匹配 · {_preview.Issues.Count} 个阻止项";
        ResolveButton.IsEnabled = _preview.Candidates.Count > 0;
        ConfirmButton.IsEnabled = _preview.CanConfirm && !_confirmed;
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
        var boxes = new Dictionary<int, ComboBox>();
        foreach (var pair in _preview.Candidates.Where(pair => pair.Value.Count > 0))
        {
            panel.Children.Add(new TextBlock { Text = $"BOM 第 {pair.Key} 行", FontWeight = Microsoft.UI.Text.FontWeights.SemiBold });
            var box = new ComboBox { ItemsSource = pair.Value, DisplayMemberPath = "Sku", HorizontalAlignment = HorizontalAlignment.Stretch };
            panel.Children.Add(box); boxes[pair.Key] = box;
        }
        var dialog = new ContentDialog { Title = "选择库存匹配", Content = panel, PrimaryButtonText = "应用", CloseButtonText = "取消", XamlRoot = XamlRoot };
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        foreach (var pair in boxes) if (pair.Value.SelectedItem is BomMatchCandidate candidate) _selections[pair.Key] = candidate.ComponentId;
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
}
