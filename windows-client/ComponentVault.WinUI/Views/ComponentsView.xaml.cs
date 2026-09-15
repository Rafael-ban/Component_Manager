using ComponentVault.WinUI.Design;
using ComponentVault.WinUI.Localization;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.ViewModels;
using ComponentVault.WinUI.Services.Migration;
using ComponentVault.WinUI.Services.Catalog;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace ComponentVault.WinUI.Views;

public sealed partial class ComponentsView : Page
{
    private readonly LcscPublicLookup _catalogLookup = new();
    private readonly HashSet<string> _lazyLookupAttempted = new(StringComparer.OrdinalIgnoreCase);
    private MainViewModel? RuntimeViewModel => ViewModelResolver.GetRuntimeViewModel(DataContext);

    public ComponentsView()
    {
        InitializeComponent();
        DataContext = ViewModelResolver.ResolveMainViewModel();
    }

    private void OnSearchTextChanged(
        AutoSuggestBox sender,
        AutoSuggestBoxTextChangedEventArgs args
    )
    {
        if (RuntimeViewModel is { } viewModel)
        {
            viewModel.ComponentSearchText = sender.Text;
        }
    }

    private void OnLowStockOnlyToggled(object sender, RoutedEventArgs e)
    {
        if (RuntimeViewModel is { } viewModel)
        {
            viewModel.ShowLowStockOnly = LowStockOnlyToggle.IsOn;
        }
    }

