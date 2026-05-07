using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Windows.UI;
using ComponentVault.WinUI.Models;
using ComponentVault.WinUI.ViewModels;

namespace ComponentVault.WinUI.Views;

public sealed partial class ComponentsView : Page
{
    private MainViewModel ViewModel => ((App)Application.Current).MainViewModel;

    public ComponentsView()
    {
        InitializeComponent();
        DataContext = ViewModel;
    }

    private void OnSearchTextChanged(
        AutoSuggestBox sender,
        AutoSuggestBoxTextChangedEventArgs args
    )
    {
        ViewModel.ComponentSearchText = sender.Text;
    }

    private void OnLowStockOnlyToggled(object sender, RoutedEventArgs e)
    {
        ViewModel.ShowLowStockOnly = LowStockOnlyToggle.IsOn;
    }

    private void OnComponentSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        ViewModel.SelectedComponent = ComponentsListView.SelectedItem as ComponentRecord;
    }

    private async void OnAddComponentClicked(object sender, RoutedEventArgs e)
    {
        var draft = await ShowComponentDialogAsync(null);
        if (draft is null)
        {
            return;
        }

        var result = ViewModel.SaveComponent(draft);
        await ShowOperationResultAsync(result);
    }

    private async void OnEditComponentClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel.SelectedComponent is null)
        {
            await ShowMessageAsync("Edit component", "Select a component first.");
            return;
        }

        var draft = await ShowComponentDialogAsync(ViewModel.SelectedComponent);
        if (draft is null)
        {
            return;
        }

        var result = ViewModel.SaveComponent(draft);
        await ShowOperationResultAsync(result);
    }

    private async void OnDeleteComponentClicked(object sender, RoutedEventArgs e)
    {
        if (ViewModel.SelectedComponent is null)
        {
            await ShowMessageAsync("Soft delete", "Select a component first.");
            return;
        }

        var dialog = new ContentDialog
        {
            Title = "Soft delete component",
            Content = $"Mark {ViewModel.SelectedComponent.Name} as deleted? Existing sync history stays intact.",
            PrimaryButtonText = "Delete",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = XamlRoot,
        };

        if (await dialog.ShowAsync() != ContentDialogResult.Primary)
        {
            return;
        }

        var result = ViewModel.DeleteSelectedComponent();
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
        panel.Children.Add(CreateField("Name", nameBox));
        panel.Children.Add(CreateField("Category", categoryBox));
        panel.Children.Add(CreateField("Package", packageBox));
        panel.Children.Add(CreateField("Location", locationBox));
        panel.Children.Add(CreateField("Description", descriptionBox));
        panel.Children.Add(CreateField("Quantity", quantityBox));
        panel.Children.Add(CreateField("Minimum stock", minStockBox));
        panel.Children.Add(errorText);

        ComponentDraft? draft = null;
        var dialog = new ContentDialog
        {
            Title = existing is null ? "Add component" : "Edit component",
            Content = panel,
            PrimaryButtonText = "Save",
            CloseButtonText = "Cancel",
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = XamlRoot,
        };
        dialog.PrimaryButtonClick += (_, args) =>
        {
            if (double.IsNaN(quantityBox.Value) || double.IsNaN(minStockBox.Value))
            {
                errorText.Text = "Quantity and minimum stock must be valid numbers.";
                args.Cancel = true;
                return;
            }

            if (quantityBox.Value < 0 || minStockBox.Value < 0)
            {
                errorText.Text = "Quantity and minimum stock cannot be negative.";
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
            await ShowMessageAsync("Operation failed", result.Message);
            return;
        }

        await ShowMessageAsync("Component updated", result.Message);
    }

    private async Task ShowMessageAsync(string title, string message)
    {
        var dialog = new ContentDialog
        {
            Title = title,
            Content = message,
            CloseButtonText = "Close",
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
        panel.Children.Add(
            new TextBlock
            {
                Text = label,
            }
        );
        panel.Children.Add(control);
        return panel;
    }
}
