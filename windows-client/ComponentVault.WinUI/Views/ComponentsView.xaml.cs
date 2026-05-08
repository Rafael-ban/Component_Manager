using ComponentVault.WinUI.Design;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace ComponentVault.WinUI.Views;

public sealed partial class ComponentsView : Page
{
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

    private async void OnEditComponentClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        if (viewModel.SelectedComponent is null)
        {
            await ShowMessageAsync("编辑元器件", "请先选择一个元器件。");
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
            await ShowMessageAsync("软删除元器件", "请先选择一个元器件。");
            return;
        }

        var dialog = new ContentDialog
        {
            Title = "软删除元器件",
            PrimaryButtonText = "确认删除",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot,
            Content = new TextBlock
            {
                Text = $"确认将 {viewModel.SelectedComponent.Name} 标记为已删除吗？历史出入库记录会保留。",
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
        var categoryBox = CreateTextBox(existing?.Category, "例如 Resistor");
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
        panel.Children.Add(CreateField("SKU", skuBox));
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

        ComponentDraft? draft = null;
        var dialog = new ContentDialog
        {
            Title = existing is null ? "新增元器件" : "编辑元器件",
            Content = new ScrollViewer
            {
                Content = panel,
                MaxHeight = 620,
            },
            PrimaryButtonText = "保存",
            CloseButtonText = "取消",
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

    private async Task ShowOperationResultAsync(OperationResult result)
    {
        await ShowMessageAsync(result.IsSuccess ? "操作完成" : "操作失败", result.Message);
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = "关闭",
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
