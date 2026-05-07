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
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        viewModel.ComponentSearchText = sender.Text;
    }

    private void OnLowStockOnlyToggled(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        viewModel.ShowLowStockOnly = LowStockOnlyToggle.IsOn;
    }

    private void OnComponentSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        viewModel.SelectedComponent = ComponentsListView.SelectedItem as ComponentRecord;
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

        var result = viewModel.SaveComponent(draft);
        await ShowOperationResultAsync(result);
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

        var result = viewModel.SaveComponent(draft);
        await ShowOperationResultAsync(result);
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
            Content = $"确认将 {viewModel.SelectedComponent.Name} 标记为已删除吗？历史同步记录会保留。",
            PrimaryButtonText = "确认删除",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot,
        };

        if (await dialog.ShowAsync() != ContentDialogResult.Primary)
        {
            return;
        }

        var result = viewModel.DeleteSelectedComponent();
        await ShowOperationResultAsync(result);
    }

    private async Task<ComponentDraft?> ShowComponentDialogAsync(ComponentRecord? existing)
    {
        var skuBox = CreateTextBox(existing?.Sku);
        var nameBox = CreateTextBox(existing?.Name);
        var categoryBox = CreateTextBox(existing?.Category);
        var packageBox = CreateTextBox(existing?.PackageName);
        var locationBox = CreateTextBox(existing?.Location);
        var descriptionBox = new TextBox
        {
            Text = existing?.Description ?? string.Empty,
            AcceptsReturn = true,
            MinHeight = 80,
            TextWrapping = TextWrapping.Wrap,
        };
        var quantityBox = new NumberBox
        {
            Value = existing?.Quantity ?? 0,
            Minimum = 0,
            SmallChange = 1,
        };
        var minStockBox = new NumberBox
        {
            Value = existing?.MinStock ?? 0,
            Minimum = 0,
            SmallChange = 1,
        };
        var errorText = new TextBlock
        {
            Foreground = new SolidColorBrush(Color.FromArgb(255, 176, 0, 32)),
            TextWrapping = TextWrapping.Wrap,
        };

        var panel = new StackPanel { Spacing = 12 };
        panel.Children.Add(CreateField("SKU", skuBox));
        panel.Children.Add(CreateField("名称", nameBox));
        panel.Children.Add(CreateField("分类", categoryBox));
        panel.Children.Add(CreateField("封装", packageBox));
        panel.Children.Add(CreateField("库位", locationBox));
        panel.Children.Add(CreateField("说明", descriptionBox));
        panel.Children.Add(CreateField("数量", quantityBox));
        panel.Children.Add(CreateField("最低库存", minStockBox));
        panel.Children.Add(errorText);

        ComponentDraft? draft = null;
        var dialog = new ContentDialog
        {
            Title = existing is null ? "新增元器件" : "编辑元器件",
            Content = panel,
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
        if (!result.IsSuccess)
        {
            await ShowMessageAsync("操作失败", result.Message);
            return;
        }

        await ShowMessageAsync("操作完成", result.Message);
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

    private static TextBox CreateTextBox(string? value) =>
        new()
        {
            Text = value ?? string.Empty,
        };

    private static FrameworkElement CreateField(string label, FrameworkElement control)
    {
        var panel = new StackPanel { Spacing = 6 };
        panel.Children.Add(new TextBlock { Text = label });
        panel.Children.Add(control);
        return panel;
    }
}
