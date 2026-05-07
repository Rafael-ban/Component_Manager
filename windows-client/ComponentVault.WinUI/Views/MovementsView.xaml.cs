using ComponentVault.WinUI.Design;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;

namespace ComponentVault.WinUI.Views;

public sealed partial class MovementsView : Page
{
    private sealed record MovementTypeOption(string Value, string Label);

    private MainViewModel? RuntimeViewModel => ViewModelResolver.GetRuntimeViewModel(DataContext);

    public MovementsView()
    {
        InitializeComponent();
        DataContext = ViewModelResolver.ResolveMainViewModel();
    }

    private void OnRefreshFeedClicked(object sender, RoutedEventArgs e)
    {
        RuntimeViewModel?.Refresh();
    }

    private async void OnRecordMovementClicked(object sender, RoutedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null || viewModel.IsBusy)
        {
            return;
        }

        if (viewModel.AvailableComponents.Count == 0)
        {
            await ShowMessageAsync("记录库存变动", "请先新增一个元器件，再记录库存变动。");
            return;
        }

        var draft = await ShowMovementDialogAsync(viewModel);
        if (draft is null)
        {
            return;
        }

        var result = viewModel.RecordMovement(draft);
        await ShowMessageAsync(result.IsSuccess ? "记录已保存" : "记录失败", result.Message);
    }

    private void OnMovementSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var viewModel = RuntimeViewModel;
        if (viewModel is null)
        {
            return;
        }

        viewModel.SelectedMovement = MovementsListView.SelectedItem as StockMovementRecord;
    }

    private async Task<MovementEntryDraft?> ShowMovementDialogAsync(MainViewModel viewModel)
    {
        var componentCombo = new ComboBox
        {
            ItemsSource = viewModel.AvailableComponents,
            DisplayMemberPath = nameof(ComponentRecord.Name),
            SelectedIndex = 0,
        };
        var movementTypeCombo = new ComboBox
        {
            ItemsSource = new[]
            {
                new MovementTypeOption("inbound", "入库"),
                new MovementTypeOption("outbound", "出库"),
                new MovementTypeOption("adjustment", "调整"),
            },
            DisplayMemberPath = nameof(MovementTypeOption.Label),
            SelectedIndex = 0,
        };
        var quantityBox = new NumberBox
        {
            Value = 1,
            Minimum = -100000,
            SmallChange = 1,
        };
        var reasonBox = new TextBox();
        var noteBox = new TextBox
        {
            AcceptsReturn = true,
            MinHeight = 70,
            TextWrapping = TextWrapping.Wrap,
        };
        var helperText = new TextBlock
        {
            Foreground = new SolidColorBrush(Color.FromArgb(255, 96, 96, 96)),
            Text = "入库和出库请填写正数；库存调整可以填写正数或负数。",
            TextWrapping = TextWrapping.Wrap,
        };
        var errorText = new TextBlock
        {
            Foreground = new SolidColorBrush(Color.FromArgb(255, 176, 0, 32)),
            TextWrapping = TextWrapping.Wrap,
        };

        var panel = new StackPanel { Spacing = 12 };
        panel.Children.Add(CreateField("元器件", componentCombo));
        panel.Children.Add(CreateField("变动类型", movementTypeCombo));
        panel.Children.Add(CreateField("数量", quantityBox));
        panel.Children.Add(helperText);
        panel.Children.Add(CreateField("原因", reasonBox));
        panel.Children.Add(CreateField("备注", noteBox));
        panel.Children.Add(errorText);

        MovementEntryDraft? draft = null;
        var dialog = new ContentDialog
        {
            Title = "记录库存变动",
            Content = panel,
            PrimaryButtonText = "保存",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = XamlRoot,
        };
        dialog.PrimaryButtonClick += (_, args) =>
        {
            if (componentCombo.SelectedItem is not ComponentRecord component)
            {
                errorText.Text = "请选择一个元器件。";
                args.Cancel = true;
                return;
            }

            if (movementTypeCombo.SelectedItem is not MovementTypeOption movementType)
            {
                errorText.Text = "请选择变动类型。";
                args.Cancel = true;
                return;
            }

            if (double.IsNaN(quantityBox.Value))
            {
                errorText.Text = "请输入有效数量。";
                args.Cancel = true;
                return;
            }

            draft = new MovementEntryDraft
            {
                ComponentId = component.Id,
                MovementType = movementType.Value,
                Quantity = (int)Math.Round(quantityBox.Value),
                Reason = reasonBox.Text,
                Note = noteBox.Text,
            };
        };

        var result = await dialog.ShowAsync();
        return result == ContentDialogResult.Primary ? draft : null;
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

    private static FrameworkElement CreateField(string label, FrameworkElement control)
    {
        var panel = new StackPanel { Spacing = 6 };
        panel.Children.Add(new TextBlock { Text = label });
        panel.Children.Add(control);
        return panel;
    }
}