    private void OnComponentSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (RuntimeViewModel is { } viewModel)
        {
            viewModel.SelectedComponent = ComponentsListView.SelectedItem as ComponentRecord;
            _ = TryLazyImageLookupAsync(viewModel.SelectedComponent);
        }
    }

    private async void OnAddComponentClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        var draft = await ShowComponentDialogAsync(null);
        if (draft is null)
        {
            return;
        }

        await ShowOperationResultAsync(viewModel.SaveComponent(draft));
    }

    private async void OnImportComponentHubClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null) return;
        var picker = new FileOpenPicker();
        picker.FileTypeFilter.Add(".json");
        InitializeWithWindow.Initialize(
            picker,
            WindowNative.GetWindowHandle(((App)Application.Current).Window)
        );
        var file = await picker.PickSingleFileAsync();
        if (file is null) return;

        var skipToggle = new ToggleSwitch
        {
            Header = "重复 SKU",
            OffContent = "阻止整个导入（默认）",
            OnContent = "明确跳过重复项",
        };
        var policyDialog = new ContentDialog
        {
            Title = "component-hub 迁移策略",
            Content = skipToggle,
            PrimaryButtonText = "生成预览",
            CloseButtonText = "取消",
            XamlRoot = XamlRoot,
        };
        if (await policyDialog.ShowAsync() != ContentDialogResult.Primary) return;
        var policy = skipToggle.IsOn ? DuplicateSkuPolicy.Skip : DuplicateSkuPolicy.Block;
        try
        {
            var preview = viewModel.PreviewComponentHubMigration(file.Path, policy);
            var text = string.Join(
                "\n",
                new[] { $"可导入：{preview.Items.Count}；跳过重复：{preview.SkippedDuplicates}" }
                    .Concat(preview.Items.Take(12).Select(item => $"{item.Sku} · {item.Name} · 库存 {item.Quantity}"))
                    .Concat(preview.Issues.Select(issue => $"阻止：{issue}"))
            );
            var previewDialog = new ContentDialog
            {
                Title = "确认 component-hub 迁移",
                Content = new ScrollViewer { Content = new TextBlock { Text = text, TextWrapping = TextWrapping.Wrap }, MaxHeight = 520 },
                PrimaryButtonText = "确认导入",
                CloseButtonText = "取消",
                DefaultButton = ContentDialogButton.Close,
                IsPrimaryButtonEnabled = preview.CanConfirm,
                XamlRoot = XamlRoot,
            };
            if (await previewDialog.ShowAsync() != ContentDialogResult.Primary) return;
            await ShowOperationResultAsync(viewModel.ImportComponentHub(new(preview.Fingerprint, policy, preview.Items)));
        }
        catch (Exception exception)
        {
            await ShowMessageAsync("迁移文件读取失败", exception.Message);
        }
    }

    private async void OnEditComponentClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        if (viewModel.SelectedComponent is null)
        {
            await ShowMessageAsync(
                AppStrings.Get("Components_Dialog_EditTitle"),
                AppStrings.Get("Components_Dialog_SelectFirstMessage")
            );
            return;
        }

        var draft = await ShowComponentDialogAsync(viewModel.SelectedComponent);
        if (draft is null)
        {
            return;
        }

        await ShowOperationResultAsync(viewModel.SaveComponent(draft));
    }

    private async void OnDeleteComponentClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        if (viewModel.SelectedComponent is null)
        {
            await ShowMessageAsync(
                AppStrings.Get("Components_Dialog_DeleteTitle"),
                AppStrings.Get("Components_Dialog_SelectFirstMessage")
            );
            return;
        }

        var dialog = new ContentDialog
        {
            Title = AppStrings.Get("Components_Dialog_DeleteTitle"),
            PrimaryButtonText = AppStrings.Get("Components_Dialog_DeleteConfirmPrimary"),
            CloseButtonText = AppStrings.Get("Common_Cancel"),
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot,
            Content = new TextBlock
            {
                Text = AppStrings.Format(
                    "Components_Dialog_DeleteConfirmMessagePattern",
                    viewModel.SelectedComponent.Name
                ),
                TextWrapping = TextWrapping.Wrap,
                MaxWidth = 420,
            },
        };

        if (await dialog.ShowAsync() != ContentDialogResult.Primary)
        {
            return;
        }

        await ShowOperationResultAsync(viewModel.DeleteSelectedComponent());
    }

    private async Task<ComponentDraft?> ShowComponentDialogAsync(ComponentRecord? existing)
    {
        var skuBox = CreateTextBox(existing?.Sku, "例如 RES-10K-0402");
        var nameBox = CreateTextBox(existing?.Name, "例如 10k 电阻");
        var categoryBox = CreateTextBox(existing?.Category, "例如 电阻");
        var packageBox = CreateTextBox(existing?.PackageName, "例如 0402");
        var locationBox = CreateTextBox(existing?.Location, "例如 B-02-01");
        var descriptionBox = new TextBox
        {
            Text = existing?.Description ?? string.Empty,
            PlaceholderText = "填写用途、兼容料号或补货说明",
            AcceptsReturn = true,
            MinHeight = 96,
            TextWrapping = TextWrapping.Wrap,
        };
        var quantityBox = new NumberBox
        {
            Value = existing?.Quantity ?? 0,
            Minimum = 0,
            SmallChange = 1,
            SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact,
        };
        var minStockBox = new NumberBox
        {
            Value = existing?.MinStock ?? 0,
            Minimum = 0,
            SmallChange = 1,
            SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Compact,
        };
        var errorText = new TextBlock
        {
            Foreground = new SolidColorBrush(Color.FromArgb(255, 176, 0, 32)),
            TextWrapping = TextWrapping.Wrap,
        };

        var panel = new StackPanel
        {
            Spacing = 16,
            Width = 520,
        };
        panel.Children.Add(CreateSectionHeader("基本信息", "名称、SKU、分类与封装。"));
        var skuActions = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        var lookupButton = new Button { Content = "按 C 编号联网补全" };
        var chinaSearchButton = new Button { Content = "打开国内商城搜索" };
        skuActions.Children.Add(lookupButton);
        skuActions.Children.Add(chinaSearchButton);
        var skuPanel = new StackPanel { Spacing = 6 };
        skuPanel.Children.Add(skuBox);
        skuPanel.Children.Add(skuActions);
        panel.Children.Add(CreateField("SKU / 立创 C 编号", skuPanel));
        panel.Children.Add(CreateField("名称", nameBox));
        panel.Children.Add(CreateField("分类", categoryBox));
        panel.Children.Add(CreateField("封装", packageBox));

        panel.Children.Add(CreateSectionHeader("库存与仓位", "数量与最低库存均不能为负数。"));
        panel.Children.Add(CreateField("仓位", locationBox));
        panel.Children.Add(CreateField("当前库存", quantityBox));
        panel.Children.Add(CreateField("最低库存", minStockBox));

        panel.Children.Add(CreateSectionHeader("备注", "填写用途、风险或替代料信息。"));
        panel.Children.Add(CreateField("描述 / 备注", descriptionBox));
        panel.Children.Add(errorText);

        lookupButton.Click += async (_, _) =>
        {
            var normalized = LcscPublicCatalog.NormalizeSku(skuBox.Text);
            if (normalized is null)
            {
                errorText.Text = "请输入 C 开头、后接 1–10 位数字的立创编号。";
                return;
            }
            lookupButton.IsEnabled = false;
            errorText.Text = "正在读取 LCSC 公共商品页…";
            try
            {
                var metadata = await _catalogLookup.LookupAsync(normalized);
                if (!string.Equals(LcscPublicCatalog.NormalizeSku(skuBox.Text), normalized, StringComparison.Ordinal))
                {
                    errorText.Text = "编号已更改，请按新编号重新查询。";
                    return;
                }
                if (metadata is null)
                {
                    errorText.Text = "公共商品页没有返回与该 C 编号精确一致的 Product 数据。";
                    return;
                }
                skuBox.Text = metadata.Sku;
                if (string.IsNullOrWhiteSpace(nameBox.Text)) nameBox.Text = metadata.Name;
                if (string.IsNullOrWhiteSpace(categoryBox.Text)) categoryBox.Text = metadata.Category;
                if (string.IsNullOrWhiteSpace(packageBox.Text) && !string.IsNullOrWhiteSpace(metadata.PackageName)) packageBox.Text = metadata.PackageName;
                descriptionBox.Text = AppendOfficialNotes(descriptionBox.Text, metadata);
                errorText.Text = "已按精确 C 编号补全空白字段；原有手工字段未覆盖。";
            }
            catch (Exception exception) { errorText.Text = $"公共商品查询失败：{exception.Message}"; }
            finally { lookupButton.IsEnabled = true; }
        };
        chinaSearchButton.Click += async (_, _) =>
        {
            var uri = LcscPublicCatalog.ChinaSearchUri(skuBox.Text);
            if (uri is null) { errorText.Text = "请输入有效 C 编号后再打开搜索。"; return; }
            await Windows.System.Launcher.LaunchUriAsync(uri);
        };

        ComponentDraft? draft = null;
        var dialog = new ContentDialog
        {
            Title = existing is null
                ? AppStrings.Get("Components_Dialog_AddTitle")
                : AppStrings.Get("Components_Dialog_EditTitle"),
            Content = new ScrollViewer
            {
                Content = panel,
                MaxHeight = 620,
            },
            PrimaryButtonText = AppStrings.Get("Common_Save"),
            CloseButtonText = AppStrings.Get("Common_Cancel"),
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = XamlRoot,
        };
        dialog.PrimaryButtonClick += (_, args) =>
        {
            if (double.IsNaN(quantityBox.Value) || double.IsNaN(minStockBox.Value))
            {
                errorText.Text = "数量和最低库存必须是有效数字。";
                args.Cancel = true;
                return;
            }

            if (quantityBox.Value < 0 || minStockBox.Value < 0)
            {
                errorText.Text = "数量和最低库存不能为负数。";
                args.Cancel = true;
                return;
            }

            draft = new ComponentDraft
            {
                Id = existing?.Id,
                Sku = skuBox.Text,
                Name = nameBox.Text,
                Category = categoryBox.Text,
                PackageName = packageBox.Text,
                Location = locationBox.Text,
                Description = descriptionBox.Text,
                Quantity = (int)Math.Round(quantityBox.Value),
                MinStock = (int)Math.Round(minStockBox.Value),
            };
        };

        var result = await dialog.ShowAsync();
        return result == ContentDialogResult.Primary ? draft : null;
    }

    private async Task TryLazyImageLookupAsync(ComponentRecord? component)
    {
        if (component is null || component.ProductImageUrl is not null) return;
        var sku = LcscPublicCatalog.NormalizeSku(component.Sku);
        if (sku is null || !_lazyLookupAttempted.Add(sku)) return;
        try
        {
            var metadata = await _catalogLookup.LookupAsync(sku);
            if (metadata?.ImageUrl is null || RuntimeViewModel is not { } viewModel) return;
            viewModel.Refresh();
        }
        catch { }
    }

    private static string AppendOfficialNotes(string description, LcscProductMetadata metadata)
    {
        var notes = new List<string>();
        if (!string.IsNullOrWhiteSpace(description))
            notes.AddRange(description.Split(['\n', '；'], StringSplitOptions.RemoveEmptyEntries)
                .Select(note => note.Trim())
                .Where(note => metadata.ImageUrl is null || !note.StartsWith("商品图片：", StringComparison.Ordinal)));
        AddUnique(notes, metadata.Model is null ? null : $"型号：{metadata.Model}");
        AddUnique(notes, metadata.Brand is null ? null : $"品牌：{metadata.Brand}");
        AddUnique(notes, $"官方商品页：{metadata.OfficialUrl}");
        AddUnique(notes, metadata.CategoryPath is null ? null : $"官方分类路径：{metadata.CategoryPath}");
        AddUnique(notes, metadata.ImageUrl is null ? null : $"商品图片：{metadata.ImageUrl}");
        return string.Join("\n", notes);
    }

    private static void AddUnique(List<string> notes, string? value)
    {
        if (!string.IsNullOrWhiteSpace(value) && !notes.Any(note => note.Contains(value, StringComparison.Ordinal))) notes.Add(value);
    }

    private async Task ShowOperationResultAsync(OperationResult result)
    {
        await ShowMessageAsync(
            result.IsSuccess
                ? AppStrings.Get("Components_Dialog_OperationSuccessTitle")
                : AppStrings.Get("Components_Dialog_OperationFailureTitle"),
            result.Message
        );
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = AppStrings.Get("Common_Close"),
            XamlRoot = XamlRoot,
        };
        await dialog.ShowAsync();
    }

    private static TextBox CreateTextBox(string? value, string placeholderText) =>
        new()
        {
            Text = value ?? string.Empty,
            PlaceholderText = placeholderText,
        };

    private static FrameworkElement CreateField(string label, FrameworkElement control)
    {
        var panel = new StackPanel { Spacing = 6 };
        panel.Children.Add(new TextBlock { Text = label, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold });
        panel.Children.Add(control);
        return panel;
    }

    private static FrameworkElement CreateSectionHeader(string title, string description)
    {
        var panel = new StackPanel { Spacing = 2 };
        panel.Children.Add(
            new TextBlock
            {
                Text = title,
                FontSize = 18,
                FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
            }
        );
        panel.Children.Add(
            new TextBlock
            {
                Text = description,
                Foreground = new SolidColorBrush(Color.FromArgb(255, 96, 96, 96)),
                TextWrapping = TextWrapping.Wrap,
            }
        );
        return panel;
    }
}
